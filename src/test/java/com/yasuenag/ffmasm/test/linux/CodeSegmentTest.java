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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.PlatformException;
import com.yasuenag.ffmasm.UnsupportedPlatformException;


@EnabledOnOs({OS.LINUX})
public class CodeSegmentTest{

  private String[] findMemorySegmentFromMaps(long startAddr, long endAddr) throws IOException{
    String startAddrInStr = Long.toHexString(startAddr);
    String endAddrInStr = Long.toHexString(endAddr);
    try(var stream = Files.lines(Path.of("/proc/self/maps"))){
      return stream.map(l -> l.split(" "))
                   .filter(l -> l[0].startsWith(startAddrInStr) || l[0].endsWith(endAddrInStr))
                   .findAny()
                   .get();
    }
  }

  @Test
  public void testAllocateCodeSegmentWithDefaultSize(){
    try(var seg = new CodeSegment()){
      var addr = seg.getAddr();
      long startAddr = addr.address();
      long endAddr = startAddr + 4096L;

      var entries = findMemorySegmentFromMaps(startAddr, endAddr);
      long actualStartAddr = Long.parseLong(entries[0].split("-")[0], 16);
      long actualEndAddr = Long.parseLong(entries[0].split("-")[1], 16);
      char execBit = entries[1].charAt(2);

      Assertions.assertTrue((actualEndAddr - actualStartAddr) >= 4096L);
      Assertions.assertEquals('x', execBit);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  @Test
  public void testAllocateCodeSegmentWithGivenSize(){
    final long size = 8192L;

    try(var seg = new CodeSegment(size)){
      var addr = seg.getAddr();
      long startAddr = addr.address();
      long endAddr = startAddr + size;

      var entries = findMemorySegmentFromMaps(startAddr, endAddr);
      long actualStartAddr = Long.parseLong(entries[0].split("-")[0], 16);
      long actualEndAddr = Long.parseLong(entries[0].split("-")[1], 16);
      char execBit = entries[1].charAt(2);

      Assertions.assertTrue((actualEndAddr - actualStartAddr) >= size);
      Assertions.assertEquals('x', execBit);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  @Test
  public void testCloseWithCleaner() throws PlatformException, UnsupportedPlatformException, InterruptedException{
    Object obj = new Object();
    var seg = new CodeSegment();
    var rawAddr = seg.getAddr().address();
    var action = new CodeSegment.CleanerAction(seg);
    Cleaner.create()
           .register(obj, action);

    // Release obj
    obj = null;
    System.gc();

    // Attempt to give opportunity to work Clerner
    Thread.yield();
    Thread.sleep(3000);

    // Check memory mapping whether region for CodeSegment is released.
    Assertions.assertThrows(NoSuchElementException.class, () -> {
      try(var stream = Files.lines(Path.of("/proc/self/maps"))){
        stream.filter(l -> l.startsWith(Long.toHexString(rawAddr)))
                            .findAny()
                            .get();
      }
    });
  }

}
