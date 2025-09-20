/*
 * Copyright (C) 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.internal.aarch64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.Optional;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.NativeRegister;
import com.yasuenag.ffmasm.aarch64.Register;
import com.yasuenag.ffmasm.aarch64.IndexClasses;
import com.yasuenag.ffmasm.internal.JvmtiEnv;


public final class AArch64NativeRegister extends NativeRegister{

  private static final Arena arena;
  private static final CodeSegment seg;

  private static MemorySegment cbStub;
  private static JvmtiEnv jvmtiEnv;
  private static MethodHandle registerStub;

  private static void setupRegisterStub() throws Throwable{
    var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,  // callbackParam
                                         ValueLayout.ADDRESS,  // jvmtiEnv
                                         ValueLayout.ADDRESS,  // cbStub
                                         ValueLayout.ADDRESS,  // ptr GetLoadedClass()
                                         ValueLayout.ADDRESS); // ptr Deallocate()

    /*             S T A C K   A L L O C A T I O N
         -------------------------------------------------  ---- SP (FP-64)
        | Number of jclasses (from GetLoadedClasses())    |
        |-------------------------------------------------|
        | Pointer to jclass array from GetLoadedClasses() |
        |-------------------------------------------------|
        | stack alignment (dummy (arg5))                  |
        |-------------------------------------------------|
        | ptr Deallocate() (arg5)                         |
        |-------------------------------------------------|
        | ptr GetLoadedClass() (arg4)                     |
        |-------------------------------------------------|
        | cbStub (arg3)                                   |
        |-------------------------------------------------|
        | jvmtiEnv (arg2)                                 |
        |-------------------------------------------------|
        | callbackParam (arg1)                            |
        |-------------------------------------------------| ---- FP
        | Previous FP (X29)                               |
        |-------------------------------------------------|
        | Previous LR (X30)                               |
         -------------------------------------------------
     */
    registerStub = new AsmBuilder.AArch64(seg, desc)
 // prologue
   /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
   /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
   /* stp  x1,  x0, [sp, #-16]! */ .stp(Register.X1, Register.X0, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
   /* stp  x3,  x2, [sp, #-16]! */ .stp(Register.X3, Register.X2, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
   /* stp  x4,  x4, [sp, #-16]! */ .stp(Register.X4, Register.X4, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
   /* sub  sp,  sp, #24         */ .subImm(Register.SP, Register.SP, 24, false)

 // call GetLoadedClasses()
   /* mov  x1, SP               */ .mov(Register.X1, Register.SP) // count (arg2)
   /* add  x2, SP, #8           */ .addImm(Register.SP, Register.X2, 8, false) // classes (arg3)
   /* ldr  x0, [SP, #48]        */ .ldr(Register.X0, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 48) // address of jvmtiEnv (arg1)
   /* ldr  x9, [SP, #32]        */ .ldr(Register.X9, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 32) // address of GetLoadedClasses() (x9: tmpreg)
   /* blr  x9                   */ .blr(Register.X9)

 // call callback(MemorySegment classes, int class_count, int resultGetLoadedClasses, MemorySegment callbackParam)
   /* mov  x2,  x0              */ .mov(Register.X2, Register.X0) // result of GetLoadedClasses() (arg3)
   /* ldp  x1,  x0, [sp]        */ .ldp(Register.X1, Register.X0, Register.SP, IndexClasses.LDP_STP.SignedOffset, 0) // count (arg2), classes(arg1)
   /* ldr  x3, [SP, #56]        */ .ldr(Register.X3, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 56) // callbackParam (arg4)
   /* ldr  x9, [SP, #40]        */ .ldr(Register.X9, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 40) // address of callback (x9: tmpreg)
   /* blr  x9                   */ .blr(Register.X9)

 // call Deallocate()
   /* ldr  x0, [SP, #48]        */ .ldr(Register.X0, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 48) // address of jvmtiEnv (arg1)
   /* ldr  x1, [SP, #8]         */ .ldr(Register.X1, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 8) // classes (arg2)
   /* ldr  x9, [SP, #24]        */ .ldr(Register.X9, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 24) // address of Deallocate (x9: tmpreg)
   /* blr  x9                   */ .blr(Register.X9)

 // epilogue
   /* add  sp,  sp, #64         */ .addImm(Register.SP, Register.SP, 64, false)
   /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
   /* ret                       */ .ret(Optional.empty())
                                   .build();
  }

  static{
    arena = Arena.ofAuto();
    try{
      seg = new CodeSegment();
      var action = new CodeSegment.CleanerAction(seg);
      Cleaner.create()
             .register(NativeRegister.class, action);

      var targetMethod = NativeRegister.class
                                       .getDeclaredMethod("callback",
                                                          MemorySegment.class,
                                                          int.class,
                                                          int.class,
                                                          MemorySegment.class);
      var hndCallback = MethodHandles.lookup()
                                   .unreflect(targetMethod);
      cbStub = Linker.nativeLinker()
                     .upcallStub(hndCallback,
                                 FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
                                                           ValueLayout.JAVA_INT,
                                                           ValueLayout.JAVA_INT,
                                                           ValueLayout.ADDRESS),
                                 arena);

      jvmtiEnv = JvmtiEnv.getInstance();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  public AArch64NativeRegister(Class<?> klass){
    super(klass);
  }

  @Override
  protected void callRegisterStub(MemorySegment callbackParam) throws Throwable{
    if(registerStub == null){
      setupRegisterStub();
    }
    registerStub.invoke(callbackParam, jvmtiEnv.getMemorySegment(), cbStub, jvmtiEnv.getLoadedClassesAddr(), jvmtiEnv.deallocateAddr());
  }

}
