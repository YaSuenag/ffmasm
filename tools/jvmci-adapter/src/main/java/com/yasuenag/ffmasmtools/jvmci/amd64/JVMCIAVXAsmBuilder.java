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
package com.yasuenag.ffmasmtools.jvmci.amd64;

import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.reflect.Method;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.JitDump;
import com.yasuenag.ffmasm.PlatformException;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.AVXAsmBuilder;

import com.yasuenag.ffmasmtools.jvmci.internal.amd64.AMD64AsmTemplate;


/**
 * Assembly builder for AVX instruction set to install machine code via JVMCI.
 *
 * @author Yasumasa Suenaga
 */
public class JVMCIAVXAsmBuilder extends AVXAsmBuilder<JVMCIAVXAsmBuilder>{

  private final AMD64AsmTemplate asmTemplate;

  /**
   * Constructor of JVMCIAVXAsmBuilder.
   * This c'tor creates CodeSegment with DEFAULT_CODE_SEGMENT_SIZE implicitly.
   */
  public JVMCIAVXAsmBuilder() throws PlatformException, UnsupportedPlatformException{
    this(CodeSegment.DEFAULT_CODE_SEGMENT_SIZE);
  }

  /**
   * Constructor of JVMCIAVXAsmBuilder.
   * This c'tor creates CodeSegment with given size implicitly.
   */
  public JVMCIAVXAsmBuilder(long codeSegmentSize) throws PlatformException, UnsupportedPlatformException{
    var seg = new CodeSegment(codeSegmentSize);
    super(seg, null);
    asmTemplate = new AMD64AsmTemplate(this);
    Cleaner.create().register(this, new CodeSegment.CleanerAction(seg));
  }

  /**
   * MethodHandle cannot be created by JVMCIAVXAsmBuilder.
   *
   * @throws UnsupportedOperationException
   */
  @Override
  public MethodHandle build(String name, JitDump jitdump, Linker.Option... options){
    throw new UnsupportedOperationException("AsmBuilder for JVMCI cannot return MethodHandle");
  }

  /**
   * Emit prologue machine code including nmethod entry barrier.
   *
   * @return this instance
   */
  public JVMCIAVXAsmBuilder emitPrologue(){
    asmTemplate.emitPrologue();
    return this;
  }

  /**
   * Emit epilogue machine code. It means "LEAVE" and "RET".
   *
   * @return this instance
   */
  public JVMCIAVXAsmBuilder emitEpilogue(){
    asmTemplate.emitEpilogue();
    return this;
  }

  /**
   * Install machine code to CodeCache as nmethod.
   * Contents of "method" would be replaced machine code in this instance
   * as default code.
   *
   * @param method method instance should be replaced
   * @param totalFrameSize frame size of this method. It should be 16 bytes
   *        at least - return address and saved RBP. It should be increased
   *        if RSP is expanded in machine code in this instance.
   */
  public void install(Method method, int totalFrameSize){
    asmTemplate.install(method, totalFrameSize);
  }

}
