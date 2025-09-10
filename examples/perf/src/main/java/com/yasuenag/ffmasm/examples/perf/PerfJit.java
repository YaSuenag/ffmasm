/*
 * Copyright (C) 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.examples.perf;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.file.*;
import java.util.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;


public class PerfJit{

  private static final CodeSegment seg;

  private static final MethodHandle rdtsc;

  private static final JitDump jitdump;

  static{
    try{
      seg = new CodeSegment();
      jitdump = JitDump.getInstance(Path.of("."));
      var desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
      rdtsc = new AsmBuilder.AMD64(seg, desc)
        /* .align 16     */ .alignTo16BytesWithNOP()
        /* retry:        */ .label("retry")
        /*   rdrand %rax */ .rdrand(Register.RAX)
        /*   jae retry   */ .jae("retry")
        /* ret           */ .ret()
                            .build("ffm_rdtsc", jitdump, Linker.Option.critical(false));
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  public static void main(String[] args) throws Throwable{
    try(jitdump; seg){
      for(int i = 0; i < 10_000_000; i++){
        long _ = (long)rdtsc.invokeExact();
      }
    }
  }

}
