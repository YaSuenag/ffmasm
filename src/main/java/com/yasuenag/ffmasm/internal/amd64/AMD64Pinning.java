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
    this.seg = segMem;
    var thisObj = this;
    var action = new CodeSegment.CleanerAction(segMem);
    Cleaner.create()
           .register(thisObj, action);

    createWrapper();

    var clazz = Pinning.class;
    var methodMap = Map.of(clazz.getDeclaredMethod("pinWrapper", Object.class), pinWrapperImpl,
                           clazz.getDeclaredMethod("unpinWrapper", Object.class, MemorySegment.class), unpinWrapperImpl);
    var register = NativeRegister.create(clazz);
    register.registerNatives(methodMap);
  }

  private void createWrapper() throws Throwable{
    var regs = CallingRegisters.getRegs();

    var pinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                        ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                        ValueLayout.ADDRESS,   // arg2 (jobject)
                                        ValueLayout.ADDRESS);  // arg3 (array)
    pinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
            /* push %rbp         */ .push(Register.RBP)
            /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
            /* mov arg3, arg2    */ .movMR(regs.arg3(), regs.arg2(), OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
            /* xor arg3, arg3    */ .xorMR(regs.arg3(), regs.arg3(), OptionalInt.empty()) // zero-clear arg3
            /* mov addr, tmpReg1 */ .movImm(regs.tmpReg1(), JniEnv.getInstance().getPrimitiveArrayCriticalAddr().address()) // address of GetPrimitiveArrayCritical()
            /* sub $32, %rsp     */ .sub(Register.RSP, 32, OptionalInt.empty()) // Shadow stack (for Windows: 32 bytes)
            /* call tmpReg1      */ .call(regs.tmpReg1())
            /* add $32, %rsp     */ .add(Register.RSP, 32, OptionalInt.empty()) // Recover shadow stack
            /* leave             */ .leave()
            /* ret               */ .ret()
                                    .getMemorySegment();

    var unpinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                          ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                          ValueLayout.ADDRESS,   // arg2 (jobject)
                                          ValueLayout.ADDRESS,   // arg3 (array)
                                          ValueLayout.ADDRESS);  // arg4 (carray)
    unpinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
              /* push %rbp         */ .push(Register.RBP)
              /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
              /* mov arg3, arg2    */ .movMR(regs.arg3(), regs.arg2(), OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
              /* mov arg4, arg3    */ .movMR(regs.arg4(), regs.arg3(), OptionalInt.empty()) // move arg4 (arg2 in Java)  to arg3
              /* xor arg4, arg4    */ .xorMR(regs.arg4(), regs.arg4(), OptionalInt.empty()) // zero-clear arg4
              /* mov addr, tmpReg1 */ .movImm(regs.tmpReg1(), JniEnv.getInstance().releasePrimitiveArrayCriticalAddr().address()) // address of ReleasePrimitiveArrayCritical()
              /* sub $32, %rsp     */ .sub(Register.RSP, 32, OptionalInt.empty()) // Shadow stack (for Windows: 32 bytes)
              /* call tmpReg1      */ .call(regs.tmpReg1())
              /* add $32, %rsp     */ .add(Register.RSP, 32, OptionalInt.empty()) // Recover shadow stack
              /* leave             */ .leave()
              /* ret               */ .ret()
                                      .getMemorySegment();

  }

}
