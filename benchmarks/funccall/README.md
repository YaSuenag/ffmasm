Benchmark for calling function
===================

[JMH](https://github.com/openjdk/jmh) benchmark to comparison between JNI and ffmasm.

This benchmark runs RDTSC instruction. JNI function is written in assembly code in [rdtsc.S](src/main/native/rdtsc.S), and same code is assembled by ffmasm in [FFMComparison.java](src/main/java/com/yasuenag/ffmasm/benchmark/FFMComparison.java). So you can see calling performacne JNI and Foreign Function & Memory API.

# Requirements

* Java 22
* Maven
* GNU Make
* GNU assembler

# How to build

```
cd /path/to/ffasm
mvn install
cd benchmark/funccall
mvn package
```

# Check result from RDTSC

```
mvn exec:exec@single-run
```

# Run benchmark

```
cd target
$JAVA_HOME/bin/java -jar ffmasm-benchmark-funccall-1.0.3.jar
```

JIT log ( `-Xlog:jit+compilation=debug,jit+inlining=debug` ) would be collected into `target` dir with PID.

# Run single benchmark

You can use [measure-single-benchmark.sh](measure-single-benchmark.sh) to run single benchmark.  
Benchmark should be set from following list:

* FFM
* FFMCritical
* RegisterNatives
* JNI

JIT log ( `-Xlog:jit+compilation=debug,jit+inlining=debug` ) would be collected into `target` dir with benchmark name.

## Measures iterations or execution time

```
./measure-single-benchmark.sh run [benchmark]
```

## Measure iterations or execution time with `perf`

You have to install perf tool before running.

```
./measure-single-benchmark.sh perf [benchmark]
```

`perf.data` would be stored into working directory.
