/*
 * Copyright (C) 2023, 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.test.amd64;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeAll;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.Register;


public class TestBase{

  public static record ArgRegister(
    Register arg1,
    Register arg2,
    Register arg3,
    Register arg4,
    Register returnReg
  ){}

  protected static ArgRegister argReg;

  private static boolean isAVX;

  private static boolean isCLFLUSHOPT;

  private static MethodHandle generateCPUID(CodeSegment seg) throws UnsupportedPlatformException{
    var desc = FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, /* eax    */
                                         ValueLayout.JAVA_INT, /* ecx    */
                                         ValueLayout.ADDRESS   /* result */
                                        );
    return new AsmBuilder.AMD64(seg, desc)
 /* push %rbp         */ .push(Register.RBP)
 /* mov %rsp, %rbp    */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
 /* push %rbx         */ .push(Register.RBX)
 /* mov arg1, %rax    */ .movRM(Register.RAX, argReg.arg1(), OptionalInt.empty())
 /* mov arg2, %rcx    */ .movRM(Register.RCX, argReg.arg2(), OptionalInt.empty())
 /* mov arg3, %r11    */ .movRM(Register.R11, argReg.arg3(), OptionalInt.empty())
 /* cpuid             */ .cpuid()
 /* mov %eax, (r11)   */ .movMR(Register.EAX, Register.R11, OptionalInt.of(0))
 /* mov %ebx, 4(r11)  */ .movMR(Register.EBX, Register.R11, OptionalInt.of(4))
 /* mov %ecx, 8(r11)  */ .movMR(Register.ECX, Register.R11, OptionalInt.of(8))
 /* mov %ecx, 12(r11) */ .movMR(Register.EDX, Register.R11, OptionalInt.of(12))
 /* pop %rbx          */ .pop(Register.RBX, OptionalInt.empty())
 /* leave             */ .leave()
 /* ret               */ .ret()
                         .build();
  }

  @BeforeAll
  public static void init(){
    String osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      argReg = new ArgRegister(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.RAX);
    }
    else if(osName.startsWith("Windows")){
      argReg = new ArgRegister(Register.RCX, Register.RDX, Register.R8, Register.R9, Register.RAX);
    }
    else{
      throw new RuntimeException(new UnsupportedPlatformException(osName));
    };

    try(var seg = new CodeSegment();
        var arena = Arena.ofConfined();){
      var cpuid = generateCPUID(seg);
      var cpuidVals = arena.allocate(ValueLayout.JAVA_INT, 4);

      // check AVX
      cpuid.invokeExact(1, 0, cpuidVals);
      isAVX = ((cpuidVals.getAtIndex(ValueLayout.JAVA_INT, 2) >>> 28) & 0x1) == 1; // ecx

      // check CLFLUSHOPT
      cpuid.invokeExact(7, 0, cpuidVals);
      isCLFLUSHOPT = ((cpuidVals.getAtIndex(ValueLayout.JAVA_INT, 1) >>> 23) & 0x1) == 1; // ebx
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  public static boolean supportAVX(){
    return isAVX;
  }

  public static boolean supportCLFLUSHOPT(){
    return isCLFLUSHOPT;
  }

  /**
   * Show PID, address of CodeSegment, then waits stdin input.
   */
  public void showDebugMessage(CodeSegment seg) throws IOException{
    System.out.println("PID: " + ProcessHandle.current().pid());
    System.out.println("Addr: 0x" + Long.toHexString(seg.getAddr().address()));
    System.in.read();
  }

}
