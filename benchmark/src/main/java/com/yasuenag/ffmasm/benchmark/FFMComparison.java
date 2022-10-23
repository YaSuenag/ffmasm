package com.yasuenag.ffmasm.benchmark;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview", "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=.", "-Xms4g", "-Xmx4g", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch"})
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
public class FFMComparison{

  private CodeSegment seg;

  private MethodHandle ffmRDTSC;

  @Setup
  public void setup(){
    System.loadLibrary("rdtsc");

    try{
      seg = new CodeSegment();
      ffmRDTSC = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, FunctionDescriptor.of(ValueLayout.JAVA_LONG))
           /* push %rbp      */ .push(Register.RBP)
           /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
           /* rdtsc          */ .rdtsc()
           /* shl $32, %rdx  */ .shl(Register.RDX, (byte)32, OptionalInt.empty())
           /* or %rdx, %rax  */ .orMR(Register.RDX, Register.RAX, OptionalInt.empty())
           /* leave          */ .leave()
           /* ret            */ .ret()
                                .build();
    }
    catch(PlatformException | UnsupportedPlatformException e){
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public native long rdtsc();

  @Benchmark
  public long invokeFFMRDTSC(){
    try{
      return (long)ffmRDTSC.invoke();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
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

  public static void main(String[] args){
    var inst = new FFMComparison();
    inst.setup();
    long nativeVal = inst.rdtsc();
    long ffmVal = inst.invokeFFMRDTSC();

    System.out.println("native: " + nativeVal);
    System.out.println("   FFM: " + ffmVal);
  }

}
