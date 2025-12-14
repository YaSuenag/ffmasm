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
package com.yasuenag.ffmasmtools.jvmci.internal.aarch64;

import java.util.Optional;

import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;

import com.yasuenag.ffmasm.aarch64.AArch64AsmBuilder;
import com.yasuenag.ffmasm.aarch64.DMBOptions;
import com.yasuenag.ffmasm.aarch64.HWShift;
import com.yasuenag.ffmasm.aarch64.IndexClass;
import com.yasuenag.ffmasm.aarch64.Register;
import com.yasuenag.ffmasm.aarch64.ShiftType;

import com.yasuenag.ffmasmtools.jvmci.internal.AsmTemplate;


/**
 * Template class for creating assembly code for AArch64.
 *
 * @autor Yasumasa Suenaga
 */
public class AArch64AsmTemplate extends AsmTemplate<AArch64AsmBuilder>{

  protected static final boolean ropProtection;
  protected static final boolean nmethodEntryBarrierConcurrentPatch;

  private static boolean checkNMethodEntryBarrierConcurrentPatch(){
    if(nmethodEntryBarrier != 0){
      Integer patchingType = config.getFieldValue("CompilerToVM::Data::BarrierSetAssembler_nmethod_patching_type", Integer.class);
      if(patchingType != null){
        int conc = config.getConstant("NMethodPatchingType::conc_instruction_and_data_patch", Integer.class);
        if(patchingType == conc){
          return true;
        }
      }
    }
    return false;
  }

  static{
    ropProtection = config.getFieldValue("VM_Version::_rop_protection", Boolean.class);
    nmethodEntryBarrierConcurrentPatch = checkNMethodEntryBarrierConcurrentPatch();
  }

  /**
   * Construct AArch64AsmTemplate instance.
   *
   * @param asmBuilder AsmBuilder instance to emit machine code from this instance
   */
  public AArch64AsmTemplate(AArch64AsmBuilder asmBuilder){
    super(asmBuilder);
  }

  /**
   * Emit prologue machine code including nmethod entry barrier.
   */
  @Override
  public void emitPrologue(){
    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), PROLOGUE_LABEL));

    // Insert NOP for NativeJump::patch_verified_entry in HotSpot for code patching
    asmBuilder.nop();

    if(ropProtection){
      // Check return address (LR) if PAC enabled to make debug easily.
      // See MacroAssembler::check_return_address() in HotSpot for details.
      asmBuilder.ldr(Register.XZR, Register.X30, IndexClass.UnsignedOffset, 0)
                .paciaz();
    }

    asmBuilder.stp(Register.X29, Register.X30, Register.SP, IndexClass.PreIndex, -16)
              .mov(Register.X29, Register.SP);

    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), BARRIER_LABEL));
    sites.add(new Mark(asmBuilder.getCodePosition(), MARKID_ENTRY_BARRIER_PATCH));

    var ref = new DataSectionReference();
    ref.setOffset(data.position());
    sites.add(new DataPatch(asmBuilder.getCodePosition(), ref));
    data.putInt(0);

    sites.add(new DataPatch(asmBuilder.getCodePosition(), ref));
    asmBuilder.ldr(Register.W8, 0xdead);
    if(nmethodEntryBarrierConcurrentPatch){
      asmBuilder.dmb(DMBOptions.ISHLD);
    }

    asmBuilder.ldr(Register.W9, Register.W28, IndexClass.UnsignedOffset, threadDisarmedOffset)
              .cmp(Register.W8, Register.W9, ShiftType.LSL, (byte)0)
              .beq(FUNCBODY_LABEL);

    // nmethod entry barrier
    asmBuilder.movz(Register.X9, (int)(nmethodEntryBarrier & 0xffff), HWShift.None)
              .movk(Register.X9, (int)((nmethodEntryBarrier >> 16) & 0xffff), HWShift.HW_16)
              .movk(Register.X9, (int)((nmethodEntryBarrier >> 32) & 0xffff), HWShift.HW_32)
              .movk(Register.X9, (int)((nmethodEntryBarrier >> 48) & 0xffff), HWShift.HW_48)
              .blr(Register.X9);

    asmBuilder.label(FUNCBODY_LABEL);
    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), FUNCBODY_LABEL));

    prologueCalled = true;
  }

  /**
   * Emit epilogue machine code. It means "LEAVE" and "RET".
   */
  @Override
  public void emitEpilogue(){
    comments.add(new HotSpotCompiledCode.Comment(asmBuilder.getCodePosition(), EPILOGUE_LABEL));
    asmBuilder.ldp(Register.X29, Register.X30, Register.SP, IndexClass.PostIndex, 16);
    if(ropProtection){
      asmBuilder.autiaz();
    }
    asmBuilder.ret(Optional.empty());

    epilogueCalled = true;
  }

}
