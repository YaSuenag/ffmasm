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
import com.yasuenag.ffmasm.aarch64.IndexClass;
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
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
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

}
