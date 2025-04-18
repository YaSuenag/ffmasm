/*
 * Copyright (C) 2022, 2025, Yasumasa Suenaga
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


@EnabledOnOs(architectures = {"amd64"})
public class AsmTest extends TestBase{

  /**
   * Tests prologue (push, movMR), movMR, epilogue (leave, ret)
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testBasicInstructions(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* mov arg1, %rax    */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
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
  @EnabledOnOs(OS.LINUX)
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
          /* mov  %rdi, %r10   */ .movMR(Register.RDI, Register.R10, OptionalInt.empty())
          /* push %rdi         */ .push(Register.RDI)
          /* push %si          */ .push(Register.SI)
          /* push %r10         */ .push(Register.R10)
          /* pop  %r11         */ .pop(Register.R11, OptionalInt.empty())
          /* mov %r11, (%rdx)  */ .movMR(Register.R11, Register.RDX, OptionalInt.of(0))
          /* pop  %ax          */ .pop(Register.AX, OptionalInt.empty())
          /* mov  %ax, 8(%rdx) */ .movMR(Register.AX, Register.RDX, OptionalInt.of(8))
          /* pop  16(%rdx)     */ .pop(Register.RDX, OptionalInt.of(16))
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();

      //showDebugMessage(seg);
      try(var arena = Arena.ofConfined()){
        var mem = arena.allocate(24, 8);
        method.invoke(1, (short)2, mem);

        Assertions.assertEquals(1L, mem.get(ValueLayout.JAVA_LONG, 0));
        Assertions.assertEquals((short)2, mem.get(ValueLayout.JAVA_SHORT, 8));
        Assertions.assertEquals(1L, mem.get(ValueLayout.JAVA_LONG, 16));
      }
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests LEA
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testLEA(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /* push %rbp           */ .push(Register.RBP)
        /* mov %rsp,    %rbp   */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /* lea 8(arg1), retReg */ .lea(argReg.returnReg(), argReg.arg1(), 8)
        /* leave               */ .leave()
        /* ret                 */ .ret()
                                  .build();

      int actual = (int)method.invoke(100);
      Assertions.assertEquals(108, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests AND
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testAND(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
           /* push %rbp        */ .push(Register.RBP)
           /* mov %rsp, %rbp   */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
           /* mov arg1, retReg */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
           /* and arg2, retReg */ .andMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty())
           /* leave            */ .leave()
           /* ret              */ .ret()
                                  .build();

      int actual = (int)method.invoke(0b1001, 0b1100);
      Assertions.assertEquals(0b1000, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests OR
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testOR(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
           /* push %rbp        */ .push(Register.RBP)
           /* mov %rsp, %rbp   */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
           /* mov arg1, retReg */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
           /* or  arg2, retReg */ .orMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty())
           /* leave            */ .leave()
           /* ret              */ .ret()
                                  .build();

      int actual = (int)method.invoke(1, 2);
      Assertions.assertEquals(3, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests XOR
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testXOR(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /* push %rbp          */ .push(Register.RBP)
         /* mov %rsp, %rbp     */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
         /* mov arg1, retReg   */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
         /* xor retReg, retReg */ .xorMR(argReg.returnReg(), argReg.returnReg(), OptionalInt.empty())
         /* leave              */ .leave()
         /* ret                */ .ret()
                                  .build();

      int actual = (int)method.invoke(100);
      Assertions.assertEquals(0, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests CPUID and 32 bit movMR
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testCPUID(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
          /* mov arg1, %rax    */ .movMR(argReg.arg1(), Register.RAX, OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
          /* mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
        /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
        /*   jl success        */ .jl("success")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs(OS.LINUX)
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
        /*   xor %rax, %rax    */ .xorMR(Register.RAX, Register.RAX, OptionalInt.empty())
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

      //showDebugMessage(seg);
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
  @EnabledOnOs(OS.LINUX)
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
        /*   xor %rax, %rax    */ .xorMR(Register.RAX, Register.RAX, OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testADDs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov arg1, %rax */ .movMR(argReg.arg1(), Register.RAX, OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testSUBs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov arg1, %rax */ .movMR(argReg.arg1(), Register.RAX, OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testSHLs(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov arg1, %rax */ .movMR(argReg.arg1(), Register.RAX, OptionalInt.empty())
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
        /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
        /*   jae success       */ .jae("success")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
        /*   cmp   $10, arg1   */ .cmp(argReg.arg1(), 10, OptionalInt.empty())
        /*   je success        */ .je("success")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
   * Test JZ
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testJZ(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument (operand)
                   ValueLayout.JAVA_INT, // 2nd argument (success)
                   ValueLayout.JAVA_INT  // 3rd argument (failure)
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /*   push %rbp         */ .push(Register.RBP)
        /*   mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
        /*   and arg1, arg2    */ .andMR(argReg.arg1(), argReg.arg2(), OptionalInt.empty())
        /*   jz success        */ .jz("success")
        /*   mov arg3, retReg  */ .movMR(argReg.arg3(), argReg.returnReg(), OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
                                  .build();

      int actual = (int)method.invoke(0b1111, 0, 1);
      Assertions.assertEquals(0, actual, "Seems not to jump at JZ.");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test JNE
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
        /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
        /*   jne success       */ .jne("success")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* success:            */ .label("success")
        /*   mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
        /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
        /*   jl fwd            */ .jl("fwd")
        /* exit:               */ .label("exit")
        /*   mov arg1, retReg  */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
        /*   leave             */ .leave()
        /*   ret               */ .ret()
        /* fwd:                */ .label("fwd")
        /*   jmp exit          */ .jmp("exit")
        /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
         /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
         /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
         /*   jl success        */ .jl("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov arg1, retReg  */        .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
         /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
         /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
         /*   jae success       */ .jae("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov arg1, retReg  */        .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
         /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
         /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 1, OptionalInt.empty())
         /*   jne success       */ .jne("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov arg1, retReg  */        .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
         /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
         /*   cmp   $1, arg1    */ .cmp(argReg.arg1(), 10, OptionalInt.empty())
         /*   je success        */ .je("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov arg1, retReg  */        .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
         /*   mov arg2, retReg  */ .movMR(argReg.arg2(), argReg.returnReg(), OptionalInt.empty()) // failure
         /*   jmp success       */ .jmp("success");
      for(int i = 0; i < 200; i++){
         /* nop */ builder.nop();
      }
        /*   leave             */ builder.leave()
        /*   ret               */        .ret()
        /* success:            */        .label("success")
        /*   mov arg1, retReg  */        .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty()) // success
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
   * Test near JMP with absolute address
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testJMP(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var getpid = Linker.nativeLinker().defaultLookup().find("getpid").get();
      var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* mov %r10, addr */ .movImm(Register.R10, getpid.address())
             /* jmp %r10       */ .jmp(Register.R10)
                                  .build();

      //showDebugMessage(seg);

      long expect = ProcessHandle.current().pid();
      long actual = (int)method.invoke();
      Assertions.assertEquals(expect, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test near JMP with absolute address memory operand
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testJMPWithMemory(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var getpid = Linker.nativeLinker().defaultLookup().find("getpid").get();
      var ptrGetPID = arena.allocateFrom(ValueLayout.ADDRESS, getpid);
      var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                       ValueLayout.ADDRESS);
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
                 /* jmp (arg1) */ .jmp(argReg.arg1(), OptionalInt.of(0))
                                  .build();

      //showDebugMessage(seg);

      long expect = ProcessHandle.current().pid();
      long actual = (int)method.invoke(ptrGetPID);
      Assertions.assertEquals(expect, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test RDRAND
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
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
   * Test CALL
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testCALL(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var strlenAddr = Linker.nativeLinker().defaultLookup().find("strlen").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_LONG, // return value
                   ValueLayout.ADDRESS,   // address of strlen
                   ValueLayout.ADDRESS    // char *
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
             /* mov arg1, %r10 */ .movMR(argReg.arg1(), Register.R10, OptionalInt.empty())
             /* mov arg2, arg1 */ .movMR(argReg.arg2(), argReg.arg1(), OptionalInt.empty())
             /* sub $32, %rsp  */ .sub(Register.RSP, 32, OptionalInt.empty()) // Shadow stack (for Windows: 32 bytes)
             /* call %r10      */ .call(Register.R10)
             /* add $32, %rsp  */ .add(Register.RSP, 32, OptionalInt.empty()) // Recover shadow stack
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();

      final String test = "test";
      var cTest = arena.allocateFrom(test);
      long len = (long)method.invoke(strlenAddr, cTest);
      Assertions.assertEquals(test.length(), (int)len, "Invalid strlen() call");
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test address alignment
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testAlignment(){
    try(var seg = new CodeSegment()){
      var byteBuf = seg.getTailOfMemorySegment()
                       .asByteBuffer()
                       .order(ByteOrder.nativeOrder());
      var desc = FunctionDescriptor.ofVoid();
      AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
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

  /**
   * Test movRM
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVRM(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // 1st argument
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
         /* push %rbp          */ .push(Register.RBP)
         /* mov %rsp, %rbp     */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
         /* push arg1          */ .push(argReg.arg1())
         /* mov (%rsp), retReg */ .movRM(argReg.returnReg(), Register.RSP, OptionalInt.of(0))
         /* add $8, %rsp       */ .add(Register.RSP, 8, OptionalInt.empty())
         /* leave              */ .leave()
         /* ret                */ .ret()
                                  .build();
      //showDebugMessage(seg);

      int result = (int)method.invoke(100);
      Assertions.assertEquals(100, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test movImm
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testMOVImm(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
        /* push %rbp           */ .push(Register.RBP)
        /* mov %rsp, %rbp      */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
        /* xor  retReg, retReg */ .xorMR(argReg.returnReg(), argReg.returnReg(), OptionalInt.empty())
        /* mov  retReg, $100   */ .movImm(argReg.returnReg(), 100)
        /* leave               */ .leave()
        /* ret                 */ .ret()
                                  .build();
      //showDebugMessage(seg);

      int result = (int)method.invoke();
      Assertions.assertEquals(100, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test SYSCALL
   */
  @Test
  @EnabledOnOs(OS.LINUX)
  public void testSYSCALL(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT // return value
                 );
      // getpid in libc does not have prologue/epilogue
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
              /* mov %rax, $39 */ .movImm(Register.RAX, 39) // getpid
              /* syscall       */ .syscall()
              /* ret           */ .ret()
                                  .build();
      //showDebugMessage(seg);

      // __PID_T_TYPE is defined as __S32_TYPE in bits/typesizes.h
      int result = (int)method.invoke();
      int pidFromJava = (int)ProcessHandle.current().pid();
      Assertions.assertEquals(pidFromJava, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test BSWAP
   */
  @Test
  @EnabledOnOs(OS.LINUX)
  public void testBSWAP(){
    // test 64bit register
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_LONG, // return value
                   ValueLayout.JAVA_LONG  // arg1
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
           /* push %rbp        */ .push(Register.RBP)
           /* mov %rsp, %rbp   */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
           /* bswap arg1       */ .bswap(argReg.arg1())
           /* mov arg1, retReg */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
           /* leave            */ .leave()
           /* ret              */ .ret()
                                  .build();
      //showDebugMessage(seg);

      long param    = 0x1122334455667788L;
      long expected = 0x8877665544332211L;
      long result = (long)method.invoke(param);
      Assertions.assertEquals(expected, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }

    // test 64bit extended register
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_LONG, // return value
                   ValueLayout.JAVA_LONG  // arg1
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
            /* push %rbp       */ .push(Register.RBP)
            /* mov %rsp, %rbp  */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
            /* mov arg1, %r8   */ .movMR(argReg.arg1(), Register.R8, OptionalInt.empty())
            /* bswap %r8       */ .bswap(Register.R8)
            /* mov %r8, retReg */ .movMR(Register.R8, argReg.returnReg(), OptionalInt.empty())
            /* leave           */ .leave()
            /* ret             */ .ret()
                                  .build();
      //showDebugMessage(seg);

      long param    = 0x1122334455667788L;
      long expected = 0x8877665544332211L;
      long result = (long)method.invoke(param);
      Assertions.assertEquals(expected, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }

    // test 32bit register
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT  // arg1
                 );
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
           /* push %rbp        */ .push(Register.RBP)
           /* mov %rsp, %rbp   */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
           /* bswap arg1       */ .bswap(Register.EDI)
           /* mov arg1, retReg */ .movMR(argReg.arg1(), argReg.returnReg(), OptionalInt.empty())
           /* leave            */ .leave()
           /* ret              */ .ret()
                                  .build();
      //showDebugMessage(seg);

      int param    = 0x11223344;
      int expected = 0x44332211;
      int result = (int)method.invoke(param);
      Assertions.assertEquals(expected, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }

  }

  /**
   * Test SFENCE
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testSFENCE(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid();
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
             /* push %rbp      */ .push(Register.RBP)
             /* mov %rsp, %rbp */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
             /* sfence         */ .sfence()
             /* leave          */ .leave()
             /* ret            */ .ret()
                                  .build();
      //showDebugMessage(seg);
      method.invokeExact();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Test CLFLUSHOPT
   */
  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  public void testCLFLUSHOPT(){
    Assumptions.assumeTrue(supportCLFLUSHOPT(), "Test platform does not support CLFLUSHOPT");
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
      var method = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
          /* push %rbp         */ .push(Register.RBP)
          /* mov %rsp, %rbp    */ .movRM(Register.RBP, Register.RSP, OptionalInt.empty())
          /* mov arg1, %r11    */ .movRM(Register.R11, argReg.arg1(), OptionalInt.empty())
          /* clflushopt (arg1) */ .clflushopt(argReg.arg1(), 0)
          /* clflushopt (%r11) */ .clflushopt(Register.R11, 0)
          /* leave             */ .leave()
          /* ret               */ .ret()
                                  .build();
      //showDebugMessage(seg);
      method.invoke(seg.getAddr());
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
