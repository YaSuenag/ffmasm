package com.yasuenag.ffmasm.benchmark.funccall;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED", "-Djava.library.path=.", "-Xms4g", "-Xmx4g", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch"})
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
public class FuncCallComparison{

  private CodeSegment seg;

  private MethodHandle ffmRDTSC;
  private MethodHandle ffmRDTSCCritical;

  @Setup
  public void setup(){
    System.loadLibrary("rdtsc");

    try{
      seg = new CodeSegment();
      var desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
      var mem = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
           /* push %rbp      */ .push(Register.RBP)
           /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
           /* rdtsc          */ .rdtsc()
           /* shl $32, %rdx  */ .shl(Register.RDX, (byte)32, OptionalInt.empty())
           /* or %rdx, %rax  */ .orMR(Register.RDX, Register.RAX, OptionalInt.empty())
           /* leave          */ .leave()
           /* ret            */ .ret()
                                .getMemorySegment();

      ffmRDTSC = Linker.nativeLinker().downcallHandle(mem, desc);
      ffmRDTSCCritical = Linker.nativeLinker().downcallHandle(mem, desc, Linker.Option.critical(false));

      var register = NativeRegister.create(this.getClass());
      register.registerNatives(Map.of(this.getClass().getMethod("invokeFFMRDTSCRegisterNatives"), mem));
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  @Benchmark
  public native long invokeJNI();

  @Benchmark
  public long invokeFFMRDTSC(){
    try{
      return (long)ffmRDTSC.invoke();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  @Benchmark
  public long invokeFFMRDTSCCritical(){
    try{
      return (long)ffmRDTSCCritical.invoke();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  @Benchmark
  public native long invokeFFMRDTSCRegisterNatives();

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
    var inst = new FuncCallComparison();
    inst.setup();
    long nativeVal = inst.invokeJNI();
    long ffmVal = inst.invokeFFMRDTSC();
    long ffmCriticalVal = inst.invokeFFMRDTSCCritical();
    long ffmRegisterNativesVal = inst.invokeFFMRDTSCRegisterNatives();

    System.out.println("                  JNI: " + nativeVal);
    System.out.println("                  FFM: " + ffmVal);
    System.out.println("       FFM (Critical): " + ffmCriticalVal);
    System.out.println("FFM (RegisterNatives): " + ffmRegisterNativesVal);
  }

}
