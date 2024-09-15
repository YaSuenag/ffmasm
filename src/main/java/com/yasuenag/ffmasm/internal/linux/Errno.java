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

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import com.yasuenag.ffmasm.PlatformException;


/**
 * Accessor of errno variable.
 * errno is defined as "*__errno_location()"
 * See /usr/include/errno.h and/or Linux Standard Base Core Specification.
 *  http://refspecs.linux-foundation.org/LSB_4.1.0/LSB-Core-generic/LSB-Core-generic/baselib---errno-location.html
 *
 * @author Yasumasa Suenaga
 */
public class Errno{

  private static final MethodHandle hnd;

  static{
    var sym = Linker.nativeLinker().defaultLookup();
    var func = sym.find("__errno_location").get();
    var desc = FunctionDescriptor.of(ValueLayout.ADDRESS);
    hnd = Linker.nativeLinker().downcallHandle(func, desc, Linker.Option.critical(false));
  }

  public static int get() throws PlatformException{
    MemorySegment errno;
    try{
      errno = ((MemorySegment)hnd.invoke()).reinterpret(4);
    }
    catch(Throwable t){
      throw new PlatformException(t);
    }
    return errno.get(ValueLayout.JAVA_INT, 0);
  }

}
