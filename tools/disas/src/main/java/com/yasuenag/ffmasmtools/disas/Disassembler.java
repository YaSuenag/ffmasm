/*
 * Copyright (C) 2025 Yasumasa Suenaga
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
package com.yasuenag.ffmasmtools.disas;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.yasuenag.ffmasm.UnsupportedPlatformException;


public class Disassembler{

  private static final MethodHandle decode_instructions_virtual;
  private static final MemorySegment disasOptions;

  private static Path getHSDISPath() throws UnsupportedPlatformException{
    var prop = System.getProperty("hsdis");
    if(prop != null){
      return Path.of(prop);
    }

    String libName = "hsdis-" + System.getProperty("os.arch");
    var osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      libName += ".so";
    }
    else if(osName.startsWith("Windows")){
      libName += ".dll";
    }
    else{
      throw new UnsupportedPlatformException(osName);
    }

    var javaHome = Path.of(System.getProperty("java.home"));
    var vmMatcher = Pattern.compile("^.+ ([^ ]+) VM$").matcher(System.getProperty("java.vm.name"));
    vmMatcher.find();
    var vmType = vmMatcher.group(1).toLowerCase();
    // Search order of hsdis:
    //   1. <home>/lib/<vm>/libhsdis-<arch>.so
    //   2. <home>/lib/<vm>/hsdis-<arch>.so
    //   3. <home>/lib/hsdis-<arch>.so
    //   4. hsdis-<arch>.so  (using LD_LIBRARY_PATH)
    // See src/hotspot/share/compiler/disassembler.cpp in OpenJDK for details.
    Path p = javaHome.resolve("lib", vmType, "lib" + libName);
    if(p.toFile().exists()){
      return p;
    }
    else{
      p = javaHome.resolve("lib", vmType, libName);
      if(p.toFile().exists()){
        return p;
      }
      else{
        p = javaHome.resolve("lib", libName);
        if(p.toFile().exists()){
          return p;
        }
      }
    }
    return Path.of(libName);
  }

  static{
    try{
      var hsdisPath = getHSDISPath();
      var sym = SymbolLookup.libraryLookup(hsdisPath, Arena.ofAuto());
      var disas = sym.find("decode_instructions_virtual").get();
      var desc = FunctionDescriptor.of(ValueLayout.ADDRESS,   // return value
                                       ValueLayout.ADDRESS,   // start_va
                                       ValueLayout.ADDRESS,   // end_va
                                       ValueLayout.ADDRESS,   // buffer
                                       ValueLayout.JAVA_LONG, // length
                                       ValueLayout.ADDRESS,   // event_callback
                                       ValueLayout.ADDRESS,   // event_stream
                                       ValueLayout.ADDRESS,   // printf_callback
                                       ValueLayout.ADDRESS,   // printf_stream
                                       ValueLayout.ADDRESS,   // options
                                       ValueLayout.JAVA_INT   // newline
                                      );
      decode_instructions_virtual = Linker.nativeLinker()
                                          .downcallHandle(disas, desc);
      disasOptions = Arena.ofAuto()
                          .allocateFrom("");
    }
    catch(UnsupportedPlatformException e){
      throw new RuntimeException(e);
    }
  }

  public static void dumpToStdout(MemorySegment code){
    try{
      decode_instructions_virtual.invoke(
        code,                          // start_va
        code.asSlice(code.byteSize()), // end_va
        code,                          // buffer
        code.byteSize(),               // length
        MemorySegment.NULL,            // event_callback
        MemorySegment.NULL,            // event_stream
        MemorySegment.NULL,            // printf_callback
        MemorySegment.NULL,            // printf_stream
        disasOptions,                  // options
        1                              // newline
      );
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

}
