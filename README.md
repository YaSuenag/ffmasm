ffmasm
===================

![CI result](../../actions/workflows/ci.yml/badge.svg)
![CodeQL](../../actions/workflows/codeql-analysis.yml/badge.svg)

ffmasm is an assembler for hand-assembling from Java.  
It uses Foreign Function & Memory API, so the application can call assembled code via [MethodHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html).

* Javadoc: https://yasuenag.github.io/ffmasm/
* Maven package: https://github.com/YaSuenag/ffmasm/packages/
* Supported instructions: See builder classes in [com.yasuenag.ffmasm.amd64](https://yasuenag.github.io/ffmasm/com.yasuenag.ffmasm/com/yasuenag/ffmasm/amd64/package-summary.html).

# Requirements

Java 21

# Supported platform

* Linux AMD64
* Windows AMD64

# How to build

```
$ mvn package
```

## Test for ffmasm

```bash
$ mvn test
```

If you want to run tests for AVX, set `true` to `avxtest` system property.

```bash
$ mvn -Davxtest=true test
```

# How to use

See [Javadoc](https://yasuenag.github.io/ffmasm/) and [cpumodel](examples/cpumodel) examples.

## 1. Create `CodeSegment`

`CodeSegment` is a storage for assembled code. In Linux, it would be allocated by `mmap(2)` with executable bit.  
It implements [AutoCloseable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html), so you can use try-with-resources in below:

```java
try(var seg = new CodeSegment()){
  ...
}
```

## 2. Create `MethodHandle` via `AMD64AsmBuilder`

You can assemble the code via `AMD64AsmBuilder`. It would be instanciated via `create()`, and it should be passed both `CodeSegment` and [FunctionDescriptor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/FunctionDescriptor.html).

In following example, the method is defined as `(I)I` (JNI signature) in `FunctionDescriptor`.  
`AMD64AsmBuilder` is builder pattern, so you can add instruction in below. Following example shows method argument (`int`) would be returned straightly.

You can get `MethodHandle` in result of `build()`.

```java
var desc = FunctionDescriptor.of(
             ValueLayout.JAVA_INT, // return value
             ValueLayout.JAVA_INT // 1st argument
           );

var method = AMD64AsmBuilder.create(seg, desc)
    /* push %rbp         */ .push(Register.RBP)
    /* mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
    /* mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
    /* leave             */ .leave()
    /* ret               */ .ret()
                            .build(Linker.Option.isTrivial());
```

NOTE: [Linker.Option.isTrivial()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/Linker.Option.html#isTrivial()) is recommended to pass `build()` method due to performance, but it might be cause of some issues in JVM (time to synchronize safepoint, memory corruption, etc). See Javadoc of `isTrivial()`.

## 3. Method call

```java
int ret = (int)method.invoke(100); // "ret" should be 100
```

# Memory pinning

You can pin Java primitive array via `Pinning`. It is same semantics of `GetPrimitiveArrayCritical()` / `ReleasePrimitiveArrayCritical()` in JNI. It expects performance improvement when you refer Java array in ffmasm code. However it might be serious problem (e.g. preventing GC) if you keep pinned memory long time. So you should use it carefully.

Following example shows pin `array` at first, then we modify value via `MemorySegment`.

```java
int[] array = new int[]{1, 2, 3, 4};

var pinnedMem = Pinning.getInstance()
                       .pin(array)
                       .reinterpret(ValueLayout.JAVA_INT.byteSize() * array.length);
for(int idx = 0; idx < expected.length; idx++){
  pinnedMem.setAtIndex(ValueLayout.JAVA_INT, idx, idx * 2);
}
Pinning.getInstance().unpin(pinnedMem);

// array is {0, 2, 4, 6} at this point
```

# License

The GNU Lesser General Public License, version 3.0
