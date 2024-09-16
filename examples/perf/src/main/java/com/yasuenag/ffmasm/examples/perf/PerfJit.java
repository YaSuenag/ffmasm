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
      rdtsc = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
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
