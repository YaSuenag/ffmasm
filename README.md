ffmasm
===================

![CI result](../../actions/workflows/ci.yml/badge.svg)

ffmasm is an assembler for hand-assembling from Java.  
It uses Foreign Function & Memory API, so the application can call assembled code via [MethodHandle](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/lang/invoke/MethodHandle.html).

* Javadoc: https://yasuenag.github.io/ffmasm/
* Maven package: https://github.com/YaSuenag/ffmasm/packages/
* Supported instructions: https://yasuenag.github.io/ffmasm/com.yasuenag.ffmasm/com/yasuenag/ffmasm/amd64/AMD64AsmBuilder.html

# Requirements

Java 19

# Supported platform

* Linux AMD64
* Windows AMD64

# How to build

```
$ mvn package
```

## Test for ffmasm

ffmasm defines some groups for testing.

* `linux`
    * Tests for Linux
* `windows`
    * Tests for Windows
* `amd64`
    * Tests for AMD64

You can set them via `groups` system property:

### Run Linux tests only

```bash
$ mvn -Dgroups=linux test
```

### Run both Linux and AMD64 tests

```bash
$ mvn -Dgroups='linux & amd64' test
```

See [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/#running-tests-tag-expressions) for details.

# How to use

See [Javadoc](https://yasuenag.github.io/ffmasm/) and [cpumodel](examples/cpumodel) examples.

## 1. Create `CodeSegment`

`CodeSegment` is a storage for assembled code. In Linux, it would be allocated by `mmap(2)` with executable bit.  
It implements [AutoCloseable](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/lang/AutoCloseable.html), so you can use try-with-resources in below:

```java
try(var seg = new CodeSegment()){
  ...
}
```

## 2. Create `MethodHandle` via `AMD64AsmBuilder`

You can assemble the code via `AMD64AsmBuilder`. It would be instanciated via `create()`, and it should be passed both `CodeSegment` and [FunctionDescriptor](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/lang/foreign/FunctionDescriptor.html).

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
                            .build();
```

## 3. Method call

```java
int ret = (int)method.invoke(100); // "ret" should be 100
```

# License

The GNU Lesser General Public License, version 3.0
