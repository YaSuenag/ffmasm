ffmasm
===================

![CI result](../../actions/workflows/ci.yml/badge.svg)
![CodeQL](../../actions/workflows/codeql-analysis.yml/badge.svg)

ffmasm is an assembler for hand-assembling from Java.  
It uses Foreign Function & Memory API, so the application can call assembled code via [MethodHandle](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/invoke/MethodHandle.html).

* Javadoc: https://yasuenag.github.io/ffmasm/
* Maven package: https://github.com/YaSuenag/ffmasm/packages/
* Supported instructions: See builder classes in [com.yasuenag.ffmasm.amd64](https://yasuenag.github.io/ffmasm/com.yasuenag.ffmasm/com/yasuenag/ffmasm/amd64/package-summary.html).

# Requirements

Java 22

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
It implements [AutoCloseable](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/AutoCloseable.html), so you can use try-with-resources in below:

```java
try(var seg = new CodeSegment()){
  ...
}
```

## 2. Create `MethodHandle` via `AMD64AsmBuilder`

You can assemble the code via `AMD64AsmBuilder`. It would be instanciated via `create()`, and it should be passed both `CodeSegment` and [FunctionDescriptor](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/FunctionDescriptor.html).

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
                            .build(Linker.Option.critical(false));
```

NOTE: [Linker.Option.critical()](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/Linker.Option.html#critical(boolean)) is recommended to pass `build()` method due to performance, but it might be cause of some issues in JVM (time to synchronize safepoint, memory corruption, etc). See Javadoc of `critical()`.

## 3. Method call

```java
int ret = (int)method.invoke(100); // "ret" should be 100
```

# Play with JNI

You can bind native method to `MemorySegment` of ffmasm code dynamically.

You have to construct `MemorySegment` of the machine code with `AMD64AsmBuilder`, and you have to get it from `getMemorySegment()`. Then you can bind it via `NativeRegister`.

Following example shows native method `test` is binded to the code made by ffmasm. Note that 1st argument in Java is located at arg3 in native function because this is native function (1st arg is `JNIEnv*`, and 2nd arg is `jobject` or `jclass`).

```java
public native int test(int arg);

<snip>

try(var seg = new CodeSegment()){
  var desc = FunctionDescriptor.of(
               ValueLayout.JAVA_INT, // return value
               ValueLayout.JAVA_INT, // 1st arg (JNIEnv *)
               ValueLayout.JAVA_INT, // 2nd arg (jobject)
               ValueLayout.JAVA_INT  // 3rd arg (arg1 of caller)
             );
  var stub = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
    /* push %rbp         */ .push(Register.RBP)
    /* mov %rsp, %rbp    */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
    /* mov %arg3, retReg */ .movMR(argReg.arg3(), argReg.returnReg(), OptionalInt.empty()) // arg1 in Java is arg3 in native
    /* leave             */ .leave()
    /* ret               */ .ret()
                            .getMemorySegment();

  var method = this.getClass()
                   .getMethod("test", int.class);

  var methodMap = Map.of(method, stub);
  var register = NativeRegister.create(this.getClass());
  register.registerNatives(methodMap);

  final int expected = 100;
  int actual = test(expected);
  Assertions.assertEquals(expected, actual);
}
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
