/*
 * Copyright (C) 2025 Yasumasa Suenaga
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
package com.yasuenag.ffmasmtools.jvmci.internal.amd64;

import java.util.OptionalInt;

import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;

import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;

import com.yasuenag.ffmasmtools.jvmci.internal.AsmTemplate;


/**
 * Template class for creating assembly code for AMD64.
 *
 * @autor Yasumasa Suenaga
 */
public class AMD64AsmTemplate extends AsmTemplate<AMD64AsmBuilder>{

  /**
   * Construct AMD64AsmTemplate instance.
   *
   * @param asmBuilder AsmBuilder instance to emit machine code from this instance
   */
  public AMD64AsmTemplate(AMD64AsmBuilder asmBuilder){
    super(asmBuilder);
  }

  /**
   * Emit prologue machine code including nmethod entry barrier.
   */
  @Override
  public void emitPrologue(){
    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), PROLOGUE_LABEL));
    asmBuilder.nop() // Insert 5 NOPs for NativeJump::patch_verified_entry
              .nop() // in HotSpot for code patching
              .nop()
              .nop()
              .nop()
              .push(Register.RBP)
              .movMR(Register.RSP, Register.RBP, OptionalInt.empty());

    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), BARRIER_LABEL));
    // nmethod entry barrier should be aligned with 4 bytes.
    asmBuilder.alignTo4BytesWithNOP();

    sites.add(new Mark(asmBuilder.getCodePosition(), MARKID_FRAME_COMPLETE));
    sites.add(new Mark(asmBuilder.getCodePosition(), MARKID_ENTRY_BARRIER_PATCH));

    asmBuilder.cmp(Register.R15D, 0, OptionalInt.of(threadDisarmedOffset))
              .je(FUNCBODY_LABEL)
              .movImm(Register.R10, nmethodEntryBarrier)
              .call(Register.R10)
              .label(FUNCBODY_LABEL);

    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), FUNCBODY_LABEL));

    prologueCalled = true;
  }

  /**
   * Emit epilogue machine code. It means "LEAVE" and "RET".
   */
  @Override
  public void emitEpilogue(){
    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), EPILOGUE_LABEL));
    asmBuilder.leave()
              .ret();

    epilogueCalled = true;
  }

}
