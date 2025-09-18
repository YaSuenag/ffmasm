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
package com.yasuenag.ffmasm.internal.aarch64;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import com.yasuenag.ffmasm.AsmBuilder;
import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.JitDump;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.aarch64.IndexClass;
import com.yasuenag.ffmasm.aarch64.Register;


/**
 * Builder for AArch64 hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class AArch64AsmBuilder<T extends AArch64AsmBuilder<T>> extends AsmBuilder<T>{

  /**
   * Constructor.
   *
   * @param seg CodeSegment which is used by this builder.
   * @param desc FunctionDescriptor for this builder. It will be used by build().
   */
  protected AArch64AsmBuilder(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
    super(seg, desc);

    if(!System.getProperty("os.arch").equals("aarch64")){
      throw new UnsupportedPlatformException("Platform is not AArch64.");
    }

    int bits = Integer.valueOf(System.getProperty("sun.arch.data.model"));
    if(bits != 64){
      throw new UnsupportedPlatformException("AArch64AsmBuilder supports 64 bit only.");
    }
  }

  /**
   * Load pair of registers
   *
   * @param rt The first general-purpose register to be transferred.
   * @param rt2 The second general-purpose register to be transferred.
   * @param rn The general-purpose base register or stack pointer.
   * @param idxCls Addressing mode.
   * @param imm7 Memory offset of rn to be loaded.
   * @return This instance
   */
  public T ldp(Register rt, Register rt2, Register rn, IndexClass idxCls, int imm7){
    byte opc = rt.width() == 64 ? (byte)0b10 : (byte)0b00;
    byte imm = rn.width() == 64 ? (byte)(imm7 / 8) : (byte)(imm7 / 4);

    int encoded = ((opc & 0b11) << 30) |
                  (0b101 << 27) |
                  (idxCls.vr() << 23) |
                  (1 << 22) |
                  ((imm & 0b1111111) << 15) |
                  (rt2.encoding() << 10) |
                  (rn.encoding() << 5) |
                  rt.encoding();

    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Store pair of registers
   *
   * @param rt The first general-purpose register to be transferred.
   * @param rt2 The second general-purpose register to be transferred.
   * @param rn The general-purpose base register or stack pointer.
   * @param idxCls Addressing mode.
   * @param imm7 Memory offset of rn to be stored.
   * @return This instance
   */
  public T stp(Register rt, Register rt2, Register rn, IndexClass idxCls, int imm7){
    byte opc = rt.width() == 64 ? (byte)0b10 : (byte)0b00;
    byte imm = rn.width() == 64 ? (byte)(imm7 / 8) : (byte)(imm7 / 4);

    int encoded = ((opc & 0b11) << 30) |
                  (0b101 << 27) |
                  (idxCls.vr() << 23) |
                  (0 << 22) |
                  ((imm & 0b1111111) << 15) |
                  (rt2.encoding() << 10) |
                  (rn.encoding() << 5) |
                  rt.encoding();

    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Move register value (includes SP)
   *
   * @param src Source register.
   * @param dst Destination register.
   * @return This instance
   */
  public T mov(Register src, Register dst){
    boolean is_SP = src == Register.SP || dst == Register.SP;
    byte sf = src.width() == 64 ? (byte)1 : (byte)0;

    int encoded;
    if(is_SP){
      encoded = (sf << 31) |
                (0b001000100000000000000 << 10) |
                (dst.encoding() << 5) |
                src.encoding();
    }
    else{
      encoded = (sf << 31) |
                (0b0101010000 << 21) |
                (dst.encoding() << 16) |
                (0b00000011111 << 5) |
                src.encoding();
    }


    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Return from subroutine
   *
   * @param rn The general-purpose register holding the address to be branched to. X30 will be set if this argument is empty.
   * @return This instance
   */
  public T ret(Optional<Register> rn){
    int encoded = (0b1101011001011111000000 << 10) |
                  (rn.orElse(Register.X30).encoding() << 5);
    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Add immediate value
   *
   * @param src Source register.
   * @param dst Destination register.
   * @param imm Immediate value to subtract. In the range 0 to 4095.
   * @param shift true if imm should be LSL #12
   * @return This instance
   */
  public T addImm(Register src, Register dst, int imm, boolean shift){
    byte sf = src.width() == 64 ? (byte)1 : (byte)0;
    byte sh = shift ? (byte)1 : (byte)0;
    int encoded = (sf << 31) |
                  (0b000100010 << 23) |
                  (sh << 22) |
                  ((imm & 0xfff) << 10) | // 12bit
                  (src.encoding() << 5) |
                  dst.encoding();

    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Subtract immediate value
   *
   * @param src Source register.
   * @param dst Destination register.
   * @param imm Immediate value to subtract. In the range 0 to 4095.
   * @param shift true if imm should be LSL #12
   * @return This instance
   */
  public T subImm(Register src, Register dst, int imm, boolean shift){
    byte sf = src.width() == 64 ? (byte)1 : (byte)0;
    byte sh = shift ? (byte)1 : (byte)0;
    int encoded = (sf << 31) |
                  (0b010100010 << 23) |
                  (sh << 22) |
                  ((imm & 0xfff) << 10) | // 12bit
                  (src.encoding() << 5) |
                  dst.encoding();

    byteBuf.putInt(encoded);
    return castToT();
  }

}
