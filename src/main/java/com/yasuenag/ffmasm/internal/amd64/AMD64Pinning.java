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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.NativeRegister;
import com.yasuenag.ffmasm.Pinning;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;
import com.yasuenag.ffmasm.internal.JniEnv;


public final class AMD64Pinning extends Pinning{

  private final Arena arena;
  private final CodeSegment seg;

  private MemorySegment pinWrapperImpl;
  private MemorySegment unpinWrapperImpl;

  public AMD64Pinning() throws Throwable{
    super();
    this.arena = Arena.ofAuto();
    var segMem = new CodeSegment();
    var thisObj = this;
    Cleaner.create()
           .register(thisObj, () -> {
             try{
               segMem.close();
             }
             catch(Exception e){
               // ignore
             }
           });
    this.seg = segMem;

    createWrapper();

    var clazz = Pinning.class;
    var methodMap = Map.of(clazz.getDeclaredMethod("pinWrapper", Object.class), pinWrapperImpl,
                           clazz.getDeclaredMethod("unpinWrapper", Object.class, MemorySegment.class), unpinWrapperImpl);
    var register = NativeRegister.create(clazz);
    register.registerNatives(methodMap);
  }

  private void createWrapper() throws Throwable{
    var pinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                        ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                        ValueLayout.ADDRESS,   // arg2 (jobject)
                                        ValueLayout.ADDRESS);  // arg3 (array)
    pinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
               /* push %rbp      */ .push(Register.RBP)
               /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
               /* mov %rdx, %rsi */ .movMR(Register.RDX, Register.RSI, OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
               /* xor %rdx, %rdx */ .xorMR(Register.RDX, Register.RDX, OptionalInt.empty()) // xor arg3
               /* mov addr, %r10 */ .movImm(Register.R10, JniEnv.getInstance().getPrimitiveArrayCriticalAddr().address()) // address of GetPrimitiveArrayCritical()
               /* call %r10      */ .call(Register.R10)
               /* leave          */ .leave()
               /* ret            */ .ret()
                                    .getMemorySegment();

    var unpinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                          ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                          ValueLayout.ADDRESS,   // arg2 (jobject)
                                          ValueLayout.ADDRESS,   // arg3 (array)
                                          ValueLayout.ADDRESS);  // arg4 (carray)
    unpinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
                 /* push %rbp      */ .push(Register.RBP)
                 /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                 /* mov %rdx, %rsi */ .movMR(Register.RDX, Register.RSI, OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
                 /* mov %rcx, %rdx */ .movMR(Register.RCX, Register.RDX, OptionalInt.empty()) // move arg4 (arg2 in Java)  to arg3
                 /* xor %rcx, %rcx */ .xorMR(Register.RCX, Register.RCX, OptionalInt.empty()) // xor arg4
                 /* mov addr, %r10 */ .movImm(Register.R10, JniEnv.getInstance().releasePrimitiveArrayCriticalAddr().address()) // address of ReleasePrimitiveArrayCritical()
                 /* call %r10      */ .call(Register.R10)
                 /* leave          */ .leave()
                 /* ret            */ .ret()
                                      .getMemorySegment();

  }

}
