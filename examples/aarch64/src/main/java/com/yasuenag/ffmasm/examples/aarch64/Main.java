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
package com.yasuenag.ffmasm.examples.aarch64;

import java.lang.foreign.*;
import java.util.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.aarch64.*;


public class Main{

  public static void main(String[] args) throws Throwable{
    System.out.println("PID: " + ProcessHandle.current().pid());

    var desc = FunctionDescriptor.of(
                 ValueLayout.JAVA_INT,
                 ValueLayout.JAVA_INT
               );
    try(var codeSegment = new CodeSegment()){
      System.out.println("Addr: 0x" + Long.toHexString(codeSegment.getAddr().address()));
      var func = new AArch64AsmBuilder(codeSegment, desc)
/* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
/* mov x29,  sp              */ .mov(Register.X29, Register.SP)
/* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
/* ret                       */ .ret(Optional.empty())
                                .build();
      int result = (int)func.invoke(100);
      System.out.println("Result: " + result);

      if((args.length > 0) && args[0].equals("--stop")){
        System.out.println();
        System.out.print("Press any key to exit...");
        System.out.flush();
        System.in.read();
      }
    }

  }
}
