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
package com.yasuenag.ffmasm.amd64;

import java.lang.foreign.FunctionDescriptor;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.Register;


/**
 * Builder for SSE hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class SSEAsmBuilder<T extends SSEAsmBuilder<T>> extends AMD64AsmBuilder<T>{

  /**
   * Constructor.
   *
   * @param seg CodeSegment which is used by this builder.
   * @param desc FunctionDescriptor for this builder. It will be used by build().
   */
  protected SSEAsmBuilder(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
    super(seg, desc);
  }

  private T movdq(Register r, Register m, OptionalInt disp, byte prefix, byte secondOpcode){
    byteBuf.put(prefix);
    emitREXOp(r, m);
    byteBuf.put((byte)0x0f); // escape opcode
    byteBuf.put(secondOpcode);
    var mode = emitModRM(r, m, disp);
    emitDisp(mode, disp, m);

    return castToT();
  }

  /**
   * Move aligned packed integer values from xmm2/mem to xmm1.
   *   Opcode: 66 0F 6F /r
   *   Instruction: MOVDQA xmm1, xmm2/m128
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be   *             a memory operand.
   * @return This instance
   */
  public T movdqaRM(Register r, Register m, OptionalInt disp){
    return movdq(r, m, disp, (byte)0x66, (byte)0x6f);
  }

  /**
   * Move aligned packed integer values from xmm1 to xmm2/mem.
   *   Opcode: 66 0F 7F /r
   *   Instruction: MOVDQA xmm2/m128, xmm1
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be   *             a memory operand.
   * @return This instance
   */
  public T movdqaMR(Register r, Register m, OptionalInt disp){
    return movdq(r, m, disp, (byte)0x66, (byte)0x7f);
  }

  /**
   * Move unaligned packed integer values from xmm2/mem128 to xmm1.
   *   Opcode: F3 0F 6F /r
   *   Instruction: MOVDQU xmm1, xmm2/m128
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be   *             a memory operand.
   * @return This instance
   */
  public T movdquRM(Register r, Register m, OptionalInt disp){
    return movdq(r, m, disp, (byte)0xf3, (byte)0x6f);
  }

  /**
   * Move unaligned packed integer values from xmm1 to xmm2/mem128.
   *   Opcode: F3 0F 7F /r
   *   Instruction: MOVDQU xmm2/m128, xmm1
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg
   *             then "r/m" have to be a SIMD register.
   *             Otherwise it has to be 64 bit GPR because it have to be   *             a memory operand.
   * @return This instance
   */
  public T movdquMR(Register r, Register m, OptionalInt disp){
    return movdq(r, m, disp, (byte)0xf3, (byte)0x7f);
  }

  private T movDInternal(Register r, Register m, OptionalInt disp, byte secondOpcode){
    return movDorQ(r, m, disp, secondOpcode, false);
  }

  private T movQInternal(Register r, Register m, OptionalInt disp, byte secondOpcode){
    return movDorQ(r, m, disp, secondOpcode, true);
  }

  private T movDorQ(Register r, Register m, OptionalInt disp, byte secondOpcode, boolean isQWORD){
    byteBuf.put((byte)0x66); // prefix
    emitREXOp(r, m, isQWORD);
    byteBuf.put((byte)0x0f); // escape opcode
    byteBuf.put(secondOpcode);
    var mode = emitModRM(r, m, disp);
    emitDisp(mode, disp, m);

    return castToT();
  }

  /**
   * Move doubleword from r/m32 to xmm.
   *   Opcode: 66 0F 6E /r
   *   Instruction: MOVD xmm, r/m32
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public T movdRM(Register r, Register m, OptionalInt disp){
    return movDInternal(r, m, disp, (byte)0x6e);
  }

  /**
   * Move doubleword from xmm register to r/m32.
   *   Opcode: 66 0F 7E /r
   *   Instruction: MOVD r/m32, xmm
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public T movdMR(Register r, Register m, OptionalInt disp){
    return movDInternal(r, m, disp, (byte)0x7e);
  }

  /**
   * Move quadword from r/m64 to xmm.
   *   Opcode: 66 REX.W 0F 6E /r
   *   Instruction: MOVQ xmm, r/m64
   *   Op/En: A
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public T movqRM(Register r, Register m, OptionalInt disp){
    return movQInternal(r, m, disp, (byte)0x6e);
  }

  /**
   * Move quadword from xmm register to r/m64.
   *   Opcode: 66 REX.W 0F 7E /r
   *   Instruction: MOVQ r/m64, xmm
   *   Op/En: B
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public T movqMR(Register r, Register m, OptionalInt disp){
    return movQInternal(r, m, disp, (byte)0x7e);
  }

}
