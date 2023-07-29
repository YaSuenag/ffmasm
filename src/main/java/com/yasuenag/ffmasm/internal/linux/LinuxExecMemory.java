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
package com.yasuenag.ffmasm.internal.linux;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import com.yasuenag.ffmasm.internal.ExecMemory;
import com.yasuenag.ffmasm.PlatformException;


/**
 * Aquiring / releasing memory for execution code for Linux.
 * This class uses mmap(2) and munmap(2) for it.
 *
 * @author Yasumasa Suenaga
 */
public class LinuxExecMemory implements ExecMemory{

  private static final SymbolLookup sym;

  private MethodHandle hndMmap = null;

  private MethodHandle hndMunmap = null;

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
    sym = Linker.nativeLinker().defaultLookup();
  }

  private MemorySegment mmap(long addr, long length, int prot, int flags, int fd, long offset) throws PlatformException{
    if(hndMmap == null){
      var func = sym.find("mmap").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.ADDRESS, // return value
                   ValueLayout.JAVA_LONG, // addr
                   ValueLayout.JAVA_LONG, // length
                   ValueLayout.JAVA_INT, // prot
                   ValueLayout.JAVA_INT, // flags
                   ValueLayout.JAVA_INT, // fd
                   ValueLayout.JAVA_LONG // offset
                 );
      hndMmap = Linker.nativeLinker().downcallHandle(func, desc, Linker.Option.isTrivial());
    }

    try{
      MemorySegment mem = (MemorySegment)hndMmap.invoke(addr, length, prot, flags, fd, offset);
      if(mem.address() == -1L){ // MAP_FAILED
        throw new PlatformException("mmap() failed", Errno.get());
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
                   ValueLayout.JAVA_LONG // length
                 );
      hndMunmap = Linker.nativeLinker().downcallHandle(func, desc, Linker.Option.isTrivial());
    }

    try{
      int retval = (int)hndMunmap.invoke(addr, length);
      if(retval == -1){
        throw new PlatformException("munmap() failed", Errno.get());
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
    return mmap(0, size, PROT_EXEC | PROT_READ | PROT_WRITE,
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
