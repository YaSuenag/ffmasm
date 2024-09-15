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
package com.yasuenag.ffmasm.internal.linux.amd64;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.PlatformException;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.internal.linux.PerfJitDump;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;                       import com.yasuenag.ffmasm.amd64.Register;


public class AMD64PerfJitDump extends PerfJitDump{

  private static boolean initialized = false;

  private static CodeSegment seg;

  private static MethodHandle mhTSC;

  private static void init(){
    try{
      seg = new CodeSegment();
      var action = new CodeSegment.CleanerAction(seg);
      Cleaner.create()
             .register(AMD64PerfJitDump.class, action);

      var desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
      mhTSC = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /* rdtsc         */ .rdtsc()
         /* shl $32, %rdx */ .shl(Register.RDX, (byte)32, OptionalInt.empty())
         /* or %rdx, %rax */ .orMR(Register.RDX, Register.RAX, OptionalInt.empty())
         /* ret           */ .ret()
                             .build();
    }
    catch(PlatformException | UnsupportedPlatformException e){
      throw new RuntimeException(e);
    }

    initialized = true;
  }

  @Override
  protected long getHeaderFlags(){
    return JITDUMP_FLAGS_ARCH_TIMESTAMP;
  }

  @Override
  protected long getTimestamp(){
    if(!initialized){
      init();
    }

    try{
      return (long)mhTSC.invokeExact();
    }
    catch(Throwable t){
      throw new RuntimeException("Exception happened at RDTSC.", t);
    }
  }

  public AMD64PerfJitDump(Path dir) throws UnsupportedPlatformException, PlatformException, IOException{
    super(dir);
  }

}
