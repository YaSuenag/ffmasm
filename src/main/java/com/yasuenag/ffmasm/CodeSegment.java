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
package com.yasuenag.ffmasm;

import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;

import com.yasuenag.ffmasm.internal.ExecMemory;
import com.yasuenag.ffmasm.internal.linux.LinuxExecMemory;
import com.yasuenag.ffmasm.internal.windows.WindowsExecMemory;


/**
 * Memory segment for executables.
 *
 * @author Yasumasa Suenaga
 */
public class CodeSegment implements AutoCloseable{

  /**
   * Default size of code segment.
   */
  public static final long DEFAULT_CODE_SEGMENT_SIZE = 4096L;

  private final ExecMemory mem;

  private final MemoryAddress addr;

  private final long size;

  private final MemorySegment seg;

  private long tail;

  /**
   * Allocate memory for this code segment with default size (4096 bytes).
   * @throws PlatformException thrown when native function call failed.
   * @throws UnsupportedPlatformException thrown when the platform is not supported.
   */
  public CodeSegment() throws PlatformException, UnsupportedPlatformException{
    this(DEFAULT_CODE_SEGMENT_SIZE);
  }

  /**
   * Allocate memory for this code segment.
   * @param size size of code segment.
   * @throws PlatformException thrown when native function call failed.
   * @throws UnsupportedPlatformException thrown when the platform is not supported.
   */
  public CodeSegment(long size) throws PlatformException, UnsupportedPlatformException{
    String osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      mem = new LinuxExecMemory();
    }
    else if(osName.startsWith("Windows")){
      mem = new WindowsExecMemory();
    }
    else{
      throw new UnsupportedPlatformException(osName + " is unsupported.");
    }

    this.size = size;
    this.addr = mem.allocate(size);
    this.seg = MemorySegment.ofAddress(addr, size, MemorySession.global());
    this.tail = 0L;
  }

  /**
   * Release memory for this code segment.
   */
  @Override
  public void close() throws Exception{
    mem.deallocate(addr, size);
  }

  /**
   * Get slice of this segment from the tail.
   *
   * @return Slice of this segment from the tail.
   */
  public MemorySegment getTailOfMemorySegment(){
    return seg.asSlice(tail);
  }

  /**
   * Align the tail to 16 bytes
   */
  public void alignTo16Bytes(){
    if((tail & 0xf) > 0){ // not aligned
      tail = (tail + 0x10) & 0xfffffffffffffff0L;
    }
  }

  /**
   * Get the tail of this segment.
   *
   * @return the tail of this segment.
   */
  public long getTail(){
    return tail;
  }

  /**
   * Increment the tail with given size.
   * @param size value to increment
   */
  public void incTail(long size){
    this.tail += size;
  }

  /**
   * Get MemoryAddress which relates to this segment.
   *
   * @return MemoryAddress of this segment.
   */
  public MemoryAddress getAddr(){
    return addr;
  }

}
