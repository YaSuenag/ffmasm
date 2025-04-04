ffmasm examples - cpumodel
===================

This is an example of ffmasm to obtain CPU model from `CPUID` instruction in AMD64.

# Requirements

* Java 24
* AMD64 Linux or Windows
* Maven

# How to run

## 1. Install ffmasm

```bash
$ mvn clean install
```

## 2. Build cpumodel

```bash
$ cd examples/cpumodel
$ mvn clean package
```

## 3. Run via Maven

```
$ mvn exec:java
```

## 4. [EXTRA] check assembly in GDB

Run GDB if you want to check assembled code.

You can see PID and address of `CodeSegment` on the console when you run `java -jar cpumodel-0.1.3.jar --stop` in below.

```
PID: 928
Addr: 0x7f4c42704000
Intel(R) Core(TM) i3-8145U CPU @ 2.10GHz
```

Attach to JVM process (928 in this case), and run `disas` on GDB.

```
$ gdb -p 928

  :

(gdb) disas 0x7f4c42704000, 0x7f4c42704020
Dump of assembler code from 0x7f4c42704000 to 0x7f4c42704020:
   0x00007f4c42704000:  push   %rbp
   0x00007f4c42704001:  mov    %rsp,%rbp
   0x00007f4c42704004:  push   %rbx
   0x00007f4c42704005:  mov    %rdi,%rax
   0x00007f4c42704008:  mov    %rsi,%r11
   0x00007f4c4270400b:  cpuid
   0x00007f4c4270400d:  mov    %eax,(%r11)
   0x00007f4c42704010:  mov    %ebx,0x4(%r11)
   0x00007f4c42704014:  mov    %ecx,0x8(%r11)
   0x00007f4c42704018:  mov    %edx,0xc(%r11)
   0x00007f4c4270401c:  pop    %rbx
   0x00007f4c4270401e:  leave
   0x00007f4c4270401f:  ret
End of assembler dump.
```

# Calling convention

Calling convention is different between Linux AMD64 and Windows x64. Linux AMD64 conform to [System V Application Binary Interface](https://refspecs.linuxbase.org/elf/x86_64-abi-0.99.pdf), on the other hand Windows  conform to [here](https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention). You have to conform to them when you assemble.

# Expected assembly code in this example on Linux

```assembly
# prologue
push %rbp
mov  %rsp, %rbp

# Evacuate callee-saved register
push %rbx

# Copy arguments to temporary registers
mov %rdi,%rax
mov %rsi,%r11

# Call CPUID
cpuid

# Store result to given memory
mov %eax,   (%r11)
mov %ebx,  4(%r11)
mov %ecx,  8(%r11)
mov %edx, 12(%r11)

# Restore callee-saved register
pop %rbx

# Epilogue
leave
ret
```

# Play with AOT

Java 24 introduced [JEP 483: Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483). It can cache InvokeDynamic link, so first call in FFM could be faster.

[pom.xml](pom.xml) generates AOT cache in `package` phase, so you can feel acceralation with AOT as following. Note that you have to move `target` directory because AOT identifies class path.

```
$ cd target


$ time $JAVA_HOME/bin/java -jar cpumodel-0.1.3.jar
PID: 3147
Addr: 0x7f8d1fc37000
AMD Ryzen 3 3300X 4-Core Processor

real    0m0.140s
user    0m0.196s
sys     0m0.049s


$ time $JAVA_HOME/bin/java -XX:AOTCache=ffmasm-cpumodel.aot -jar cpumodel-0.1.3.jar
PID: 3167
Addr: 0x7f9000001000
AMD Ryzen 3 3300X 4-Core Processor

real    0m0.095s
user    0m0.121s
sys     0m0.041s
```
