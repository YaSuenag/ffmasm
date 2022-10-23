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
package com.yasuenag.ffmasm.test.amd64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.AVXAsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


public class AVXAsmTest{

  /**
   * Show PID, address of CodeSegment, then waits stdin input.
   */
  private static void showDebugMessage(CodeSegment seg) throws IOException{
    System.out.println("PID: " + ProcessHandle.current().pid());
    System.out.println("Addr: 0x" + Long.toHexString(seg.getAddr().toRawLongValue()));
    System.in.read();
  }

  /**
   * Tests MOVDQA A/B
   */
  @Test
  @Tag("avx")
  @Tag("linux")
  public void testMOVDQA(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
      /* push %rbp             */ .push(Register.RBP)
      /* mov %rsp, %rb p       */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
      /* vmovdqa (%rdi), %ymm0 */ .vmovdqaMR(Register.YMM0, Register.RDI, OptionalInt.of(0))
      /* vmovdqa %ymm0, (%rsi) */ .vmovdqaRM(Register.YMM0, Register.RSI, OptionalInt.of(0))
      /* leave                 */ .leave()
      /* ret                   */ .ret()
                                  .build();

      long[] expected = new long[]{1, 2, 3, 4}; // 64 * 4 = 256 bit
      var alloc = SegmentAllocator.implicitAllocator();
      MemorySegment src = alloc.allocate(32, 32);  // 256 bit
      MemorySegment dest = alloc.allocate(32, 32); // 256 bit
      MemorySegment.copy(expected, 0, src, ValueLayout.JAVA_LONG, 0, expected.length);

      method.invoke(src, dest);

      Assertions.assertArrayEquals(expected, src.toArray(ValueLayout.JAVA_LONG));
      Assertions.assertArrayEquals(expected, dest.toArray(ValueLayout.JAVA_LONG));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
