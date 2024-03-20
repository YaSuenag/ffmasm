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

```sh
$ cd /path/to/ffasm
$ mvn install
$ cd benchmark/funccall
$ mvn package
```

# Check result from RDTSC

```sh
$ mvn exec:exec@single-run
```

# Run benchmark

```sh
$ cd target
$ $JAVA_HOME/bin/java -jar ffmasm-benchmark-funccall-1.1.0.jar
```
