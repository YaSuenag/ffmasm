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
package com.yasuenag.ffmasm.test.common;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.internal.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.internal.amd64.SSEAsmBuilder;
import com.yasuenag.ffmasm.internal.amd64.AVXAsmBuilder;
import com.yasuenag.ffmasm.internal.aarch64.AArch64AsmBuilder;


public class AsmBuilderTest{

  private static CodeSegment seg;

  @BeforeAll
  public static void init(){
    try{
      seg = new CodeSegment();
    }
    catch(Exception e){
      throw new Error(e);
    }
  }

  @AfterAll
  public static void tearDown(){
    try{
      seg.close();
    }
    catch(Exception e){
      // Do nothing
    }
  }

  @Test
  @EnabledOnOs(architectures = {"amd64"})
  public void testAMD64AsmBuilderOnAMD64() throws UnsupportedPlatformException{
    Assertions.assertTrue(new AsmBuilder.AMD64(seg) instanceof AMD64AsmBuilder);
  }

  @Test
  @EnabledOnOs(architectures = {"amd64"})
  public void testSSEAsmBuilderOnAMD64() throws UnsupportedPlatformException{
    Assertions.assertTrue(new AsmBuilder.SSE(seg) instanceof SSEAsmBuilder);
  }

  @Test
  @EnabledOnOs(architectures = {"amd64"})
  public void testAVXAsmBuilderOnAMD64() throws UnsupportedPlatformException{
    Assertions.assertTrue(new AsmBuilder.AVX(seg) instanceof AVXAsmBuilder);
  }

  @Test
  @EnabledOnOs(architectures = {"amd64"})
  public void testAArch64AsmBuilderOnAMD64() throws UnsupportedPlatformException{
    Assertions.assertThrows(UnsupportedPlatformException.class, () -> new AsmBuilder.AArch64(seg));
  }

  @Test
  @EnabledOnOs(architectures = {"aarch64"})
  public void testAMD64AsmBuilderOnAArch64(){
    Assertions.assertThrows(UnsupportedPlatformException.class, () -> new AsmBuilder.AMD64(seg));
  }

  @Test
  @EnabledOnOs(architectures = {"aarch64"})
  public void testSSEAsmBuilderOnAArch64(){
    Assertions.assertThrows(UnsupportedPlatformException.class, () -> new AsmBuilder.SSE(seg));
  }

  @Test
  @EnabledOnOs(architectures = {"aarch64"})
  public void testAVXAsmBuilderOnAArch64(){
    Assertions.assertThrows(UnsupportedPlatformException.class, () -> new AsmBuilder.AVX(seg));
  }

  @Test
  @EnabledOnOs(architectures = {"aarch64"})
  public void testAArch64AsmBuilderOnAArch64() throws UnsupportedPlatformException{
    Assertions.assertTrue(new AsmBuilder.AArch64(seg) instanceof AArch64AsmBuilder);
  }

}
