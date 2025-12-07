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
package com.yasuenag.ffmasm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.JitDump;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.aarch64.AArch64AsmBuilder;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.AVXAsmBuilder;
import com.yasuenag.ffmasm.amd64.SSEAsmBuilder;


/**
 * Base class of assembly builder.
 *
 * @param <T> Implementation of AsmBuilder
 *
 * @author Yasumasa Suenaga
 */
public class AsmBuilder<T extends AsmBuilder>{

  /**
   * Builder class for AMD64
   */
  public static final class AMD64 extends AMD64AsmBuilder<AMD64>{

    public AMD64(CodeSegment seg) throws UnsupportedPlatformException{
      this(seg, null);
    }

    public AMD64(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }

  }

  /**
   * Builder class for SSE
   */
  public static final class SSE extends SSEAsmBuilder<SSE>{

    public SSE(CodeSegment seg) throws UnsupportedPlatformException{
      this(seg, null);
    }

    public SSE(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }

  }

  /**
   * Builder class for AVX
   */
  public static final class AVX extends AVXAsmBuilder<AVX>{

    public AVX(CodeSegment seg) throws UnsupportedPlatformException{
      this(seg, null);
    }

    public AVX(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }

  }

  /**
   * Builder class for AArch64
   */
  public static final class AArch64 extends AArch64AsmBuilder<AArch64>{

    public AArch64(CodeSegment seg) throws UnsupportedPlatformException{
      this(seg, null);
    }

    public AArch64(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }

  }

  private final CodeSegment seg;

  private final MemorySegment mem;

  /**
   * ByteBuffer which includes code content.
   */
  protected final ByteBuffer byteBuf;

  private final FunctionDescriptor desc;

  // Key: label, Value: position
  protected final Map<String, Integer> labelMap;

  // Key: label, Value: jump data
  public static record PendingJump(Consumer<Integer> emitOp, int position){}
  protected final Map<String, Set<PendingJump>> pendingLabelMap;

  protected AsmBuilder(CodeSegment seg, FunctionDescriptor desc){
    seg.alignTo16Bytes();

    this.seg = seg;
    this.mem = seg.getTailOfMemorySegment();
    this.byteBuf = mem.asByteBuffer().order(ByteOrder.nativeOrder());
    this.desc = desc;
    this.labelMap = new HashMap<>();
    this.pendingLabelMap = new HashMap<>();
  }

  /**
   * Cast "this" to "T" without unchecked warning.
   *
   * @return "this" casted to "T"
   */
  @SuppressWarnings("unchecked")
  protected T castToT(){
    return (T)this;
  }

  /**
   * Get current position of code buffer.
   *
   * @return position of code buffer
   */
  public int getCodePosition(){
    return byteBuf.position();
  }

  private void updateTail(){
    if(!pendingLabelMap.isEmpty()){
      throw new IllegalStateException("Label is not defined: " + pendingLabelMap.keySet().toString());
    }
    seg.incTail(byteBuf.position());
  }

  /**
   * Build as a MethodHandle
   *
   * @param options Linker options to pass to downcallHandle().
   * @return MethodHandle for this assembly
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MethodHandle build(Linker.Option... options){
    return build("<unnamed>", options);
  }

  /**
   * Build as a MethodHandle
   *
   * @param name Method name
   * @param options Linker options to pass to downcallHandle().
   * @return MethodHandle for this assembly
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MethodHandle build(String name, Linker.Option... options){
    return build(name, null, options);
  }

  private void storeMethodInfo(String name, JitDump jitdump){
    var top = mem.address();
    var size = byteBuf.position();
    var methodInfo = seg.addMethodInfo(name, top, size);
    if(jitdump != null){
      jitdump.writeFunction(methodInfo);
    }
  }

  /**
   * Build as a MethodHandle
   *
   * @param name Method name
   * @param jitdump JitDump instance which should be written.
   * @param options Linker options to pass to downcallHandle().
   * @return MethodHandle for this assembly
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MethodHandle build(String name, JitDump jitdump, Linker.Option... options){
    updateTail();
    storeMethodInfo(name, jitdump);
    return Linker.nativeLinker().downcallHandle(mem, desc, options);
  }

  /**
   * Get MemorySegment which is associated with this builder.
   *
   * @return MemorySegment of this builder
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MemorySegment getMemorySegment(){
    return getMemorySegment("<unnamed>");
  }

  /**
   * Get MemorySegment which is associated with this builder.
   *
   * @param name Method name
   * @return MemorySegment of this builder
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MemorySegment getMemorySegment(String name){
    return getMemorySegment(name, null);
  }

  /**
   * Get MemorySegment which is associated with this builder.
   *
   * @param name Method name
   * @param jitdump JitDump instance which should be written.
   * @return MemorySegment of this builder
   * @throws IllegalStateException when label(s) are not defined even if they are used
   */
  public MemorySegment getMemorySegment(String name, JitDump jitdump){
    updateTail();
    storeMethodInfo(name, jitdump);
    long length = byteBuf.position();
    return mem.reinterpret(length);
  }

}
