/*
 * Copyright (C) 2022, 2023, Yasumasa Suenaga
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

    var mem = Arena.ofAuto().allocate(4 * 4 * 3); // 32bit * 4 regs (eax - edx) * 3 calls
    var desc = FunctionDescriptor.ofVoid(
                 ValueLayout.JAVA_INT, // eax
                 ValueLayout.ADDRESS   // memory for eax - edx
               );
    try(var codeSegment = new CodeSegment()){
      System.out.println("Addr: 0x" + Long.toHexString(codeSegment.getAddr().address()));

      Register arg1, arg2;
      String osName = System.getProperty("os.name");
      if(osName.equals("Linux")){
        arg1 = Register.RDI;
        arg2 = Register.RSI;
      }
      else if(osName.startsWith("Windows")){
        arg1 = Register.RCX;
        arg2 = Register.RDX;
      }
      else{
        throw new RuntimeException("Unsupported OS: " + osName);
      }

      var cpuid = AMD64AsmBuilder.create(AMD64AsmBuilder.class, codeSegment, desc)
        /* push %rbp          */ .push(Register.RBP)
        /* mov %rsp, %rbp     */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /* push %rbx          */ .push(Register.RBX)
        /* mov <arg1>, %rax   */ .movMR(arg1, Register.RAX, OptionalInt.empty())
        /* mov <arg2>, %r11   */ .movMR(arg2, Register.R11, OptionalInt.empty())
        /* cpuid              */ .cpuid()
        /* mov %eax,   (%r11) */ .movMR(Register.EAX, Register.R11, OptionalInt.of(0))
        /* mov %ebx,  4(%r11) */ .movMR(Register.EBX, Register.R11, OptionalInt.of(4))
        /* mov %ecx,  8(%r11) */ .movMR(Register.ECX, Register.R11, OptionalInt.of(8))
        /* mov %edx, 12(%r11) */ .movMR(Register.EDX, Register.R11, OptionalInt.of(12))
        /* pop %rbx           */ .pop(Register.RBX, OptionalInt.empty())
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
