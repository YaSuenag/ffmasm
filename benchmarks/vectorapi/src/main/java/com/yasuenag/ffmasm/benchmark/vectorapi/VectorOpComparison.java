package com.yasuenag.ffmasm.benchmark.vector;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

import jdk.incubator.vector.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview", "--enable-native-access=ALL-UNNAMED", "--add-modules", "jdk.incubator.vector", "--add-exports", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED", "-Xms6g", "-Xmx6g"})
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
public class VectorOpComparison{

  private int[] randArray;
  private int[] result;

  private CodeSegment seg;
  private MethodHandle ffm;
  private MemorySegment srcSeg;
  private MemorySegment destSeg;

  private IntVector vectorSrc;
  private IntVector vectorDest;

  @Setup(Level.Trial)
  public void setup(){

    try{
      seg = new CodeSegment();
      var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
      ffm = AMD64AsmBuilder.create(AVXAsmBuilder.class, seg, desc)
/* push %rbp                   */ .push(Register.RBP)
/* mov %rsp, %rbp              */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                                  .cast(AVXAsmBuilder.class)
/* vmovdqa (%rdi), %ymm0       */ .vmovdqaMR(Register.YMM0, Register.RDI, OptionalInt.of(0))
/* vpaddd (%rsi), %ymm0, %ymm0 */ .vpaddd(Register.YMM0, Register.RSI, Register.YMM0, OptionalInt.of(0))
/* vmovdqa %ymm0, (%rdi)       */ .vmovdqaRM(Register.YMM0, Register.RDI, OptionalInt.of(0))
/* leave                       */ .leave()
/* ret                         */ .ret()
                                  .build(Linker.Option.isTrivial());
    }
    catch(PlatformException | UnsupportedPlatformException e){
      throw new RuntimeException(e);
    }

    var arena = Arena.ofAuto();
    srcSeg = arena.allocate(32, 32);
    destSeg = arena.allocate(32, 32);
  }

  @Setup(Level.Iteration)
  public void paramSetup(){
    randArray = (new Random()).ints()
                              .limit(8)
                              .toArray();
    result = new int[]{0, 0, 0, 0, 0, 0, 0, 0};

    MemorySegment.copy(randArray, 0, srcSeg, ValueLayout.JAVA_INT, 0, 8);
    MemorySegment.copy(result, 0, destSeg, ValueLayout.JAVA_INT, 0, 8);

    vectorSrc = IntVector.fromArray(IntVector.SPECIES_256, randArray, 0);
    vectorDest = IntVector.fromArray(IntVector.SPECIES_256, result, 0);
   }

  @Benchmark
  public int[] invokeJava(){
    for(int i = 0; i < 8; i++){
      result[i] += randArray[i];
    }
    return result;
  }

  @Benchmark
  public int[] invokeFFM(){
    try{
      ffm.invoke(destSeg, srcSeg);
      return destSeg.toArray(ValueLayout.JAVA_INT);
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  @Benchmark
  public int[] invokeVector(){
    vectorDest.add(vectorSrc).intoArray(result, 0);
    return result;
  }

  @TearDown
  public void tearDown(){
    try{
      seg.close();
    }
    catch(Exception e){
      throw new RuntimeException(e);
    }
  }

}
