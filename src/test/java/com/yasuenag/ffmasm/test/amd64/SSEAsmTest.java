/*
 * Copyright (C) 2024, Yasumasa Suenaga
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;
import com.yasuenag.ffmasm.amd64.SSEAsmBuilder;


public class SSEAsmTest extends TestBase{

  /**
   * Tests MOVDQA A/B
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVDQA(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(SSEAsmBuilder.class, seg, desc)
      /* push %rbp            */ .push(Register.RBP)
      /* mov %rsp, %rbp       */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                 .cast(SSEAsmBuilder.class)
      /* movdqa (arg1), %xmm0 */ .movdqaRM(Register.XMM0, argReg.arg1(), OptionalInt.of(0))
      /* movdqa %xmm0, (arg2) */ .movdqaMR(Register.XMM0, argReg.arg2(), OptionalInt.of(0))
      /* leave                */ .leave()
      /* ret                  */ .ret()
                                 .build();

      long[] expected = new long[]{1, 2}; // 64 * 2 = 128 bit
      var arena = Arena.ofAuto();
      MemorySegment src = arena.allocate(16, 16);  // 128 bit
      MemorySegment dest = arena.allocate(16, 16); // 128 bit
      MemorySegment.copy(expected, 0, src, ValueLayout.JAVA_LONG, 0, expected.length);

      method.invoke(src, dest);

      Assertions.assertArrayEquals(expected, src.toArray(ValueLayout.JAVA_LONG));
      Assertions.assertArrayEquals(expected, dest.toArray(ValueLayout.JAVA_LONG));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests MOVDQU A/B
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVDQU(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(SSEAsmBuilder.class, seg, desc)
      /* push %rbp            */ .push(Register.RBP)
      /* mov %rsp, %rbp       */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                 .cast(SSEAsmBuilder.class)
      /* movdqu (arg1), %xmm0 */ .movdquRM(Register.XMM0, argReg.arg1(), OptionalInt.of(0))
      /* movdqu %xmm0, (arg2) */ .movdquMR(Register.XMM0, argReg.arg2(), OptionalInt.of(0))
      /* leave                */ .leave()
      /* ret                  */ .ret()
                                 .build();

      long[] expected = new long[]{1, 2}; // 64 * 2 = 128 bit
      var arena = Arena.ofAuto();
      MemorySegment src = arena.allocate(16, 16);  // 128 bit
      MemorySegment dest = arena.allocate(16, 16); // 128 bit
      MemorySegment.copy(expected, 0, src, ValueLayout.JAVA_LONG, 0, expected.length);

      method.invoke(src, dest);

      Assertions.assertArrayEquals(expected, src.toArray(ValueLayout.JAVA_LONG));
      Assertions.assertArrayEquals(expected, dest.toArray(ValueLayout.JAVA_LONG));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests MOVD A
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVD_A(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_FLOAT, // return value
                   ValueLayout.ADDRESS     // 1st argument
                 );
      var method = AMD64AsmBuilder.create(SSEAsmBuilder.class, seg, desc)
      /* push %rbp             */ .push(Register.RBP)
      /* mov %rsp, %rbp        */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(SSEAsmBuilder.class)
      /* movd (arg1), %xmm0    */ .movdRM(Register.XMM0, argReg.arg1(), OptionalInt.of(0))
      /* leave                 */ .leave()
      /* ret                   */ .ret()
                                  .build();

      float expected = 1.1f;
      var arena = Arena.ofAuto();
      MemorySegment src = arena.allocate(ValueLayout.JAVA_FLOAT);
      src.set(ValueLayout.JAVA_FLOAT, 0, expected);

      float actual = (float)method.invoke(src);

      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests MOVD B
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVD_B(){
    try(var seg = new CodeSegment()){
      var arena = Arena.ofAuto();
      MemorySegment dest = arena.allocate(ValueLayout.JAVA_FLOAT);

      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.JAVA_FLOAT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(SSEAsmBuilder.class, seg, desc)
      /* push %rbp             */ .push(Register.RBP)
      /* mov %rsp, %rbp        */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
      // Mixed argument order (int, fp) is different between Windows and Linux.
      // Thus address is loaded from immediate value.
      /* mov addr, %rax        */ .movImm(Register.RAX, dest.address())
                                  .cast(SSEAsmBuilder.class)
      /* movd %xmm0, (%rax)    */ .movdMR(Register.XMM0, Register.RAX, OptionalInt.of(0))
      /* leave                 */ .leave()
      /* ret                   */ .ret()
                                  .build();

      float expected = 1.1f;

      method.invoke(expected);
      float actual = dest.get(ValueLayout.JAVA_FLOAT, 0);

      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
