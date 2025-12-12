/*
 * Copyright (C) 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.test.aarch64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.aarch64.DMBOptions;
import com.yasuenag.ffmasm.aarch64.HWShift;
import com.yasuenag.ffmasm.aarch64.IndexClass;
import com.yasuenag.ffmasm.aarch64.Register;
import com.yasuenag.ffmasm.aarch64.ShiftType;


@EnabledOnOs(architectures = {"aarch64"})
public class AsmTest{

  /**
   * Show PID, address of CodeSegment, then waits stdin input.
   */
  public void showDebugMessage(CodeSegment seg) throws IOException{
    System.out.println("PID: " + ProcessHandle.current().pid());
    System.out.println("Addr: 0x" + Long.toHexString(seg.getAddr().address()));
    System.in.read();
  }

  /**
   * Tests prologue (stp, mov), epilogue (ldp, ret)
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testBasicInstructions(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
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
   * Tests 32bit MOV and Zero Register
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void test32BitMOVandZeroReg(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* mov w0,   wzr             */ .mov(Register.W0, Register.WZR)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      final int expected = 0;
      int actual = (int)method.invoke(100);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests LDR
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testLdr(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* stp  x0,  x1, [sp, #-16]! */ .stp(Register.X0, Register.X1, Register.SP, IndexClass.PreIndex, -16)
 /* ldr  x0, [sp, #8]         */ .ldr(Register.X0, Register.SP, IndexClass.UnsignedOffset, 8)
 /* mov  sp, x29              */ .mov(Register.SP, Register.X29)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      final int expected = 200;
      int actual = (int)method.invoke(100, expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests STR
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testStr(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* stp  x0,  x0, [sp, #-16]! */ .stp(Register.X0, Register.X0, Register.SP, IndexClass.PreIndex, -16)
 /* str  x1, [sp]             */ .str(Register.X1, Register.SP, IndexClass.UnsignedOffset, 0)
 /* ldr  x0, [sp]             */ .ldr(Register.X0, Register.SP, IndexClass.UnsignedOffset, 0)
 /* mov  sp, x29              */ .mov(Register.SP, Register.X29)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      final int expected = 200;
      int actual = (int)method.invoke(100, expected);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests to set 64bit immediate value through MOVZ and MOVK
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testMOVZandMOVK(){
    final long expected = 0x123456789abcdef0L;
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_LONG // return value
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
/* stp  x29, x30, [sp, #-16]!          */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
/* mov  x29, sp                        */ .mov(Register.X29, Register.SP)
/* movz  x0, #expected[0:15]           */ .movz(Register.X0, (int)(expected & 0xffff), HWShift.None)
/* movk  x0, #expected[16:31], lsl #16 */ .movk(Register.X0, (int)((expected >> 16) & 0xffff), HWShift.HW_16)
/* movk  x0, #expected[32:47], lsl #32 */ .movk(Register.X0, (int)((expected >> 32) & 0xffff), HWShift.HW_32)
/* movk  x0, #expected[48:63], lsl #48 */ .movk(Register.X0, (int)((expected >> 48) & 0xffff), HWShift.HW_48)
/* ldp  x29, x30, [sp], #16            */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
/* ret                                 */ .ret(Optional.empty())
                                          .build();

      //showDebugMessage(seg);

      long actual = (long)method.invoke();
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests addImm
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testAddImm(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );

      // 1. No shift
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* add  x0,  x0, #10         */ .addImm(Register.X0, Register.X0, 10, false)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();
      int expected = 110;
      int actual = (int)method.invoke(100);
      Assertions.assertEquals(expected, actual);

      // 2. Shift
      method = new AsmBuilder.AArch64(seg, desc)
/* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
/* mov x29,  sp              */ .mov(Register.X29, Register.SP)
/* add  x0,  x0, #1, lsl #12 */ .addImm(Register.X0, Register.X0, 1, true)
/* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
/* ret                       */ .ret(Optional.empty())
                                .build();
      expected = 4097;
      actual = (int)method.invoke(1);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests subImm
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testSubImm(){
    try(var seg = new CodeSegment()){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT // 1st argument
                 );

      // 1. No shift
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* sub  x0,  x0, #10         */ .subImm(Register.X0, Register.X0, 10, false)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();
      int expected = 90;
      int actual = (int)method.invoke(100);
      Assertions.assertEquals(expected, actual);

      // 2. Shift
      method = new AsmBuilder.AArch64(seg, desc)
/* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
/* mov x29,  sp              */ .mov(Register.X29, Register.SP)
/* sub  x0,  x0, #1, lsl #12 */ .subImm(Register.X0, Register.X0, 1, true)
/* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
/* ret                       */ .ret(Optional.empty())
                                .build();
      expected = 1;
      actual = (int)method.invoke(4097);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests BR
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testBr(){
    try(var seg = new CodeSegment();){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_SHORT, // return value
                   ValueLayout.ADDRESS     // address of subroutine
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
                     /* br x0 */ .br(Register.X0)
                                 .build();

      final short expected = 100;
      var memSub = new AsmBuilder.AArch64(seg, desc)
/* stp  x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
/* mov  x29,  sp              */ .mov(Register.X29, Register.SP)
/* movz  x0, #expected        */ .movz(Register.X0, expected & 0xffff, HWShift.None)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .getMemorySegment();

      //showDebugMessage(seg);

      short actual = (short)method.invoke(memSub);
      Assertions.assertEquals(actual, expected);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests BLR
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testBlr(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var strlenAddr = Linker.nativeLinker().defaultLookup().find("strlen").get();
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_LONG, // return value
                   ValueLayout.ADDRESS,   // address of strlen
                   ValueLayout.ADDRESS    // char *
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* mov  x9,  x0              */ .mov(Register.X9, Register.X0)
 /* mov  x0,  x1              */ .mov(Register.X0, Register.X1)
 /* blr  x9                   */ .blr(Register.X9)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

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
   * Tests CMP and BEQ
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testCMPandBEQ(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var desc = FunctionDescriptor.of(
                   ValueLayout.JAVA_INT, // return value
                   ValueLayout.JAVA_INT, // 1st argument
                   ValueLayout.JAVA_INT  // 2nd argument
                 );
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* cmp w0, w1                */ .cmp(Register.W0, Register.W1, ShiftType.LSL, (byte)0)
 /* b.eq EQUALS               */ .beq("EQUALS")
 /* movz w0, $1               */ .movz(Register.W0, 1, HWShift.None)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
 /* EQUALS:                   */ .label("EQUALS")
 /* mov w0, wzr               */ .mov(Register.W0, Register.WZR)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      int expected = 0;
      int actual = (int)method.invoke(1, 1);
      Assertions.assertEquals(expected, actual);

      expected = 1;
      actual = (int)method.invoke(1, 10);
      Assertions.assertEquals(expected, actual);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests NOP
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testNOP(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var desc = FunctionDescriptor.ofVoid();
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* nop                       */ .nop()
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests PACIAZ and RETAA
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  @DisabledIfSystemProperty(named = "skipPACtest", matches = "true")
  public void testPACIAZAndRETAA(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var desc = FunctionDescriptor.ofVoid();
      var method = new AsmBuilder.AArch64(seg, desc)
 /* paciaz                    */ .paciaz()
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .retaa()
                                 .build();

      //showDebugMessage(seg);
      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests DMB
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testDMB(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var desc = FunctionDescriptor.ofVoid();
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* dmb ishld                 */ .dmb(DMBOptions.ISHLD)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      method.invoke();
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

  /**
   * Tests SVC
   */
  @Test
  @EnabledOnOs({OS.LINUX})
  public void testSVC(){
    try(var arena = Arena.ofConfined();
        var seg = new CodeSegment();){
      var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
      var method = new AsmBuilder.AArch64(seg, desc)
 /* stp x29, x30, [sp, #-16]! */ .stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
 /* mov x29,  sp              */ .mov(Register.X29, Register.SP)
 /* movz x8, $172             */ .movz(Register.X8, 172, HWShift.None) // getpid
 /* svc #0                    */ .svc(0)
 /* ldp x29, x30, [sp], #16   */ .ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16)
 /* ret                       */ .ret(Optional.empty())
                                 .build();

      //showDebugMessage(seg);

      int result = (int)method.invoke();
      int pidFromJava = (int)ProcessHandle.current().pid();
      Assertions.assertEquals(pidFromJava, result);
    }
    catch(Throwable t){
      Assertions.fail(t);
    }
  }

}
