package com.yasuenag.ffmasm.benchmark.funccall;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED", "-Djava.library.path=.", "-Xms4g", "-Xmx4g", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch", "-XX:+PreserveFramePointer", "-Xlog:jit+compilation=debug,jit+inlining=debug:file=jit%p.log::filesize=0"})
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
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
      return (long)ffmRDTSC.invokeExact();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }
  }

  @Benchmark
  public long invokeFFMRDTSCCritical(){
    try{
      return (long)ffmRDTSCCritical.invokeExact();
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

  public void singleRun(){
    long nativeVal = invokeJNI();
    long ffmVal = invokeFFMRDTSC();
    long ffmCriticalVal = invokeFFMRDTSCCritical();
    long ffmRegisterNativesVal = invokeFFMRDTSCRegisterNatives();

    System.out.println("                  JNI: " + nativeVal);
    System.out.println("                  FFM: " + ffmVal);
    System.out.println("       FFM (Critical): " + ffmCriticalVal);
    System.out.println("FFM (RegisterNatives): " + ffmRegisterNativesVal);
  }

  public void iterate(String benchmark){
    Runnable runner = switch(benchmark){
                        case "JNI" -> this::invokeJNI;
                        case "FFM" -> this::invokeFFMRDTSC;
                        case "FFMCritical" -> this::invokeFFMRDTSCCritical;
                        case "RegisterNatives" -> this::invokeFFMRDTSCRegisterNatives;
                        default -> throw new IllegalArgumentException("Unknown benchmark");
                      };

    final AtomicLong counter = new AtomicLong();

    var task = new TimerTask(){
                 @Override
                 public void run(){
                   System.out.printf("Interrupted: counter = %d\n", counter.get());
                   System.exit(0);
                 }
               };
    var timer = new Timer("Benchmark Finisher");
    timer.schedule(task, 30_000);

    long startTime = System.nanoTime();
    while(true){
      runner.run();
      if(counter.incrementAndGet() == Long.MAX_VALUE){
        timer.cancel();
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        System.out.printf("Finished: %d ns\n", elapsedTime);
      }
    }
  }

  public static void main(String[] args){
    if(args.length < 1){
      System.err.println("You should specify the mode.");
      System.exit(1);
    }
    String mode = args[0];

    var inst = new FuncCallComparison();
    inst.setup();

    if(mode.equals("single")){
      inst.singleRun();
    }
    else if(mode.equals("iterate")){
      if(args.length != 2){
        System.err.println("You should specify the benchmark.");
        System.exit(2);
      }
      inst.iterate(args[1]);
    }
    else{
      System.err.println("Unknown mode.");
      System.exit(3);
    }
  }

}
