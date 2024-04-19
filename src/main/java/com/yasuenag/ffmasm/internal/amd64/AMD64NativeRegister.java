/*
 * Copyright (C) 2023, 2024, Yasumasa Suenaga
 *
 * This file is part of ffmasm.
 *
 * ffmasm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ffmasm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ffmasm.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.yasuenag.ffmasm.internal.amd64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.NativeRegister;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;
import com.yasuenag.ffmasm.internal.JvmtiEnv;


public final class AMD64NativeRegister extends NativeRegister{

  private static final Arena arena;
  private static final CodeSegment seg;

  private static MethodHandle registerStub;

  private static void setupRegisterStub() throws Throwable{
    var targetMethod = NativeRegister.class
                                     .getDeclaredMethod("callback",
                                                        MemorySegment.class,
                                                        int.class,
                                                        int.class,
                                                        MemorySegment.class);
    var hndCallback = MethodHandles.lookup()
                                   .unreflect(targetMethod);
    var cbStub = Linker.nativeLinker()
                       .upcallStub(hndCallback,
                                   FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
                                                             ValueLayout.JAVA_INT,
                                                             ValueLayout.JAVA_INT,
                                                             ValueLayout.ADDRESS),
                                   arena);

    var jvmtiEnv = JvmtiEnv.getInstance();
    var regs = CallingRegisters.getRegs();
    var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS); // callbackParam

    /*             S T A C K   A L L O C A T I O N
         -------------------------------------------------  ---- RSP (RBP-64)
        | Shadow space for arg4                           |
        |-------------------------------------------------|
        | Shadow space for arg3                           |
        |-------------------------------------------------|
        | Shadow space for arg2                           |
        |-------------------------------------------------|
        | Shadow space for arg1                           |
        |-------------------------------------------------|
        | Stack alignment                                 |
        |-------------------------------------------------|
        | Number of jclasses (from GetLoadedClasses())    |
        |-------------------------------------------------|
        | Pointer to jclass array from GetLoadedClasses() |
        |-------------------------------------------------|
        | Saved Register 1                                |
        |-------------------------------------------------| ---- RBP
        | Previous RBP                                    |
        |-------------------------------------------------|
        | Return Address                                  |
         -------------------------------------------------
     */
    registerStub = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
  // prologue
    /* push %rbp               */ .push(Register.RBP)
    /* mov %rsp, %rbp          */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
    /* sub $64, %rsp           */ .sub(Register.RSP, 64, OptionalInt.empty())
    /* mov savedReg1, -8(%rbp) */ .movMR(regs.savedReg1(), Register.RBP, OptionalInt.of(-8))
    /* mov arg1, savedReg1     */ .movMR(regs.arg1(), regs.savedReg1(), OptionalInt.empty())

  // call GetLoadedClasses()
    /* lea -24(%rbp), arg2     */ .lea(regs.arg2(), Register.RBP, -24) // count
    /* lea -16(%rbp), arg3     */ .lea(regs.arg3(), Register.RBP, -16)  // classes
    /* mov addr, arg1          */ .movImm(regs.arg1(), jvmtiEnv.getRawAddress()) // address of jvmtiEnv
    /* mov addr, tmpReg1       */ .movImm(regs.tmpReg1(), jvmtiEnv.getLoadedClassesAddr().address()) // address of GetLoadedClasses()
    /* call tmpReg1            */ .call(regs.tmpReg1())

  // call callback(jclass *classes, jint class_count)
    /* mov -24(%rbp), arg2     */ .movRM(regs.arg2(), Register.RBP, OptionalInt.of(-24)) // count
    /* mov -16(%rbp), arg1     */ .movRM(regs.arg1(), Register.RBP, OptionalInt.of(-16))  // classes
    /* mov returnReg, arg3     */ .movMR(regs.returnReg(), regs.arg3(), OptionalInt.empty()) // result of GetLoadedClasses()
    /* mov savedReg1, arg4     */ .movMR(regs.savedReg1(), regs.arg4(), OptionalInt.empty()) // callbackParam
    /* mov addr, tmpReg1       */ .movImm(regs.tmpReg1(), cbStub.address()) // address of callback
    /* call tmpReg1            */ .call(regs.tmpReg1())

  // call Deallocate()
    /* mov addr, arg1          */ .movImm(regs.arg1(), jvmtiEnv.getRawAddress()) // address of jvmtiEnv
    /* mov -16(%rbp), arg1     */ .movRM(regs.arg1(), Register.RBP, OptionalInt.of(-16))  // classes
    /* mov addr, tmpReg1       */ .movImm(regs.tmpReg1(), jvmtiEnv.deallocateAddr().address()) // address of Deallocate()
    /* call tmpReg1            */ .call(regs.tmpReg1())

  // epilogue
    /* mov -8(%rbp), savedReg1 */ .movRM(regs.savedReg1(), Register.RBP, OptionalInt.of(-8))
    /* leave                   */ .leave()
    /* ret                     */ .ret()
                                  .build();
  }

  static{
    arena = Arena.ofAuto();
    try{
      seg = new CodeSegment();
      var action = new CodeSegment.CleanerAction(seg);
      Cleaner.create()
             .register(NativeRegister.class, action);
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  public AMD64NativeRegister(Class<?> klass){
    super(klass);
  }

  @Override
  protected void callRegisterStub(MemorySegment callbackParam) throws Throwable{
    if(registerStub == null){
      setupRegisterStub();
    }
    registerStub.invoke(callbackParam);
  }

}
