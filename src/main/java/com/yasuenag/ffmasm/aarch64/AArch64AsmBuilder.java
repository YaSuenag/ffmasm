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
package com.yasuenag.ffmasm.aarch64;

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

  private T ldrstrInternal(Register rt, Register rn, IndexClass idxCls, int opcAndImm){
    byte size = rt.width() == 64 ? (byte)0b11 : (byte)0b10;
    byte vr = switch(idxCls){
      case PostIndex, PreIndex -> (byte)0b000;
      case UnsignedOffset -> (byte)0b001;
      default -> throw new IllegalArgumentException("Unsupported index class");
    };

    int encoded = (size << 30) |
                  (0b111 << 27) |
                  (vr << 24) |
                  opcAndImm |
                  (rn.encoding() << 5) |
                  rt.encoding();

    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Load register (immediate)
   *
   * @param rt The general-purpose register to be transferred.
   * @param rn The general-purpose base register or stack pointer.
   * @param idxCls Addressing mode.
   * @param imm Memory offset of rn to be loaded.
   * @return This instance
   */
  public T ldr(Register rt, Register rn, IndexClass idxCls, int imm){
    int denominator = rt.width() == 64 ? 8 : 4;
    int opcAndImm = switch(idxCls){
      case PostIndex -> (0b010 << 21) | ((imm & 0x1ff) << 12) | (0b01 << 10);
      case PreIndex -> (0b010 << 21) | ((imm & 0x1ff) << 12) | (0b11 << 10);
      case UnsignedOffset -> (0b01 << 22) | (((imm / denominator) & 0xfff) << 10);
      default -> throw new IllegalArgumentException("Unsupported index class");
    };

    return ldrstrInternal(rt, rn, idxCls, opcAndImm);
  }

  /**
   * Store register (immediate)
   *
   * @param rt The general-purpose register to be transferred.
   * @param rn The general-purpose base register or stack pointer.
   * @param idxCls Addressing mode.
   * @param imm Memory offset of rn to be loaded.
   * @return This instance
   */
  public T str(Register rt, Register rn, IndexClass idxCls, int imm){
    int denominator = rt.width() == 64 ? 8 : 4;
    int opcAndImm = switch(idxCls){
      case PostIndex -> ((imm & 0x1ff) << 12) | (0b01 << 10);
      case PreIndex -> ((imm & 0x1ff) << 12) | (0b11 << 10);
      case UnsignedOffset -> (((imm / denominator) & 0xfff) << 10);
      default -> throw new IllegalArgumentException("Unsupported index class");
    };

    return ldrstrInternal(rt, rn, idxCls, opcAndImm);
  }

  private T ldpstpInternal(Register rt, Register rt2, Register rn, IndexClass idxCls, int imm7, boolean isLoad){
    byte opc = rt.width() == 64 ? (byte)0b10 : (byte)0b00;
    byte imm = rn.width() == 64 ? (byte)(imm7 / 8) : (byte)(imm7 / 4);
    byte vr = switch(idxCls){
      case PostIndex -> (byte)0b0001;
      case PreIndex -> (byte)0b0011;
      case SignedOffset -> (byte)0b0010;
      default -> throw new IllegalArgumentException("Unsupported index class");
    };

    int encoded = ((opc & 0b11) << 30) |
                  (0b101 << 27) |
                  (vr << 23) |
                  ((isLoad ? 1 : 0) << 22) |
                  ((imm & 0b1111111) << 15) |
                  (rt2.encoding() << 10) |
                  (rn.encoding() << 5) |
                  rt.encoding();

    byteBuf.putInt(encoded);
    return castToT();
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
    return ldpstpInternal(rt, rt2, rn, idxCls, imm7, true);
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
    return ldpstpInternal(rt, rt2, rn, idxCls, imm7, false);
  }

  /**
   * Move register value (includes SP)
   *
   * @param dst Destination register.
   * @param src Source register.
   * @return This instance
   */
  public T mov(Register dst, Register src){
    boolean is_SP = src == Register.SP || dst == Register.SP;
    byte sf = src.width() == 64 ? (byte)1 : (byte)0;

    int encoded;
    if(is_SP){
      encoded = (sf << 31) |
                (0b001000100000000000000 << 10) |
                (src.encoding() << 5) |
                dst.encoding();
    }
    else{
      encoded = (sf << 31) |
                (0b0101010000 << 21) |
                (src.encoding() << 16) |
                (0b00000011111 << 5) |
                dst.encoding();
    }


    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Move wide with zero
   *
   * @param dst Destination register.
   * @param imm The 16-bit unsigned immediate, in the range 0 to 65535
   * @param shift The amount by which to shift the immediate left
   * @return This instance
   */
  public T movz(Register dst, int imm, HWShift shift){
    byte sf = dst.width() == 64 ? (byte)1 : (byte)0;
    int encoded = (sf << 31) |
                  (0b10100101 << 23) |
                  (shift.ordinal() << 21) |
                  ((imm & 0xffff) << 5) |
                  dst.encoding();
    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Move wide with keep
   *
   * @param dst Destination register.
   * @param imm The 16-bit unsigned immediate, in the range 0 to 65535
   * @param shift The amount by which to shift the immediate left
   * @return This instance
   */
  public T movk(Register dst, int imm, HWShift shift){
    byte sf = dst.width() == 64 ? (byte)1 : (byte)0;
    int encoded = (sf << 31) |
                  (0b11100101 << 23) |
                  (shift.ordinal() << 21) |
                  ((imm & 0xffff) << 5) |
                  dst.encoding();
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

  /**
   * Branch to register
   *
   * @param rn The general-purpose register holding the address to be branched to.
   * @return This instance
   */
  public T br(Register rn){
    int encoded = (0b1101011000011111000000 << 10) | (rn.encoding() << 5);

    byteBuf.putInt(encoded);
    return castToT();
  }

  /**
   * Branch with link to register
   *
   * @param rn The general-purpose register holding the address to be branched to.
   * @return This instance
   */
  public T blr(Register rn){
    int encoded = (0b1101011000111111000000 << 10) | (rn.encoding() << 5);

    byteBuf.putInt(encoded);
    return castToT();
  }

}
