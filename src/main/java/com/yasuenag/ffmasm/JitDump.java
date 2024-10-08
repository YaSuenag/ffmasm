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

import com.yasuenag.ffmasm.internal.linux.PerfJitDump;


/**
 * Interface of jitdump for perf command on Linux.
 *
 * @author Yasumasa Suenaga
 */
public interface JitDump extends AutoCloseable{

  /**
   * Get instance of JitDump.
   *
   * @param dir Base directory which jitdump is generated.
   * @throws UnsupportedPlatformException if the call happens on unsupported platform.
   */
  public static JitDump getInstance(Path dir) throws UnsupportedPlatformException, PlatformException, IOException{
    var osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      return new PerfJitDump(dir);
    }

    throw new UnsupportedPlatformException("This platform is not supported in JitDump");
  }

  /**
   * Write method info to jitdump.
   *
   * @param method MethodInfo should be written.
   */
  public void writeFunction(CodeSegment.MethodInfo method);

}
