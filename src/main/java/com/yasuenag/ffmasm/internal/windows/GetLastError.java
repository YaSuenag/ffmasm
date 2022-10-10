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
package com.yasuenag.ffmasm.internal.windows;

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import com.yasuenag.ffmasm.PlatformException;


/**
 * Accessor of GetLastError()
 *
 * @author Yasumasa Suenaga
 */
public class GetLastError{

  private static final MethodHandle hnd;

  static{
    System.loadLibrary("Kernel32");
    var sym = SymbolLookup.loaderLookup();
    var func = sym.lookup("GetLastError").get();
    var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
    hnd = Linker.nativeLinker().downcallHandle(func, desc);
  }

  public static int get() throws PlatformException{
    try{
      return (int)hnd.invoke();
    }
    catch(Throwable t){
      throw new PlatformException(t);
    }
  }

}
