/*
 * Copyright (C) 2022, 2024, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.internal.linux;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.Map;

import com.yasuenag.ffmasm.internal.ExecMemory;
import com.yasuenag.ffmasm.PlatformException;


/**
 * Aquiring / releasing memory for execution code for Linux.
 * This class uses mmap(2) and munmap(2) for it.
 *
 * @author Yasumasa Suenaga
 */
public class LinuxExecMemory implements ExecMemory{

  private static final Linker nativeLinker;

  private static final SymbolLookup sym;

  private static final Map<String, MemoryLayout> canonicalLayouts;

  private static final Linker.Option errnoState;

  private static final MemorySegment errnoSeg;

  private MethodHandle hndMmap = null;

  private MethodHandle hndMunmap = null;

  private VarHandle hndErrno = null;

  /**
   * page can be read
   */
  public static final int PROT_READ = 0x1;

  /**
   * page can be written
   */
  public static final int PROT_WRITE = 0x2;

  /**
   * page can be executed
   */
  public static final int PROT_EXEC = 0x4;

  /**
   * Changes are private
   */
  public static final int MAP_PRIVATE = 0x02;

  /**
   * don't use a file
   */
  public static final int MAP_ANONYMOUS = 0x20;

  static{
    nativeLinker = Linker.nativeLinker();
    sym = nativeLinker.defaultLookup();
    canonicalLayouts = nativeLinker.canonicalLayouts();
    errnoState = Linker.Option.captureCallState("errno");
    errnoSeg = Arena.global().allocate(Linker.Option.captureStateLayout());
  }

  private MemorySegment mmap(MemorySegment addr, long length, int prot, int flags, int fd, long offset) throws PlatformException{
    if(hndMmap == null){
      var func = sym.find("mmap").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.ADDRESS, // return value
                   ValueLayout.ADDRESS, // addr
                   canonicalLayouts.get("size_t"), // length
                   ValueLayout.JAVA_INT, // prot
                   ValueLayout.JAVA_INT, // flags
                   ValueLayout.JAVA_INT, // fd
                   ValueLayout.JAVA_LONG // offset
                 );
      hndMmap = nativeLinker.downcallHandle(func, desc, errnoState);
    }

    try{
      MemorySegment mem = (MemorySegment)hndMmap.invoke(errnoSeg, addr, length, prot, flags, fd, offset);
      if(mem.address() == -1L){ // MAP_FAILED
        if(hndErrno == null){
          hndErrno = Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("errno"));
        }
        throw new PlatformException("mmap() failed", (int)hndErrno.get(errnoSeg, 0L));
      }
      return mem.reinterpret(length);
    }
    catch(Throwable t){
      throw new PlatformException(t);
    }
  }

  private int munmap(MemorySegment addr, long length) throws PlatformException{
    if(hndMunmap == null){
      var func = sym.find("munmap").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.ADDRESS, // addr
                   canonicalLayouts.get("size_t") // length
                 );
      hndMunmap = nativeLinker.downcallHandle(func, desc, Linker.Option.critical(false));
    }

    try{
      int retval = (int)hndMunmap.invoke(addr, length);
      if(retval == -1){
        if(hndErrno == null){
          hndErrno = Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("errno"));
        }
        throw new PlatformException("munmap() failed", (int)hndErrno.get(errnoSeg, 0L));
      }
      return retval; // it should be 0
    }
    catch(Throwable t){
      throw new PlatformException(t);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MemorySegment allocate(long size) throws PlatformException{
    return mmap(MemorySegment.NULL, size, PROT_EXEC | PROT_READ | PROT_WRITE,
                MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deallocate(MemorySegment addr, long size) throws PlatformException{
    munmap(addr, size);
  }

}
