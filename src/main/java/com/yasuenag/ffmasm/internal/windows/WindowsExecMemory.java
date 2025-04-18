/*
 * Copyright (C) 2022, 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.internal.windows;

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
 * Aquiring / releasing memory for execution code for Windows.
 * This class uses VirtualAlloc and VirtualFree for it.
 *
 * @author Yasumasa Suenaga
 */
public class WindowsExecMemory implements ExecMemory{

  private static final Linker nativeLinker;

  private static final SymbolLookup sym;

  private static final Map<String, MemoryLayout> canonicalLayouts;

  private static final Linker.Option getLastErrorState;

  private static final MemorySegment getLastErrorSeg;

  private MethodHandle hndVirtualAlloc = null;

  private MethodHandle hndVirtualFree = null;

  private VarHandle hndGetLastError = null;

  public static final int MEM_COMMIT = 0x00001000;

  public static final int MEM_RESERVE = 0x00002000;

  public static final int MEM_RELEASE = 0x00008000;

  public static final int PAGE_EXECUTE_READWRITE = 0x40;
  
  static{
    sym = SymbolLookup.libraryLookup("Kernel32", Arena.global());
    nativeLinker = Linker.nativeLinker();
    canonicalLayouts = nativeLinker.canonicalLayouts();
    getLastErrorState = Linker.Option.captureCallState("GetLastError");
    getLastErrorSeg = Arena.global().allocate(Linker.Option.captureStateLayout());
  }

  private MemorySegment virtualAlloc(long lpAddress, long dwSize, int flAllocationType, int flProtect) throws PlatformException{
    if(hndVirtualAlloc == null){
      var func = sym.find("VirtualAlloc").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.ADDRESS, // return value
                   ValueLayout.JAVA_LONG, // lpAddress
                   canonicalLayouts.get("long"), // dwSize
                   ValueLayout.JAVA_INT, // flAllocationType
                   ValueLayout.JAVA_INT // flProtect
                 );
      hndVirtualAlloc = nativeLinker.downcallHandle(func, desc, getLastErrorState);
    }

    try{
      MemorySegment mem = (MemorySegment)hndVirtualAlloc.invoke(getLastErrorSeg,
                                                                lpAddress,
                                                                (int)dwSize, // "long" is 32bit in LLP64
                                                                flAllocationType,
                                                                flProtect);
      if(mem.equals(MemorySegment.NULL)){
        if(hndGetLastError == null){
          hndGetLastError = Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
        }
        throw new PlatformException("VirtualAlloc() failed", (int)hndGetLastError.get(getLastErrorSeg, 0L));
      }
      return mem.reinterpret(dwSize);
    }
    catch(Throwable t){
      throw new PlatformException(t);
    }
  }

  /**
   * VirtualFree returns BOOL, it is defined in int.
   *   https://learn.microsoft.com/en-us/windows/win32/winprog/windows-data-types
   */
  private int virtualFree(MemorySegment lpAddress, long dwSize, int dwFreeType) throws PlatformException{
    if(hndVirtualFree == null){
      var func = sym.find("VirtualFree").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.ADDRESS, // addr
                   canonicalLayouts.get("long"), // dwSize
                   ValueLayout.JAVA_INT // dwFreeType
                 );
      hndVirtualFree = nativeLinker.downcallHandle(func, desc, getLastErrorState);
    }

    try{
      int result = (int)hndVirtualFree.invoke(getLastErrorSeg,
                                              lpAddress,
                                              (int)dwSize, // "long" is 32bit in LLP64
                                              dwFreeType);
      if(result == 0){
        if(hndGetLastError == null){
          hndGetLastError = Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
        }
        throw new PlatformException("VirtualFree() failed", (int)hndGetLastError.get(getLastErrorSeg, 0L));
      }
      return result; // it should be true
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
    return virtualAlloc(0, size, MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deallocate(MemorySegment addr, long size) throws PlatformException{
    virtualFree(addr, 0, MEM_RELEASE);
  }

}
