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
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


@Tag("amd64")
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
   * Tests prologue (push, movRM), movRM, epilogue (leave, ret)
   */
  @Test
  public void testBasicInstructions(){
    try(var seg = new CodeSegment()){
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

      final int expected = 100;
      int actual = (int)method.invoke(expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests CPUID and 32 bit movRM
   */
  @Test
  public void testCPUID(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
          /* mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
          /* cpuid             */ .cpuid()
          /* mov %edx, %eax    */ .movRM(Register.EDX, Register.EAX, OptionalInt.empty())
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
  public void testNOP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
          /* nop               */ .nop()
          /* mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
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
  public void testCMPandJL(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void test16bitCMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_SHORT, // 1st argument (success)
                   ValueLayout.JAVA_SHORT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %di     */ .cmp(Register.DI, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %si, %ax      */ .movRM(Register.SI, Register.AX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %di, %ax      */ .movRM(Register.DI, Register.AX, OptionalInt.empty()) // success
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
  public void test8bitCMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_BYTE, // 1st argument (success)
                   ValueLayout.JAVA_BYTE  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   mov %rdi, %rbx    */ .movRM(Register.RDI, Register.RCX, OptionalInt.empty())
        /*   mov %rsi, %rcx    */ .movRM(Register.RSI, Register.RDX, OptionalInt.empty())
        /*   cmp   $1, %cl     */ .cmp(Register.CL, 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov %dl, %al      */ .movRM(Register.DL, Register.AL, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %cl, %al      */ .movRM(Register.CL, Register.AL, OptionalInt.empty()) // success
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
  public void testADDs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
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
  public void testSUBs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov %rdi, %rax */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty())
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
   * Test JAE
   */
  @Test
  public void testJAE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jae success       */ .jae("success")
        /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $10, %rdi   */ .cmp(Register.RDI, 10, OptionalInt.empty())
        /*   je success        */ .je("success")
        /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJNE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jne success       */ .jne("success")
        /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testFwdBackJMP(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
        /*   jl fwd            */ .jl("fwd")
        /* exit:               */ .label("exit")
        /*   mov %rdi, %rax    */ .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* fwd:                */ .label("fwd")
        /*   jmp exit          */ .jmp("exit")
        /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
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
  public void testJLwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jl success        */ .jl("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJAEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jae success       */ .jae("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJNEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 1, OptionalInt.empty())
         /*   jne success       */ .jne("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJEwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   cmp   $1, %rdi    */ .cmp(Register.RDI, 10, OptionalInt.empty())
         /*   je success        */ .je("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testJMPwithImm32(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (success)
                   ValueLayout.JAVA_INT  // 2nd argument (failure)
                 );
      var builder = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp         */ .push(Register.RBP)
         /*   mov %rsp, %rbp    */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
         /*   mov %rsi, %rax    */ .movRM(Register.RSI, Register.RAX, OptionalInt.empty()) // failure
         /*   jmp success       */ .jmp("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov %rdi, %rax    */        .movRM(Register.RDI, Register.RAX, OptionalInt.empty()) // success
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
  public void testUndefinedLabel(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid();
      Assertions.assertThrows(IllegalStateException.class, () -> {
          AMD64AsmBuilder.create(seg, desc)
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
  public void testRDRAND(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      var method = AMD64AsmBuilder.create(seg, desc)
         /*   push %rbp      */ .push(Register.RBP)
         /*   mov %rsp, %rbp */ .movRM(Register.RSP, Register.RBP, OptionalInt.empty())
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
   * Test address alignment
   */
  @Test
  public void testAlignment(){
    try(var seg = new CodeSegment()){
      var byteBuf = seg.getTailOfMemorySegment()
                       .asByteBuffer()
                       .order(ByteOrder.nativeOrder());
      var desc = FunctionDescriptor.ofVoid();
      var builder = AMD64AsmBuilder.create(seg, desc)
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
