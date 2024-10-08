Example of profiling with perf tool
===================

This example shows how to use `JitDump` to profile your assembly code generated by ffmasm. [PerfJit.java](src/main/java/com/yasuenag/ffmasm/examples/perf/PerfJit.java) issues `RDRAND` instruction in 10,000,000 times.

# How to build

```
cd /path/to/ffasm
mvn install
cd examples/perf
mvn package
```

# Run test

[perf.sh](perf.sh) runs the example with `perf` and injects jitted code (it means assembly code generated by ffmasm)  with `perf inject`.

```
./perf.sh
```

You can see `jit-<PID>.dump`, `jitted-*.so`, and `perf.*data*` on working directory of `perf.sh`. Please keep them until you run `perf report`.

# Profiling with `perf report`

You need to specify `perf.jit.data` with `-i` option to load injected data.

```
perf report -i perf.jit.data
```
