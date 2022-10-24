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
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;


/**
 * Builder for AVX hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class AVXAsmBuilder extends SSEAsmBuilder{

  /**
   * Constructor.
   *
   * @param seg CodeSegment which is used by this builder.
   * @param desc FunctionDescriptor for this builder. It will be used by build().
   */
  protected AVXAsmBuilder(CodeSegment seg, FunctionDescriptor desc){
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

  private void emit2ByteVEXPrefix(Register src1, PP simdPrefix){
    byte VEXvvvv = (byte)((~src1.encoding()) & 0b1111);
    byte rexr = (byte)((VEXvvvv >> 3) & 1);
    byte is256Bit = (src1.width() == 256) ? (byte)1 : (byte)0;
    byteBuf.put((byte)0xC5); // 2-byte VEX
    byteBuf.put((byte)(       (rexr << 7) | // REX.R
                           (VEXvvvv << 3) | // VEX.vvvv
                          (is256Bit << 2) | // Vector Length
                      simdPrefix.prefix()   // opcode extension (SIMD prefix)
               ));
  }

  private AVXAsmBuilder vmovdqa(Register r, Register m, OptionalInt disp, byte opcode){
    byte mode = calcModRMMode(disp);

    emit2ByteVEXPrefix(Register.YMM0 /* unused */, PP.H66);
    byteBuf.put(opcode); // MOVDQA
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
  public AVXAsmBuilder vmovdqaMR(Register r, Register m, OptionalInt disp){
    return vmovdqa(r, m, disp, (byte)0x6f);
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
  public AVXAsmBuilder vmovdqaRM(Register r, Register m, OptionalInt disp){
    return vmovdqa(r, m, disp, (byte)0x7f);
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
  public AVXAsmBuilder vpaddd(Register r, Register m, Register dest, OptionalInt disp){
    byte mode = calcModRMMode(disp);

    emit2ByteVEXPrefix(r, PP.H66);
    byteBuf.put((byte)0xfe); // VPADDD
    byteBuf.put((byte)(                    mode << 6  |
                       ((dest.encoding() & 0x7) << 3) |
                           (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return this;
  }

}
