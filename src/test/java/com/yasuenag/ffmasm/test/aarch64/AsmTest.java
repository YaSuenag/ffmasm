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
package com.yasuenag.ffmasm.test.aarch64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.aarch64.IndexClasses;
import com.yasuenag.ffmasm.aarch64.Register;


@EnabledOnOs(architectures = {"aarch64"})
public class AsmTest{

  /**
   * Tests prologue (stp, mov), epilogue (ldp, ret)
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testBasicInstructions(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      final int expected = 100;
      int actual = (int)method.invoke(expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests LDR
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testLdr(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* stp  x0,  x1, [sp, #-16]! */ .stp(Register.X0, Register.X1, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
 /* ldr  x0, [sp, #8]         */ .ldr(Register.X0, Register.SP, IndexClasses.LDR_STR.UnsignedOffset, 8)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      final int expected = 200;
      int actual = (int)method.invoke(100, expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests addImm
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testAddImm(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );

      // 1. No shift
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* add  x0,  x0, #10         */ .addImm(Register.X0, Register.X0, 10, false)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();
      int expected = 110;
      int actual = (int)method.invoke(100);
      Assertions.assertEquals(expected, actual);

      // 2. Shift
      method = new AsmBuilder.AArch64(seg, desc)
/* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
/* mov x29,  sp              */ .mov(Register.X29, Register.SP)
/* add  x0,  x0, #1, lsl #12 */ .addImm(Register.X0, Register.X0, 1, true)
/* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
/* ret                       */ .ret(Optional.empty())
                                .build();
      expected = 4097;
      actual = (int)method.invoke(1);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests subImm
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testSubImm(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );

      // 1. No shift
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* sub  x0,  x0, #10         */ .subImm(Register.X0, Register.X0, 10, false)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();
      int expected = 90;
      int actual = (int)method.invoke(100);
      Assertions.assertEquals(expected, actual);

      // 2. Shift
      method = new AsmBuilder.AArch64(seg, desc)
/* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PreIndex, -16)
/* mov x29,  sp              */ .mov(Register.X29, Register.SP)
/* sub  x0,  x0, #1, lsl #12 */ .subImm(Register.X0, Register.X0, 1, true)
/* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClasses.LDP_STP.PostIndex, 16)
/* ret                       */ .ret(Optional.empty())
                                .build();
      expected = 1;
      actual = (int)method.invoke(4097);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
