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
package com.yasuenag.ffmasm.amd64;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;


/**
 * Builder for AMD64 hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class AMD64AsmBuilder{

  private final CodeSegment seg;

  private final MemorySegment mem;

  private final ByteBuffer byteBuf;

  private final FunctionDescriptor desc;

  private AMD64AsmBuilder(CodeSegment seg, FunctionDescriptor desc){
    this.seg = seg;
    this.mem = seg.getTailOfMemorySegment();
    this.byteBuf = mem.asByteBuffer().order(ByteOrder.nativeOrder());
    this.desc = desc;
  }

  /**
   * Create builder instance.
   *
   * @param seg code segment to use in this builder.
   * @return Builder instance
   */
  public static AMD64AsmBuilder create(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
    int bits = Integer.valueOf(System.getProperty("sun.arch.data.model"));
    if(bits != 64){
      throw new UnsupportedPlatformException("AMD64AsmBuilder supports 64 bit only.");
    }

    seg.alignTo16Bytes();
    return new AMD64AsmBuilder(seg, desc);
  }

  /**
   * Push r64.
   *   Opcode: 50+rd
   *   Instruction: PUSH r64
   *   Op/En: O
   *
   * @param reg Register to push to the stack.
   * @return This instance
   */
  public AMD64AsmBuilder push(Register reg){
    byteBuf.put((byte)(0x50 | reg.encoding()));
    return this;
  }

  private void emitREXOp(Register r, Register m){
    byte rexw = (r.width() == 64) ? (byte)0b1000 : (byte)0;
    byte rexr = (byte)(((r.encoding() >> 3) << 2) & 0b0100);
    byte rexb = (byte)((m.encoding() >> 3) & 0b0001);
    byte rex = (byte)(rexw | rexr | rexb);
    if(rex != 0){
      rex |= (byte)0b01000000;
      byteBuf.put(rex);
    }
  }

  /**
   * Move r to r/m.
   * If "r" is 64 bit register, Add REX.W to instruction, otherwise it will not happen.
   * If "disp" is not empty, r/m operand treats as memory.
   *   Opcode: REX.W + 89 /r (64 bit)
   *                   89 /r (32 bit)
   *   Instruction: MOV r/m,r
   *   Op/En: RM
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder movRM(Register r, Register m, OptionalInt disp){
    int mode;
    if(disp.isPresent()){
      int dispAsInt = disp.getAsInt();
      if(dispAsInt == 0){
        mode = 0b00;
      }
      else if(dispAsInt <= 0xff){
        mode = 0b01; // disp8
      }
      else{
        mode = 0b10; // disp32
      }
    }
    else{
      mode = 0b11; // reg-reg by default
    }

    emitREXOp(r, m);
    byteBuf.put((byte)0x89); // MOV
    byteBuf.put((byte)(                 mode << 6  |
                       ((r.encoding() & 0x7) << 3) |
                        (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return this;
  }

  /**
   * Returns processor identification and feature information to
   * the EAX, EBX, ECX, and EDX registers, as determined by
   * input entered in EAX (in some cases, ECX as well).
   *   Opcode: 0F A2
   *   Instruction: CPUID
   *   Op/En: ZO
   *
   * @return This instance
   */
  public AMD64AsmBuilder cpuid(){
    byteBuf.put((byte)(0x0f));
    byteBuf.put((byte)(0xa2));
    return this;
  }

  /**
   * Set RSP to RBP, then pop RBP.
   *   Opcode: C9
   *   Instruction: LEAVE
   *   Op/En: ZO
   *
   * @return This instance
   */
  public AMD64AsmBuilder leave(){
    byteBuf.put((byte)(0xc9));
    return this;
  }

  /**
   * Near return to calling procedure.
   *   Opcode: C3
   *   Instruction: RET
   *   Op/En: ZO
   *
   * @return This instance
   */
  public AMD64AsmBuilder ret(){
    byteBuf.put((byte)(0xc3));
    return this;
  }

  /**
   * Build as a MethodHandle
   *
   * @return MethodHandle for this assembly
   */
  public MethodHandle build(){
    seg.incTail(byteBuf.position());
    return Linker.nativeLinker().downcallHandle(mem, desc);
  }

}
