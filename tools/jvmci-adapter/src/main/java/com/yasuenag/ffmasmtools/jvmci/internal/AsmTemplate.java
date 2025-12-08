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
package com.yasuenag.ffmasmtools.jvmci.internal;

import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;

import com.yasuenag.ffmasm.AsmBuilder;


/**
 * Template class for creating assembly code.
 *
 * @author Yasumasa Suenaga
 */
public abstract class AsmTemplate<T extends AsmBuilder>{

  protected static final String PROLOGUE_LABEL = "Prologue";
  protected static final String BARRIER_LABEL  = "NMethod entry barrier";
  protected static final String FUNCBODY_LABEL = "Function Body";
  protected static final String EPILOGUE_LABEL = "Epilogue";

  protected static final int MARKID_FRAME_COMPLETE;
  protected static final int MARKID_ENTRY_BARRIER_PATCH;
  protected static final int threadDisarmedOffset;
  protected static final long nmethodEntryBarrier;

  protected static final MetaAccessProvider metaAccess;

  private static final CodeCacheProvider codeCache;

  static{
    var runtime = HotSpotJVMCIRuntime.runtime();
    var configStore = runtime.getConfigStore();
    var config = new HotSpotVMConfigAccess(configStore);

    MARKID_FRAME_COMPLETE = config.getConstant("CodeInstaller::FRAME_COMPLETE", Integer.class);
    MARKID_ENTRY_BARRIER_PATCH = config.getConstant("CodeInstaller::ENTRY_BARRIER_PATCH", Integer.class);
    threadDisarmedOffset = config.getFieldValue("CompilerToVM::Data::thread_disarmed_guard_value_offset", Integer.class, "int");
    nmethodEntryBarrier = config.getFieldValue("CompilerToVM::Data::nmethod_entry_barrier", Long.class, "address");

    var backend = runtime.getHostJVMCIBackend();
    metaAccess = backend.getMetaAccess();
    codeCache = backend.getCodeCache();
  }

  protected final List<Site> sites;
  protected final List<HotSpotCompiledCode.Comment> comments;
  protected final T asmBuilder;

  protected boolean prologueCalled;
  protected boolean epilogueCalled;

  /**
   * Constructor
   *
   * @param asmBuilder AsmBuilder instance to emit machine code from this in
stance
   */
  public AsmTemplate(T asmBuilder){
    sites = new ArrayList<>();
    comments = new ArrayList<>();
    this.asmBuilder = asmBuilder;
    prologueCalled = false;
    epilogueCalled = false;
  }

  /**
   * Emit prologue code.
   */
  public abstract void emitPrologue();

  /**
   * Emit epilogue code.
   */
  public abstract void emitEpilogue();

  /**
   * Check whether both prologue and epilogue are emitted.
   *
   * @throws IllegalStateException if prologue and/or epilogue are emitted.
   */
  public void check(){
    if(!prologueCalled){
      throw new IllegalStateException("Prologue is noe emitted.");
    }
    if(!epilogueCalled){
      throw new IllegalStateException("Epilogue is noe emitted.");
    }
  }

  public Site[] getSites(){
    return sites.toArray(new Site[0]);
  }

  public HotSpotCompiledCode.Comment[] getComments(){
    return comments.toArray(new HotSpotCompiledCode.Comment[0]);
  }

  /**
   * Install machine code to CodeCache as nmethod.
   * Contents of "method" would be replaced machine code in this instance
   * as default code.
   *
   * @param method method instance should be replaced
   * @param totalFrameSize frame size of this method. It should be 16 bytes
   *        at least - in AMD64, return address and saved RBP. It should be
   *        increased if RSP is expanded in machine code in this instance.
   */
  public void install(Method method, int totalFrameSize){
    var func = asmBuilder.getMemorySegment("<jvmci: " + method.getName() + ">", null);
    var resolvedMethod = (HotSpotResolvedJavaMethod)metaAccess.lookupJavaMethod(method);
    var rawMachineCode = func.toArray(ValueLayout.JAVA_BYTE);

    var nmethod = new HotSpotCompiledNmethod(
                        resolvedMethod.getName(),
                        rawMachineCode,
                        rawMachineCode.length,
                        getSites(),
                        null, // assumptions
                        new ResolvedJavaMethod[]{resolvedMethod}, // methods
                        getComments(),
                        new byte[0], // data section
                        16, // data section alignment
                        new DataPatch[0], // data section patches
                        true, // isImmutablePIC
                        totalFrameSize,
                        null, // deopt rescue slot
                        resolvedMethod,
                        -1, // entry BCI
                        resolvedMethod.allocateCompileId(0),
                        0L, // compile state
                        false // has unsafe access
                      );
    resolvedMethod.setNotInlinableOrCompilable();
    codeCache.setDefaultCode(resolvedMethod, nmethod);
  }

}
