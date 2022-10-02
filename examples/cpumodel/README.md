ffmasm examples - cpumodel
===================

This is an example of ffmasm to obtain CPU model from `CPUID` instruction in AMD64.

# Requirements

* Java 19
* Linux AMD64
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
$ mvn exec:exec
```

Press enter to exit.

## 4. [EXTRA] check assembly in GDB

Run GDB if you want to check assembled code.

You can see PID and address of `CodeSegment` on the console when you run `mvn exec:exec` in below.

```
PID: 928
Addr: 0x7f046c3fc000
Intel(R) Core(TM) i3-8145U CPU @ 2.10GHz
```

Attach to JVM process (928 in this case), and run `disas` on GDB.

```
$ gdb -p 928

  :

(gdb) disas 0x7f046c3fc000, 0x7f046c3fc016
Dump of assembler code from 0x7f046c3fc000 to 0x7f046c3fc016:
   0x00007f046c3fc000:  push   %rbp
   0x00007f046c3fc001:  mov    %rsp,%rbp
   0x00007f046c3fc004:  mov    %rdi,%rax
   0x00007f046c3fc007:  cpuid
   0x00007f046c3fc009:  mov    %eax,(%rsi)
   0x00007f046c3fc00b:  mov    %ebx,0x4(%rsi)
   0x00007f046c3fc00e:  mov    %ecx,0x8(%rsi)
   0x00007f046c3fc011:  mov    %edx,0xc(%rsi)
   0x00007f046c3fc014:  leave
   0x00007f046c3fc015:  ret
End of assembler dump.
```

# Expected assembly code in this example

```assembly
# prologue
push %rbp
mov  %rsp, %rbp

# Set 1st argument to RAX
mov %rdi, %rax

cpuid

# Store result to given memory
mov %eax,   (%rsi)
mov %ebx,  4(%rsi)
mov %ecx,  8(%rsi)
mov %edx, 12(%rsi)

# Epilogue
leave
ret
```
