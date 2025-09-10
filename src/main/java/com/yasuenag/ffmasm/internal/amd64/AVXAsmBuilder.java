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
package com.yasuenag.ffmasm.internal.amd64;

import java.lang.foreign.FunctionDescriptor;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.Register;


/**
 * Builder for AVX hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class AVXAsmBuilder<T extends AVXAsmBuilder<T>> extends SSEAsmBuilder<T>{

  /**
   * Constructor.
   *
   * @param seg CodeSegment which is used by this builder.
   * @param desc FunctionDescriptor for this builder. It will be used by build().
   */
  protected AVXAsmBuilder(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
    super(seg, desc);
  }

  private static enum PP{
    None((byte)0b00),
    H66((byte)0b01),
    HF3((byte)0b10),
    HF2((byte)0b11);

    private final byte prefix;

    private PP(byte prefix){
      this.prefix = prefix;
    }

    public byte prefix(){
      return prefix;
    }
  }

  private static enum LeadingBytes{
    H0F((byte)0b00001),
    H0F38((byte)0b00010),
    H0F3A((byte)0b00011);

    private final byte bytes;

    private LeadingBytes(byte bytes){
      this.bytes = bytes;
    }

    public byte bytes(){
      return bytes;
    }
  }

  private void emit2ByteVEXPrefix(Register src1, PP simdPrefix){
    byte VEXvvvv = (byte)((~src1.encoding()) & 0b1111);
    emit2ByteVEXPrefixWithVVVV(VEXvvvv, src1.width() == 256, simdPrefix);
  }

  private void emit2ByteVEXPrefixWithVVVV(byte VEXvvvv, boolean is256bit, PP simdPrefix){
    byte rexr = (byte)((VEXvvvv >> 3) & 1);
    byte vecLength = is256bit ? (byte)1 : (byte)0;
    byteBuf.put((byte)0xC5); // 2-byte VEX
    byteBuf.put((byte)(       (rexr << 7) | // REX.R
                           (VEXvvvv << 3) | // VEX.vvvv
                         (vecLength << 2) | // Vector Length
                      simdPrefix.prefix()   // opcode extension (SIMD prefix)
               ));
  }

  private void emit3ByteVEXPrefix(Register r, Register m, PP simdPrefix, LeadingBytes bytes){
    byte VEXvvvv = (byte)((~r.encoding()) & 0b1111);
    byte invMem = (byte)((~m.encoding()) & 0b1111);
    byte rexr = (byte)((VEXvvvv >> 3) & 1);
    byte rexb = (byte)((invMem >> 3) & 1);
    byte is256Bit = (r.width() == 256) ? (byte)1 : (byte)0;
    byteBuf.put((byte)0xC4); // 3-byte VEX
    byteBuf.put((byte)(   (rexr << 7) | // REX.R
                          (   1 << 6) | // inverse of REX.X
                          (rexb << 5) | // REX.B
                        bytes.bytes()   // leading opcode bytes
               ));
    byteBuf.put((byte)(     (VEXvvvv << 3) | // VEX.vvvv
                           (is256Bit << 2) | // Vector Length
                       simdPrefix.prefix()   // opcode extension (SIMD prefix)
               ));
  }

  private T vmovdq(Register r, Register m, OptionalInt disp, PP pp, byte opcode){
    if(m.encoding() > 7){
      emit3ByteVEXPrefix(Register.YMM0 /* unused */, m, pp, LeadingBytes.H0F);
    }
    else{
      emit2ByteVEXPrefix(Register.YMM0 /* unused */, pp);
    }

    byteBuf.put(opcode); // MOVDQA

    byte mode = emitModRM(r, m, disp);
    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return castToT();
  }

  /**
   * Move aligned packed integer values from r/m to r.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.66.0F.WIG 6F /r (256 bit)
   *   Instruction: VMOVDQA r, r/m
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand..
   * @return This instance
   */
  public T vmovdqaRM(Register r, Register m, OptionalInt disp){
    return vmovdq(r, m, disp, PP.H66, (byte)0x6f);
  }

  /**
   * Move aligned packed integer values from r to r/m.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.66.0F.WIG 7F /r (256 bit)
   *   Instruction: VMOVDQA r/m, r
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand..
   * @return This instance
   */
  public T vmovdqaMR(Register r, Register m, OptionalInt disp){
    return vmovdq(r, m, disp, PP.H66, (byte)0x7f);
  }

  /**
   * Move unaligned packed integer values from r/m to r.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.F3.0F.WIG 6F /r (256 bit)
   *   Instruction: VMOVDQU r, r/m
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand..
   * @return This instance
   */
  public T vmovdquRM(Register r, Register m, OptionalInt disp){
    return vmovdq(r, m, disp, PP.HF3, (byte)0x6f);
  }

  /**
   * Move unaligned packed integer values from r to r/m.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.F3.0F.WIG 7F /r (256 bit)
   *   Instruction: VMOVDQU r/m, r
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand..
   * @return This instance
   */
  public T vmovdquMR(Register r, Register m, OptionalInt disp){
    return vmovdq(r, m, disp, PP.HF3, (byte)0x7f);
  }

  /**
   * Bitwise XOR of r and r/m.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.66.0F.WIG EF /r (256 bit)
   *   Instruction: VPXOR dest, r, r/m
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param dest "dest" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand.
   * @return This instance
   */
  public T vpxor(Register r, Register m, Register dest, OptionalInt disp){
    if(m.encoding() > 7){
      emit3ByteVEXPrefix(r, m, PP.H66, LeadingBytes.H0F);
    }
    else{
      emit2ByteVEXPrefix(r, PP.H66);
    }

    byteBuf.put((byte)0xef); // VPXOR

    byte mode = emitModRM(dest, m, disp);
    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return castToT();
  }

  /**
   * Add packed doubleword integers from r/m, r and store in dest.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.66.0F.WIG FE /r (256 bit)
   *   Instruction: VPADDD dest, r, r/m
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param dest "dest" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand.
   * @return This instance
   */
  public T vpaddd(Register r, Register m, Register dest, OptionalInt disp){
    if(m.encoding() > 7){
      emit3ByteVEXPrefix(r, m, PP.H66, LeadingBytes.H0F);
    }
    else{
      emit2ByteVEXPrefix(r, PP.H66);
    }

    byteBuf.put((byte)0xfe); // VPADDD

    byte mode = emitModRM(dest, m, disp);
    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return castToT();
  }

  /**
   * Logical compare r with r/m.
   * NOTES: This method supports YMM register only now.
   *   Opcode: VEX.256.66.0F38.WIG 17 /r (256 bit)
   *   Instruction: VPTEST r, r/m
   *   Op/En: RM
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be
   *             a memory operand.
   * @return This instance
   */
  public T vptest(Register r, Register m, OptionalInt disp){
    emit3ByteVEXPrefix(Register.YMM0 /* unused */, m, PP.H66, LeadingBytes.H0F38);
    byteBuf.put((byte)0x17); // PTEST
    byte mode = emitModRM(r, m, disp);
    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return castToT();
  }

  /**
   * Zero bits in positions 128 and higher of some YMM and ZMM registers.
   *   Opcode: VEX.128.0F.WIG 77
   *   Instruction: VZEROUPPER
   *   Op/En: ZO
   *
   * @return This instance
   */
  public T vzeroupper(){
    emit2ByteVEXPrefixWithVVVV((byte)0b1111, false, PP.None);
    byteBuf.put((byte)0x77); // VZEROUPPER
    return castToT();
  }

}
