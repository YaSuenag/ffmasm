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
package com.yasuenag.ffmasm.test.amd64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.ValueLayout;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.AVXAsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


@EnabledIfSystemProperty(named = "avxtest", matches = "true")
public class AVXAsmTest extends TestBase{

  /**
   * Tests MOVDQA A/B
   */
  @Test
  @EnabledOnOs(value = {OS.LINUX, OS.WINDOWS})
  public void testMOVDQA(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
      /* push %rbp             */ .push(Register.RBP)
      /* mov %rsp, %rbp        */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
      /* vmovdqa (arg1), %ymm0 */ .vmovdqaMR(Register.YMM0, argReg.arg1(), OptionalInt.of(0))
      /* vmovdqa %ymm0, (arg2) */ .vmovdqaRM(Register.YMM0, argReg.arg2(), OptionalInt.of(0))
      /* leave                 */ .leave()
      /* ret                   */ .ret()
                                  .build();

      long[] expected = new long[]{1, 2, 3, 4}; // 64 * 4 = 256 bit
      var alloc = SegmentAllocator.nativeAllocator(SegmentScope.auto());
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

  /**
   * Tests PXOR
   */
  @Test
  @EnabledOnOs(value = {OS.LINUX, OS.WINDOWS})
  public void testPXOR(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
  /* push %rbp                 */ .push(Register.RBP)
  /* mov %rsp, %rbp            */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
  /* vmovdqa (arg1), %ymm0     */ .vmovdqaMR(Register.YMM0, argReg.arg1(), OptionalInt.of(0))
  /* vpxor %ymm0, %ymm0, %ymm1 */ .vpxor(Register.YMM0, Register.YMM0, Register.YMM1, OptionalInt.empty())
  /* vmovdqa %ymm1, (arg2)     */ .vmovdqaRM(Register.YMM1, argReg.arg2(), OptionalInt.of(0))
  /* leave                     */ .leave()
  /* ret                       */ .ret()
                                  .build();

      int[]      src = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
      int[] expected = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
      var alloc = SegmentAllocator.nativeAllocator(SegmentScope.auto());
      MemorySegment srcSeg = alloc.allocate(32, 32);  // 256 bit
      MemorySegment destSeg = alloc.allocate(32, 32); // 256 bit
      MemorySegment.copy(src, 0, srcSeg, ValueLayout.JAVA_INT, 0, src.length);

      method.invoke(srcSeg, destSeg);

      Assertions.assertArrayEquals(src, srcSeg.toArray(ValueLayout.JAVA_INT));
      Assertions.assertArrayEquals(expected, destSeg.toArray(ValueLayout.JAVA_INT));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests PADDD
   */
  @Test
  @EnabledOnOs(value = {OS.LINUX, OS.WINDOWS})
  public void testPADDD(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.ADDRESS, // 1st argument
                   ValueLayout.ADDRESS, // 2nd argument
                   ValueLayout.ADDRESS  // 3rd argument
                 );
      var method = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
/* push %rbp                   */ .push(Register.RBP)
/* mov %rsp, %rbp              */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
/* vmovdqa (arg1), %ymm0       */ .vmovdqaMR(Register.YMM0, argReg.arg1(), OptionalInt.of(0))
/* vpaddd (arg2), %ymm0, %ymm1 */ .vpaddd(Register.YMM0, argReg.arg2(), Register.YMM1, OptionalInt.of(0))
/* vmovdqa %ymm1, (arg3)       */ .vmovdqaRM(Register.YMM1, argReg.arg3(), OptionalInt.of(0))
      /* leave                 */ .leave()
      /* ret                   */ .ret()
                                  .build();

      int[]     src1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
      int[]     src2 = new int[]{8, 7, 6, 5, 4, 3, 2, 1};
      int[] expected = new int[]{9, 9, 9, 9, 9, 9, 9, 9};
      var alloc = SegmentAllocator.nativeAllocator(SegmentScope.auto());
      MemorySegment src1Seg = alloc.allocate(32, 32);  // 256 bit
      MemorySegment src2Seg = alloc.allocate(32, 32);  // 256 bit
      MemorySegment destSeg = alloc.allocate(32, 32); // 256 bit
      MemorySegment.copy(src1, 0, src1Seg, ValueLayout.JAVA_INT, 0, src1.length);
      MemorySegment.copy(src2, 0, src2Seg, ValueLayout.JAVA_INT, 0, src2.length);

      method.invoke(src1Seg, src2Seg, destSeg);

      Assertions.assertArrayEquals(src1, src1Seg.toArray(ValueLayout.JAVA_INT));
      Assertions.assertArrayEquals(src2, src2Seg.toArray(ValueLayout.JAVA_INT));
      Assertions.assertArrayEquals(expected, destSeg.toArray(ValueLayout.JAVA_INT));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests PTEST
   */
  @Test
  @EnabledOnOs(value = {OS.LINUX, OS.WINDOWS})
  public void testPTEST(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.ADDRESS,  // 1st argument (operand)
                   ValueLayout.JAVA_INT, // 2nd argument (success)
                   ValueLayout.JAVA_INT  // 3rd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
  /* push %rbp                 */ .push(Register.RBP)
  /* mov %rsp, %rbp            */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
  /* vpxor %ymm0, %ymm0, %ymm0 */ .vpxor(Register.YMM0, Register.YMM0, Register.YMM0, OptionalInt.empty())
  /* vptest (arg1), %ymm0      */ .vptest(Register.YMM0, argReg.arg1(), OptionalInt.of(0))
  /* jz success                */ .jz("success")
  /* mov arg3, retReg          */ .movMR(argReg.arg3(), argReg.returnReg(), OptionalInt.empty())
  /* leave                     */ .leave()
  /* ret                       */ .ret()
  /* success:                  */ .label("success")
  /*   mov arg2, retReg        */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty())
  /*   leave                   */ .leave()
  /*   ret                     */ .ret()
                                  .build();

      int[]    zero = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
      int[] nonzero = new int[]{1, 1, 1, 1, 1, 1, 1, 1};
      var alloc = SegmentAllocator.nativeAllocator(SegmentScope.auto());
      MemorySegment zeroSeg = alloc.allocate(32, 32);  // 256 bit
      MemorySegment nonzeroSeg = alloc.allocate(32, 32); // 256 bit
      MemorySegment.copy(zero, 0, zeroSeg, ValueLayout.JAVA_INT, 0, zero.length);
      MemorySegment.copy(nonzero, 0, nonzeroSeg, ValueLayout.JAVA_INT, 0, nonzero.length);

      Assertions.assertEquals(0, (int)method.invoke(zeroSeg, 0, 1), "Should return zero");
      Assertions.assertEquals(1, (int)method.invoke(zeroSeg, 1, 1), "Should return 1");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
