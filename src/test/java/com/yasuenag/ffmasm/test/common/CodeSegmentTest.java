/*
 * Copyright (C) 2023, 2024, Yasumasa Suenaga
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.yasuenag.ffmasm.CodeSegment;


public class CodeSegmentTest{

  @Test
  public void testAlignment(){
    try(var seg = new CodeSegment()){
      seg.alignTo16Bytes();
      Assertions.assertEquals((byte)0, (byte)(seg.getTail() & 0xf), "Memory is not aligned: " + Long.toHexString(seg.getAddr().address() + seg.getTail()));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  @Test
  public void testMethodInfoString(){
    var info = new CodeSegment.MethodInfo(null, "func", 0x1234, 0xff);
    Assertions.assertEquals("0x1234 0xff func", info.toString());
  }

}
