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
package com.yasuenag.ffmasm.test.linux;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.yasuenag.ffmasm.CodeSegment;


public class CodeSegmentTest{

  @Test
  @Tag("linux")
  public void testAllocateCodeSegmentWithDefaultSize(){
    try(var seg = new CodeSegment()){
      var addr = seg.getAddr();
      long startAddr = addr.address();
      try(var stream = Files.lines(Path.of("/proc/self/maps"))){
        String[] entries = stream.filter(l -> l.startsWith(Long.toHexString(startAddr)))
                                 .findFirst()
                                 .get()
                                 .split(" ");

        long endAddr = Long.parseLong(entries[0].split("-")[1], 16);
        char execBit = entries[1].charAt(2);

        Assertions.assertEquals(4096L, endAddr - startAddr);
        Assertions.assertEquals('x', execBit);
      }
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  @Test
  @Tag("linux")
  public void testAllocateCodeSegmentWithGivenSize(){
    final long size = 8192L;

    try(var seg = new CodeSegment(size)){
      var addr = seg.getAddr();
      long startAddr = addr.address();
      try(var stream = Files.lines(Path.of("/proc/self/maps"))){
        String[] entries = stream.filter(l -> l.startsWith(Long.toHexString(startAddr)))
                                 .findFirst()
                                 .get()
                                 .split(" ");

        long endAddr = Long.parseLong(entries[0].split("-")[1], 16);
        char execBit = entries[1].charAt(2);

        Assertions.assertEquals(size, endAddr - startAddr);
        Assertions.assertEquals('x', execBit);
      }
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  @Test
  @Tag("linux")
  public void testAlignment(){
    try(var seg = new CodeSegment()){
      seg.alignTo16Bytes();
      Assertions.assertEquals((byte)0, (byte)(seg.getTail() & 0xf), "Memory is not aligned: " + Long.toHexString(seg.getAddr().address() + seg.getTail()));
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
