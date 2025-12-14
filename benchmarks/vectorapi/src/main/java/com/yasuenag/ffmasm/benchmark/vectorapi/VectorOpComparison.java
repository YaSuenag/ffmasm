package com.yasuenag.ffmasm.benchmark.vector;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

import jdk.incubator.vector.*;

import sun.misc.Unsafe;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import com.yasuenag.ffmasmtools.jvmci.amd64.*;

import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1,
      jvmArgsAppend = {
        "--enable-native-access=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector",
        "--add-exports=jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
        "-Xms6g",
        "-Xmx6g",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED",
        "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.site=ALL-UNNAMED",
        "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED",
        "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED",
        "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED"
      })
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
public class VectorOpComparison{

  private int[] randArray;
  private int[] result;

  private static final CodeSegment seg;
  private static final MethodHandle ffm;

  static{
    try{
      seg = new CodeSegment();
      var action = new CodeSegment.CleanerAction(seg);
      Cleaner.create()
             .register(VectorOpComparison.class, action);

      var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
      ffm = new AsmBuilder.AVX(seg, desc)
/* vmovdqu (%rdi), %ymm0       */ .vmovdquRM(Register.YMM0, Register.RDI, OptionalInt.of(0))
/* vpaddd (%rsi), %ymm0, %ymm0 */ .vpaddd(Register.YMM0, Register.RSI, Register.YMM0, OptionalInt.of(0))
/* vmovdqu %ymm0, (%rdi)       */ .vmovdquMR(Register.YMM0, Register.RDI, OptionalInt.of(0))
/* ret                         */ .ret()
                                  .build(Linker.Option.critical(true));

      var targetMethod = VectorOpComparison.class.getDeclaredMethod("addInJVMCI", int[].class, int[].class);
      var jvmciBuilder = new JVMCIAVXAsmBuilder();
      var arrayOffset = OptionalInt.of(Unsafe.ARRAY_INT_BASE_OFFSET);
      jvmciBuilder.emitPrologue()
/* vmovdqu ofs(%rsi), %ymm0        */ .vmovdquRM(Register.YMM0, Register.RSI, arrayOffset)
/* vpaddd  ofs(%rdx), %ymm0, %ymm0 */ .vpaddd(Register.YMM0, Register.RDX, Register.YMM0, arrayOffset)
/* vmovdqu %ymm0,     ofs(%rsi)    */ .vmovdquMR(Register.YMM0, Register.RSI, arrayOffset)
                                      .emitEpilogue()
                                      .install(targetMethod, 16);
    }
    catch(Exception e){
      throw new RuntimeException(e);
    }
  }

  @Setup(Level.Iteration)
  public void paramSetup(){
    randArray = (new Random()).ints()
                              .limit(8)
                              .toArray();
    result = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
  }

  @Benchmark
  public int[] invokeJava(){
    for(int i = 0; i < 8; i++){
      result[i] += randArray[i];
    }
    return result;
  }

  @Benchmark
  public int[] invokeFFM() throws Throwable{
    ffm.invoke(MemorySegment.ofArray(result), MemorySegment.ofArray(randArray));
    return result;
  }

  @Benchmark
  public int[] invokeVector(){
    var vectorSrc = IntVector.fromArray(IntVector.SPECIES_256, randArray, 0);
    var vectorDest = IntVector.fromArray(IntVector.SPECIES_256, result, 0);
    vectorDest.add(vectorSrc).intoArray(result, 0);
    return result;
  }

  private void addInJVMCI(int[] src, int[] dest){
    // This method should be overwritten by JVMCI
    throw new RuntimeException("Not implemented");
  }

  @Benchmark
  public int[] invokeJVMCI(){
    addInJVMCI(result, randArray);
    return result;
  }

}
