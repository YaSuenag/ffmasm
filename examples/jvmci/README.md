ffmasm examples - jvmci
===================

This is an example of ffmasm to `getpid` syscall on AMD64. Machine code would be installed CodeCache on HotSpot via JVMCI.

# Requirements

* Java 25
* AMD64 Linux
* Maven

# How to run

## 1. Build cpumodel

```bash
mvn clean package
```

## 2. Run via Maven

```
mvn exec:exec@run
```

You can see disassemble code if you installed hsdis in your JDK.

# Details

[jvmci-adapter](../../tools/jvmci-adapter) provides `AsmBuilder` to install machine code into CodeCache on HotSpot. You can generate machine code in same way with normal `AsmBuilder`, but note that you have to emit `emitPrologue()` and `emitEpilogue()`.

```
var method = Main.class.getMethod("getPid");
new JVMCIAMD64AsmBuilder()
                    .emitPrologue()
/* mov %rax, $39 */ .movImm(Register.RAX, 39) // getpid
/* syscall       */ .syscall()
                    .emitEpilogue()
                    .install(method, 16);
```

`install()` requires frame size of the method (machine code). It should be 16 bytes at least in AMD64 - it means both return address and saved RBP on the stack.

For example, if you expand the stack like following, you have to set `32` as frame size.

```
new JVMCIAMD64AsmBuilder()
                    .emitPrologue()
/* sub $16, %rsp */ .sub(Register.RSP, 16, OptionalInt.empty())
                    .emitEpilogue()
```
