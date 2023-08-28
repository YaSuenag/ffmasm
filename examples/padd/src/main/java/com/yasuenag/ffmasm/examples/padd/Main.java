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
package com.yasuenag.ffmasm.examples.padd;

import java.lang.foreign.*;
import java.util.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;


public class Main{

  public static void main(String[] args) throws Throwable{
    System.out.println("PID: " + ProcessHandle.current().pid());

    int[] array1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
    int[] array2 = new int[]{8, 7, 6, 5, 4, 3, 2, 1};

    MemorySegment array1Seg = MemorySegment.ofArray(array1);
    MemorySegment array2Seg = MemorySegment.ofArray(array2);

    var desc = FunctionDescriptor.ofVoid(
                 ValueLayout.ADDRESS,
                 ValueLayout.ADDRESS
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

      var padd = AMD64AsmBuilder.create(AVXAsmBuilder.class, codeSegment, desc)
/* push %rbp                    */ .push(Register.RBP)
/* mov %rsp, %rbp               */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                   .cast(AVXAsmBuilder.class)
/* vmovdqu (<arg1>), %ymm0      */ .vmovdquMR(Register.YMM0, arg1, OptionalInt.of(0))
/* vpaddd (<arg2>), %ymm0, ymm0 */ .vpaddd(Register.YMM0, arg2, Register.YMM0, OptionalInt.of(0))
/* vmovdqu %ymm0, (<arg1>)      */ .vmovdquRM(Register.YMM0, arg1, OptionalInt.of(0))
/* leave                        */ .leave()
/* ret                          */ .ret()
                                   .build(Linker.Option.isTrivial());

      array1Seg.pin();
      array2Seg.pin();
      padd.invoke(array1Seg, array2Seg);
      array1Seg.unpin();
      array2Seg.unpin();

      for(int i : array1){
        System.out.println(i);
      }

      System.out.println();
      System.out.print("Press any key to exit...");
      System.out.flush();
      System.in.read();
    }

  }
}
