Benchmark for vector operation
===================

[JMH](https://github.com/openjdk/jmh) benchmark to comparison Pure Java code, [Vector API](https://openjdk.org/jeps/426), and ffmasm.

This benchmark adds with 256bit packed int. Pure Java and Vector API are expected to get benefit from C2.

# Requirements

* Java 22
* Maven

# How to build

```sh
$ cd /path/to/ffasm
$ mvn install
$ cd benchmark/vectorapi
$ mvn package
```

# Run benchmark

```sh
$ $JAVA_HOME/bin/java -jar target/ffmasm-benchmark-vectorapi-1.0.4.jar
```
