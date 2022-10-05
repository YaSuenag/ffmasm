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
    byte mode = calcModRMMode(disp);
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
   * Compare imm32 with r/m.
   * imm32 is treated as sign-extended if REX.W operation.
   *   Opcode: REX.W + 81 /7 id (64 bit)
   *                   81 /7 id (32 bit)
   *   Instruction: CMP r/m, imm32
   *   Op/En: MI
   *
   * @param m "r/m" register
   * @param imm Immediate value to compare.
   * @param disp Displacement. Set "empty" if this operation is reg-reg.
   * @return This instance
   */
  public AMD64AsmBuilder cmp(Register m, int imm, OptionalInt disp){
    byte mode = calcModRMMode(disp);
    emitREXOp(Register.RAX /* dummy*/, m);
    byteBuf.put((byte)0x81); // CMP
    byteBuf.put((byte)(             mode << 6 |
                                       7 << 3 | // digit (/7)
                       (m.encoding() & 0x7)));

    if(mode == 0b01){ // reg-mem disp8
      byteBuf.put((byte)disp.getAsInt());
    }
    else if(mode == 0b10){ // reg-mem disp32
      byteBuf.putInt(disp.getAsInt());
    }

    byteBuf.putInt(imm); // imm32

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
    Consumer<Integer> emitOp = (o) -> {
      int offset = o.intValue() - 2;
      if((offset > -129) && (offset < 128)){
        // rel8
        byteBuf.put((byte)0x7c);
        byteBuf.put((byte)offset);
      }
      else{
        // rel32
        offset -= 4; // opcode (2 bytes) - imm32 (4 bytes)
        byteBuf.put((byte)0x0f);
        byteBuf.put((byte)0x8c);
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
    Consumer<Integer> emitOp = (o) -> {
      int offset = o.intValue() - 2;
      if((offset > -129) && (offset < 128)){
        // rel8
        byteBuf.put((byte)0x73);
        byteBuf.put((byte)offset);
      }
      else{
        // rel32
        offset -= 4; // opcode (2 bytes) - imm32 (4 bytes)
        byteBuf.put((byte)0x0f);
        byteBuf.put((byte)0x83);
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
   *   Opcode: NFx 0F C7 /6 RDRAND r32
   *           NFx REX.W + 0F C7 /6 RDRAND r64
   *   Instruction: RDRAND
   *   Op/En: M
   *
   * @param m "r/m" register
   * @return This instance
   */
  public AMD64AsmBuilder rdrand(Register m){
    byte mode = calcModRMMode(OptionalInt.empty());
    emitREXOp(Register.RAX /* dummy*/, m);
    byteBuf.put((byte)0x0f); // RARAND (1)
    byteBuf.put((byte)0xc7); // RARAND (2)
    byteBuf.put((byte)(             mode << 6 |
                                       6 << 3 | // digit (/6)
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
