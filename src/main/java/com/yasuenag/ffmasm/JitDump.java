/*
 * Copyright (C) 2024, Yasumasa Suenaga
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

import java.io.IOException;
import java.nio.file.Path;

import com.yasuenag.ffmasm.internal.linux.amd64.AMD64PerfJitDump;


public interface JitDump extends AutoCloseable{

  public static JitDump getInstance(Path dir) throws UnsupportedPlatformException, PlatformException, IOException{
    var osName = System.getProperty("os.name");
    if(!osName.equals("Linux")){
      throw new UnsupportedPlatformException(osName + " is unsupported.");
    }
    if(System.getProperty("os.arch").equals("amd64")){
      return new AMD64PerfJitDump(dir);
    }

    throw new UnsupportedPlatformException("This platform is not supported in JitDump");
  }

  public void writeFunction(CodeSegment.MethodInfo method);

}
