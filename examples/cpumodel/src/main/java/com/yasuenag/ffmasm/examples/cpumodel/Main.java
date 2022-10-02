/*
 * Copyright (C) 2022 Yasumasa Suenaga
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
package com.yasuenag.ffmasm.examples.cpumodel;

import java.lang.foreign.*;
import java.nio.charset.*;
import java.util.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;


public class Main{

  public static void main(String[] args) throws Throwable{
    System.out.println("PID: " + ProcessHandle.current().pid());

    var mem = SegmentAllocator.implicitAllocator().allocate(4 * 4 * 3); // 32bit * 4 regs (eax - edx) * 3 calls
    var desc = FunctionDescriptor.ofVoid(
                 ValueLayout.JAVA_INT, // eax
                 ValueLayout.ADDRESS   // memory for eax - edx
               );
    try(var codeSegment = new CodeSegment()){
      System.out.println("Addr: 0x" + Long.toHexString(codeSegment.getAddr().toRawLongValue()));

      var cpuid = AMD64AsmBuilder.create(codeSegment, desc)
        /* push %rbp          */ .push(Register.RBP)
        /* mov %rsp, %rbp     */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /* mov %rdi, %rax     */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
        /* cpuid              */ .cpuid()
        /* mov %eax,   (%rsi) */ .movRM(Register.EAX, Register.RSI, OptionalInt.of(0))
        /* mov %ebx,  4(%rsi) */ .movRM(Register.EBX, Register.RSI, OptionalInt.of(4))
        /* mov %ecx,  8(%rsi) */ .movRM(Register.ECX, Register.RSI, OptionalInt.of(8))
        /* mov %edx, 12(%rsi) */ .movRM(Register.EDX, Register.RSI, OptionalInt.of(12))
        /* leave              */ .leave()
        /* ret                */ .ret()
                                 .build();

      cpuid.invoke(0x80000002, mem);
      cpuid.invoke(0x80000003, mem.asSlice(4 * 4));
      cpuid.invoke(0x80000004, mem.asSlice(4 * 4 * 2));

      String model = mem.getUtf8String(0L);
      System.out.println(model);

      System.out.println();
      System.out.print("Press any key to exit...");
      System.out.flush();
      System.in.read();
    }

  }
}
