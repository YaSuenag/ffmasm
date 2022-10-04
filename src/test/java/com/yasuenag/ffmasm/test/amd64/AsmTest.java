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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


@Tag("amd64")
public class AsmTest{

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

}
