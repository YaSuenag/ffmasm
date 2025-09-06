ffmasm examples - aarch64
===================

This is an example of ffmasm on AArch64 Linux

# Requirements

* Java 22
* AArch64 Linux
* Maven

# How to run

## 1. Install ffmasm

```bash
$ mvn clean install
```

## 2. Build

```bash
$ cd examples/aarch64
$ mvn clean package
```

## 3. Run via Maven

```
$ mvn exec:java
```

## 4. [EXTRA] check assembly in GDB

Run GDB if you want to check assembled code.

You can see PID and address of `CodeSegment` on the console when you run `java -jar aarch64-asm-0.1.0.jar --stop` in below.

```
PID: 1847
Addr: 0xffffac5d8000
Result: 100

Press any key to exit...
```

Attach to JVM process (1847 in this case), and run `disas` on GDB.

```
$ gdb -p 1847

  :

(gdb) disas 0xffffac5d8000, 0xffffac5d8010
Dump of assembler code from 0xffffac5d8000 to 0xffffac5d8010:
   0x0000ffffac5d8000:  stp     x29, x30, [sp, #-16]!
   0x0000ffffac5d8004:  mov     x29, sp
   0x0000ffffac5d8008:  ldp     x29, x30, [sp], #16
   0x0000ffffac5d800c:  ret
End of assembler dump.
```
