/*
 * Copyright (C) 2023, Yasumasa Suenaga
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
    var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS); // callbackParam
    registerStub = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
      // prologue
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
      // evacutate
          /* push %r12         */ .push(Register.R12)
          /* mov %rdi, %r12    */ .movMR(Register.RDI, Register.R12, OptionalInt.empty()) // count
      // call GetLoadedClasses()
          /* sub $16, %rsp     */ .sub(Register.RSP, 16, OptionalInt.empty())
          /* lea 8(%rsp), %rdx */ .lea(Register.RDX, Register.RSP, 8) // classes
          /* mov %rsp, %rsi    */ .movMR(Register.RSP, Register.RSI, OptionalInt.empty()) // count
          /* mov addr, %rdi    */ .movImm(Register.RDI, jvmtiEnv.getRawAddress()) // address of jvmtiEnv
          /* mov addr, %r10    */ .movImm(Register.R10, jvmtiEnv.getLoadedClassesAddr().address()) // address of GetLoadedClasses()
          /* sub $8, %rsp      */ .sub(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
          /* call %r10         */ .call(Register.R10)
          /* add $8, %rsp      */ .add(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
      // call callback(jclass *classes, jint class_count)
          /* pop %rsi          */ .pop(Register.RSI, OptionalInt.empty())
          /* mov (%rsp), %rdi  */ .movRM(Register.RDI, Register.RSP, OptionalInt.of(0))
          /* mov %rax, %rdx    */ .movMR(Register.RAX, Register.RDX, OptionalInt.empty()) // result of GetLoadedClasses()
          /* mov %r12, %rcx    */ .movMR(Register.R12, Register.RCX, OptionalInt.empty()) // callbackParam
          /* mov addr, %r10    */ .movImm(Register.R10, cbStub.address()) // address of callback
          /* call %r12         */ .call(Register.R10)
      // call Deallocate()
          /* mov addr, %rdi    */ .movImm(Register.RDI, jvmtiEnv.getRawAddress()) // address of jvmtiEnv
          /* pop %rsi          */ .pop(Register.RSI, OptionalInt.empty()) // classes
          /* mov addr, %r10    */ .movImm(Register.R10, jvmtiEnv.deallocateAddr().address()) // address of Deallocate()
          /* sub $8, %rsp      */ .sub(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
          /* call %r10         */ .call(Register.R10)
          /* add $8, %rsp      */ .add(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
      // epilogue
          /* pop %r12          */ .pop(Register.R12, OptionalInt.empty()) // classes
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();
  }

  static{
    arena = Arena.ofAuto();
    try{
      seg = new CodeSegment();
      var segForCleaner = seg;
      Cleaner.create()
             .register(NativeRegister.class, () -> {
               try{
                 segForCleaner.close();
               }
               catch(Exception e){
                 // ignore
               }
             });
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
