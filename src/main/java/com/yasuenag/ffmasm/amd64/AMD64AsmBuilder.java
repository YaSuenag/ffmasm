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
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

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

  // Key: label, Value: position
  private final Map<String, Integer> labelMap;

  // Key: label, Value: jump data
  private static record PendingJump(Consumer<Integer> emitOp, int position){}
  private final Map<String, Set<PendingJump>> pendingLabelMap;

  private AMD64AsmBuilder(CodeSegment seg, FunctionDescriptor desc){
    this.seg = seg;
    this.mem = seg.getTailOfMemorySegment();
    this.byteBuf = mem.asByteBuffer().order(ByteOrder.nativeOrder());
    this.desc = desc;
    this.labelMap = new HashMap<>();
    this.pendingLabelMap = new HashMap<>();
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
   * Push.
   *   Opcode: 50+rd (64 bit)
   *           50+rw (16 bit)
   *   Instruction: PUSH
   *   Op/En: O
   *
   * @param reg Register to push to the stack.
   * @return This instance
   */
  public AMD64AsmBuilder push(Register reg){
    if(reg.width() == 16){
      // Ops for 16 bits operands (66H)
      byteBuf.put((byte)0x66);
    }
    byteBuf.put((byte)(0x50 | reg.encoding()));
    return this;
  }

  /**
   * Pop top of stack into r/m; increment stack pointer.
   *   Opcode: 66H + 8F /0 (64 bit)
   *           8F /0 (16 bit)
   *   Instruction: POP r/m
   *   Op/En: M
   *
   * @param reg Register to push to the stack.
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder pop(Register reg, OptionalInt disp){
    byte mode = calcModRMMode(disp);

    if(reg.width() == 16){
      // Ops for 16 bits operands (66H)
      byteBuf.put((byte)0x66);
    }
    byteBuf.put((byte)0x8f); // POP
    byteBuf.put((byte)(             mode << 6 |
                                            0 | // digit (/0)
                        (reg.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    return this;
  }

  private void emitREXOp(Register r, Register m){
    if(r.width() == 16){
      // Ops for 16 bits operands (66H)
      byteBuf.put((byte)0x66);
    }
    else{
      byte rexw = (r.width() == 64) ? (byte)0b1000 : (byte)0;
      byte rexr = (byte)(((r.encoding() >> 3) << 2) & 0b0100);
      byte rexb = (byte)((m.encoding() >> 3) & 0b0001);
      byte rex = (byte)(rexw | rexr | rexb);
      if(rex != 0){
        rex |= (byte)0b01000000;
        byteBuf.put(rex);
      }
    }
  }

  private byte calcModRMMode(OptionalInt disp){
    byte mode = (byte)0b11; // reg-reg by default
    if(disp.isPresent()){
      int dispAsInt = disp.getAsInt();
      if(dispAsInt == 0){
        mode = (byte)0b00;
      }
      else if(dispAsInt <= 0xff){
        mode = (byte)0b01; // disp8
      }
      else{
        mode = (byte)0b10; // disp32
      }
    }
    return mode;
  }

  /**
   * Move r to r/m.
   * If "r" is 64 bit register, Add REX.W to instruction, otherwise it will not happen.
   * If "r" is 16 bit register, Add 66H to instruction, otherwise it will not happen.
   * If "disp" is not empty, r/m operand treats as memory.
   *   Opcode: REX.W + 89 /r (64 bit)
   *                   89 /r (32 bit)
   *              66 + 89 /r (16 bit)
   *                   88 /r ( 8 bit)
   *   Instruction: MOV r/m,r
   *   Op/En: RM
   *
   * @param r "r" register
   * @param m "r/m" register
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder movRM(Register r, Register m, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    emitREXOp(r, m);
    byte opcode = (r.width() == 8) ? (byte)0x88 : (byte)0x89;
    byteBuf.put(opcode); // MOV
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
   * Compare imm with r/m.
   * imm32 is treated as sign-extended if REX.W operation.
   *   Opcode: REX.W + 81 /7 id (64 bit)
   *                   81 /7 id (32 bit)
   *             66H + 81 /7 iw (16 bit)
   *                   80 /7 ib ( 8 bit)
   *   Instruction: CMP r/m, imm32 (64 bit, 32bit)
   *                CMP r/m, imm16 (16 bit)
   *                CMP r/m, imm8  ( 8 bit)
   *   Op/En: MI
   *
   * @param m "r/m" register
   * @param imm Immediate value to compare.
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder cmp(Register m, int imm, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byte opcode = (m.width() == 8) ? (byte)0x80 : (byte)0x81;
    byteBuf.put(opcode); // CMP
    byteBuf.put((byte)(             mode << 6 |
                                       7 << 3 | // digit (/7)
                       (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    if(m.width() == 8){
      byteBuf.put((byte)imm); // imm8
    }
    else if(m.width() == 16){
      byteBuf.putShort((short)imm); // imm16
    }
    else{
      byteBuf.putInt(imm); // imm32
    }

    return this;
  }

  /**
   * Add imm to r/m.
   * imm32 is treated as sign-extended if REX.W operation.
   *   Opcode: REX.W + 81 /0 id (64 bit)
   *                   81 /0 id (32 bit)
   *             66H + 81 /0 iw (16 bit)
   *                   80 /0 ib ( 8 bit)
   *   Instruction: ADD r/m, imm32 (64 bit, 32bit)
   *                ADD r/m, imm16 (16 bit)
   *                ADD r/m, imm8  ( 8 bit)
   *   Op/En: MI
   *
   * @param m "r/m" register
   * @param imm Immediate value to add
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder add(Register m, int imm, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byte opcode = (m.width() == 8) ? (byte)0x80 : (byte)0x81;
    byteBuf.put(opcode); // ADD
    byteBuf.put((byte)(             mode << 6 |
                                            0 | // digit (/0)
                       (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    if(m.width() == 8){
      byteBuf.put((byte)imm); // imm8
    }
    else if(m.width() == 16){
      byteBuf.putShort((short)imm); // imm16
    }
    else{
      byteBuf.putInt(imm); // imm32
    }

    return this;
  }

  /**
   * Subtract imm from r/m.
   * imm32 is treated as sign-extended if REX.W operation.
   *   Opcode: REX.W + 81 /5 id (64 bit)
   *                   81 /5 id (32 bit)
   *             66H + 81 /5 iw (16 bit)
   *                   80 /5 ib ( 8 bit)
   *   Instruction: SUB r/m, imm32 (64 bit, 32bit)
   *                SUB r/m, imm16 (16 bit)
   *                SUB r/m, imm8  ( 8 bit)
   *   Op/En: MI
   *
   * @param m "r/m" register
   * @param imm Immediate value to subtract
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder sub(Register m, int imm, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byte opcode = (m.width() == 8) ? (byte)0x80 : (byte)0x81;
    byteBuf.put(opcode); // SUB
    byteBuf.put((byte)(             mode << 6 |
                                       5 << 3 | // digit (/5)
                       (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    if(m.width() == 8){
      byteBuf.put((byte)imm); // imm8
    }
    else if(m.width() == 16){
      byteBuf.putShort((short)imm); // imm16
    }
    else{
      byteBuf.putInt(imm); // imm32
    }

    return this;
  }

  /**
   * Multiply r/m by 2, imm8 times.
   *   Opcode: REX.W + C1 /4 ib (64 bit)
   *                   C1 /4 ib (32 bit)
   *             66H + C1 /4 ib (16 bit)
   *                   C0 /4 ib ( 8 bit)
   *   Instruction: SHL r/m, imm8
   *   Op/En: MI
   *
   * @param m "r/m" register
   * @param imm Immediate value to subtract
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder shl(Register m, byte imm, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byte opcode = (m.width() == 8) ? (byte)0xc0 : (byte)0xc1;
    byteBuf.put(opcode); // SAL
    byteBuf.put((byte)(             mode << 6 |
                                       4 << 3 | // digit (/4)
                       (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    byteBuf.put(imm); // imm8
    return this;
  }

  /**
   * Set label at current position.
   *
   * @param name label name
   * @return This instance
   * @throws IllegalArgumentException thrown when the label already exists.
   */
  public AMD64AsmBuilder label(String name){
    if(labelMap.containsKey(name)){
      throw new IllegalArgumentException("Label \"" + name + "\" already exists.");
    }

    int labelPosition = byteBuf.position();
    labelMap.put(name, labelPosition);

    if(pendingLabelMap.containsKey(name)){
      Set<PendingJump> jumps = pendingLabelMap.remove(name);
      for(var jumpData : jumps){
        byteBuf.position(jumpData.position());
        int offset = labelPosition - jumpData.position();
        jumpData.emitOp().accept(offset);
      }
    }

    byteBuf.position(labelPosition);
    return this;
  }

  /**
   * One byte no-operation instruction
   *
   * @return This instance
   */
  public AMD64AsmBuilder nop(){
    byteBuf.put((byte)0x90);
    return this;
  }

  private void jcc(byte opcode8, byte[] opcode, String label){
    Consumer<Integer> emitOp = (o) -> {
      int offset = o.intValue() - 2;
      if((offset > -129) && (offset < 128)){
        // rel8
        byteBuf.put(opcode8);
        byteBuf.put((byte)offset);
      }
      else{
        // rel32
        offset -= 4; // opcode (2 bytes) - imm32 (4 bytes)
        byteBuf.put(opcode);
        byteBuf.putInt(offset);
      }
    };

    int position = byteBuf.position();
    Integer labelPosition = labelMap.get(label);
    if(labelPosition == null){
      /* forward jump - pending until label is set */
      Set<PendingJump> jumps = pendingLabelMap.computeIfAbsent(label, k -> new HashSet<>());
      jumps.add(new PendingJump(emitOp, position));

      // Fill with NOP in 6 bytes (max 2 opcodes + rel32) temporally.
      for(int i = 0; i < 6; i++){
        nop();
      }
    }
    else{
      int offset = labelPosition.intValue() - position;
      emitOp.accept(offset);
    }
  }

  /**
   * Jump if equal (ZF = 1).
   *   Opcode:    74 cb (rel8)
   *           0F 84 cd (rel32)
   *   Instruction: JE
   *   Op/En: D
   *
   * @param label the label to jump.
   * @return This instance
   */
  public AMD64AsmBuilder je(String label){
    jcc((byte)0x74, new byte[]{(byte)0x0f, (byte)0x84}, label);
    return this;
  }

  /**
   * Jump if less (SF ≠ OF).
   *   Opcode:    7C cb (rel8)
   *           0F 8C cd (rel32)
   *   Instruction: JL
   *   Op/En: D
   *
   * @param label the label to jump.
   * @return This instance
   */
  public AMD64AsmBuilder jl(String label){
    jcc((byte)0x7c, new byte[]{(byte)0x0f, (byte)0x8c}, label);
    return this;
  }

  /**
   * Jump if above or equal (CF = 0)
   *   Opcode:    73 cb (rel8)
   *           0F 83 cd (rel32)
   *   Instruction: JAE
   *   Op/En: D
   *
   * @param label the label to jump.
   * @return This instance
   */
  public AMD64AsmBuilder jae(String label){
    jcc((byte)0x73, new byte[]{(byte)0x0f, (byte)0x83}, label);
    return this;
  }

  /**
   * Jump if not equal (ZF = 0).
   *   Opcode:    75 cb (rel8)
   *           0F 85 cd (rel32)
   *   Instruction: JNE
   *   Op/En: D
   *
   * @param label the label to jump.
   * @return This instance
   */
  public AMD64AsmBuilder jne(String label){
    jcc((byte)0x75, new byte[]{(byte)0x0f, (byte)0x85}, label);
    return this;
  }

  /**
   * Jump.
   *   Opcode: EB cb (rel8)
   *           E9 cd (rel32)
   *   Instruction: JMP
   *   Op/En: D
   *
   * @param label the label to jump.
   * @return This instance
   */
  public AMD64AsmBuilder jmp(String label){
    Consumer<Integer> emitOp = (o) -> {
      /*
       * Offset should be following JMP instruction.
       * See pseudo code in Intel SDM for details.
       */
      int offset = o.intValue() - 2;
      if((offset > -129) && (offset < 128)){
        // rel8
        byteBuf.put((byte)0xeb);
        byteBuf.put((byte)offset);
      }
      else{
        // rel32
        offset -= 3; // opcode (1 bytes) - imm32 (4 bytes)
        byteBuf.put((byte)0xe9);
        byteBuf.putInt(offset);
      }
    };

    int position = byteBuf.position();
    Integer labelPosition = labelMap.get(label);
    if(labelPosition == null){
      /* forward jump - pending until label is set */
      Set<PendingJump> jumps = pendingLabelMap.computeIfAbsent(label, k -> new HashSet<>());
      jumps.add(new PendingJump(emitOp, position));

      // Fill with NOP in 5 bytes (max 1 opcodes + rel32) temporally.
      for(int i = 0; i < 5; i++){
        nop();
      }
    }
    else{
      int offset = labelPosition.intValue() - position;
      emitOp.accept(offset);
    }

    return this;
  }

  /**
   * Read a random number and store in the destination register.
   *   Opcode:   NFx 66H + 0F C7 /6 r16
   *                   NFx 0F C7 /6 r32
   *           NFx REX.W + 0F C7 /6 r64
   *   Instruction: RDRAND
   *   Op/En: M
   *
   * @param m "r/m" register
   * @return This instance
   */
  public AMD64AsmBuilder rdrand(Register m){
    byte mode = calcModRMMode(OptionalInt.empty());
    if(m.width() == 16){
      // Ops for 16 bits operands (66H)
      byteBuf.put((byte)0x66);
    }
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byteBuf.put((byte)0x0f); // RARAND (1)
    byteBuf.put((byte)0xc7); // RARAND (2)
    byteBuf.put((byte)(             mode << 6 |
                                       6 << 3 | // digit (/6)
                       (m.encoding() & 0x7)));

    return this;
  }

  /**
   * Read a NIST SP800-90B & C compliant random value and
   * store in the destination register.
   *   Opcode:   NFx 66H + 0F C7 /7 r16
   *                   NFx 0F C7 /7 r32
   *           NFx REX.W + 0F C7 /7 r64
   *   Instruction: RDSEED
   *   Op/En: M
   *
   * @param m "r/m" register
   * @return This instance
   */
  public AMD64AsmBuilder rdseed(Register m){
    byte mode = calcModRMMode(OptionalInt.empty());
    if(m.width() == 16){
      // Ops for 16 bits operands (66H)
      byteBuf.put((byte)0x66);
    }
    Register dummy = switch(m.width()){
      case  8 -> Register.AL;
      case 16 -> Register.AX;
      case 32 -> Register.EAX;
      default -> Register.RAX;
    };
    emitREXOp(dummy, m);
    byteBuf.put((byte)0x0f); // RARAND (1)
    byteBuf.put((byte)0xc7); // RARAND (2)
    byteBuf.put((byte)(             mode << 6 |
                                       7 << 3 | // digit (/7)
                       (m.encoding() & 0x7)));

    return this;
  }

  /**
   * Align the position to 16 bytes with NOP.
   *
   * @return This instance
   */
  public AMD64AsmBuilder alignTo16BytesWithNOP(){
    int position = byteBuf.position();
    if((position & 0xf) > 0){ // not aligned
      int newPosition = (position + 0x10) & 0xfffffff0;
      int diff = newPosition - position;
      for(int i = 0; i < diff; i++){
        nop();
      }
    }
    return this;
  }

  /**
   * Build as a MethodHandle
   *
   * @return MethodHandle for this assembly
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MethodHandle build(){
    if(!pendingLabelMap.isEmpty()){
      throw new IllegalStateException("Label is not defined: " + pendingLabelMap.keySet().toString());
    }
    seg.incTail(byteBuf.position());
    return Linker.nativeLinker().downcallHandle(mem, desc);
  }

}
