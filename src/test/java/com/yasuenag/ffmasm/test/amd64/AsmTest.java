/*
 * Copyright (C) 2022 Yasumasa Suenaga
 *
 * This file is part of ffmasm.
 *
 * ffmasm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ffmasm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ffmasm.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.yasuenag.ffmasm.test.amd64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


public class AsmTest{

  /**
   * Show PID, address of CodeSegment, then waits stdin input.
   */
  private static void showDebugMessage(CodeSegment seg) throws IOException{
    System.out.println("PID: " + ProcessHandle.current().pid());
    System.out.println("Addr: 0x" + Long.toHexString(seg.getAddr().toRawLongValue()));
    System.in.read();
  }

  /**
   * Tests prologue (push, movMR), movMR, epilogue (leave, ret)
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testBasicInstructions(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();

      final int expected = 100;
      int actual = (int)method.invoke(expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests PUSH and POP
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testPUSHandPOP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(
                   ValueLayout.JAVA_LONG,  // 1st argument
                   ValueLayout.JAVA_SHORT, // 2nd Argument
                   ValueLayout.ADDRESS     // 3rd Argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov  %rsp, %rbp   */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* push %rdi         */ .push(Register.RDI)
          /* push %si          */ .push(Register.SI)
          /* pop  %ax          */ .pop(Register.AX, OptionalInt.empty())
          /* mov  %ax, 8(%rdx) */ .movMR(Register.AX, Register.RDX, OptionalInt.of(8))
          /* pop  (%rdx)       */ .pop(Register.RDX, OptionalInt.of(0))
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();

      //showDebugMessage(seg);
      try(var session = MemorySession.openConfined()){
        var mem = session.allocate(10, 8);
        method.invoke(1, (short)2, mem);

        Assertions.assertEquals(1L, mem.get(ValueLayout.JAVA_LONG, 0));
        Assertions.assertEquals((short)2, mem.get(ValueLayout.JAVA_SHORT, 8));
      }
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests OR
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testOR(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
             /* or  %rsi, %rax */ .orMR(Register.RSI, Register.RAX, OptionalInt.empty())
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();

      int actual = (int)method.invoke(1, 2);
      Assertions.assertEquals(3, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests CPUID and 32 bit movMR
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testCPUID(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
          /* cpuid             */ .cpuid()
          /* mov %edx, %eax    */ .movMR(Register.EDX, Register.EAX, OptionalInt.empty())
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(0x1); // EAX = 01H
      int SSE2flag = (actual >>> 26) & 0x1;
      Assertions.assertEquals(1, SSE2flag, "Cannot get SSE2 support flag.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test NOP
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testNOP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* nop               */ .nop()
          /* mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
          /* nop               */ .nop()
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();

      final int expected = 100;
      int actual = (int)method.invoke(expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests CMP, JL, and label
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testCMPandJL(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(0, 10);
      Assertions.assertEquals(0, actual, "Seems not to jump at JL.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests 16 bit CMP
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void test16bitCMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_SHORT, // 1st argument (success)
                   ValueLayout.JAVA_SHORT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %di     */ .cmp(Register.DI, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %si, %ax      */ .movMR(Register.SI, Register.AX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %di, %ax      */ .movMR(Register.DI, Register.AX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke((short)0, (short)10);
      Assertions.assertEquals(0, actual, "16 bit CMP test failed.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests 8 bit CMP
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void test8bitCMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_BYTE, // 1st argument (success)
                   ValueLayout.JAVA_BYTE  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   mov %rdi, %rcx    */ .movMR(Register.RDI, Register.RCX, OptionalInt.empty())
        /*   mov %rsi, %rcx    */ .movMR(Register.RSI, Register.RDX, OptionalInt.empty())
        /*   cmp   $1, %cl     */ .cmp(Register.CL, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %dl, %al      */ .movMR(Register.DL, Register.AL, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %cl, %al      */ .movMR(Register.CL, Register.AL, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke((byte)0, (byte)10);
      Assertions.assertEquals(0, actual, "8 bit CMP test failed.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests ADDs
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testADDs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
             /* add $1, %al    */ .add(Register.AL, 1, OptionalInt.empty())
             /* add $2, %ax    */ .add(Register.AX, 2, OptionalInt.empty())
             /* add $3, %eax   */ .add(Register.EAX, 3, OptionalInt.empty())
             /* add $4, %rax   */ .add(Register.RAX, 4, OptionalInt.empty())
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();

      int actual = (int)method.invoke(0);
      Assertions.assertEquals(10, actual, "Add operations failed.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests SUBs
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testSUBs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
             /* sub $1, %al    */ .sub(Register.AL, 1, OptionalInt.empty())
             /* sub $2, %ax    */ .sub(Register.AX, 2, OptionalInt.empty())
             /* sub $3, %eax   */ .sub(Register.EAX, 3, OptionalInt.empty())
             /* sub $4, %rax   */ .sub(Register.RAX, 4, OptionalInt.empty())
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();

      int actual = (int)method.invoke(20);
      Assertions.assertEquals(10, actual, "Sub operations failed.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests SHLs
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testSHLs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty())
             /* shl $1, %al    */ .shl(Register.AL, (byte)1, OptionalInt.empty())
             /* shl $2, %ax    */ .shl(Register.AX, (byte)2, OptionalInt.empty())
             /* shl $3, %eax   */ .shl(Register.EAX, (byte)3, OptionalInt.empty())
             /* shl $4, %rax   */ .shl(Register.RAX, (byte)4, OptionalInt.empty())
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();

      int actual = (int)method.invoke(1);
      Assertions.assertEquals(1024, actual, "SHL operations failed.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JAE
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJAE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jae success       */ .jae("success")
        /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JAE.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JE
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $10, %rdi   */ .cmp(Register.RDI, 10, OptionalInt.empty())
        /*   je success        */ .je("success")
        /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JE.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JNE
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJNE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jne success       */ .jne("success")
        /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JNE.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests forward & backward jump.
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testFwdBackJMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jl fwd            */ .jl("fwd")
        /* exit:               */ .label("exit")
        /*   mov %rdi, %rax    */ .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* fwd:                */ .label("fwd")
        /*   jmp exit          */ .jmp("exit")
        /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(0, 10);
      Assertions.assertEquals(0, actual, "Jump tests failure.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JL with imm32
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJLwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jl success        */ .jl("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */        .leave()
        /*   ret               */        .ret();

      var method = builder.build();
      int actual = (int)method.invoke(0, 10);
      Assertions.assertEquals(0, actual, "Seems not to jump at JL with imm32.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JAE with imm32
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJAEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jae success       */ .jae("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */        .leave()
        /*   ret               */        .ret();

      var method = builder.build();
      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JAE with imm32.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JNE with imm32
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJNEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jne success       */ .jne("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */        .leave()
        /*   ret               */        .ret();

      var method = builder.build();
      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JNE with imm32.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JE with imm32
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 10, OptionalInt.empty())
         /*   je success        */ .je("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */        .leave()
        /*   ret               */        .ret();

      var method = builder.build();
      int actual = (int)method.invoke(10, 0);
      Assertions.assertEquals(10, actual, "Seems not to jump at JE with imm32.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests JMP with imm32
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  public void testJMPwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movMR(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   jmp success       */ .jmp("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movMR(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */        .leave()
        /*   ret               */        .ret();

      var method = builder.build();
      int actual = (int)method.invoke(0, 10);
      Assertions.assertEquals(0, actual, "Seems not to jump at JMP with imm32.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test throwing IllegalStateException if undefined label is remaining
   * when build() is called.
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  @Tag("windows")
  public void testUndefinedLabel(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid();
      Assertions.assertThrows(IllegalStateException.class, () -> {
          AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
                         .jl("SilverBullet")
                         .build();
      });
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test RDRAND
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  @Tag("windows")
  public void testRDRAND(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp      */ .push(Register.RBP)
         /*   mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   rdrand %ax     */ .rdrand(Register.AX)  // encode check
         /*   rdrand %eax    */ .rdrand(Register.EAX) // encode check
         /* retry:           */ .label("retry")
         /*   rdrand %rax    */ .rdrand(Register.RAX)
         /*   jae retry      */ .jae("retry")
         /*   leave          */ .leave()
         /*   ret            */ .ret()
                                .build();

      //showDebugMessage(seg);
      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test RDSEED
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  @Tag("windows")
  public void testRDSEED(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp      */ .push(Register.RBP)
         /*   mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   rdseed %ax     */ .rdseed(Register.AX)  // encode check
         /*   rdseed %eax    */ .rdseed(Register.EAX) // encode check
         /* retry:           */ .label("retry")
         /*   rdseed %rax    */ .rdseed(Register.RAX)
         /*   jae retry      */ .jae("retry")
         /*   leave          */ .leave()
         /*   ret            */ .ret()
                                .build();

      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test RDTSC
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  @Tag("windows")
  public void testRDTSC(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /*   push %rbp      */ .push(Register.RBP)
         /*   mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   rdtsc          */ .rdtsc()
         /*   leave          */ .leave()
         /*   ret            */ .ret()
                                .build();

      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test address alignment
   */
  @Test
  @Tag("amd64")
  @Tag("linux")
  @Tag("windows")
  public void testAlignment(){
    try(var seg = new CodeSegment()){
      var byteBuf = seg.getTailOfMemorySegment()
                       .asByteBuffer()
                       .order(ByteOrder.nativeOrder());
      var desc = FunctionDescriptor.ofVoid();
      var builder = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
                                   .nop()
                                   .alignTo16BytesWithNOP()
                                   .build();

      Assertions.assertEquals(16, seg.getTail(), "Memory size is not aligned.");
      byte[] array = new byte[16];
      byteBuf.get(array, 0, 16);
      for(byte b : array){
        Assertions.assertEquals((byte)0x90, b, "Not NOP");
      }
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
