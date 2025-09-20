/*
 * Copyright (C) 2023, 2024, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.internal;

import java.lang.invoke.MethodHandle;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


public class JvmtiEnv{

  /* from jvmti.h */
  public static final int JVMTI_ERROR_NONE = 0;
  private static final int JVMTI_VERSION_21 = 0x30150000;

  /* indices / positions are from API doc */
  private static final int JVMTI_Deallocate_POSITION = 47;
  private static final int JVMTI_GetClassSignature_POSITION = 48;
  private static final int JVMTI_GetLoadedClasses_POSITION = 78;
  private static final int JVMTI_SetHeapSamplingInterval_POSITION = 156;
  private static final int JVMTI_MAX_POSITION = JVMTI_SetHeapSamplingInterval_POSITION;

  /* Keep instance of this class as a singleton */
  private static JvmtiEnv instance;

  private final Arena arena;
  private final MemorySegment jvmtiEnv;
  private final MemorySegment functionTable;

  /* function addresses / handles */
  private MemorySegment GetLoadedClassesAddr;
  private MemorySegment DeallocateAddr;
  private MethodHandle Deallocate;
  private MethodHandle GetClassSignature;

  private JvmtiEnv() throws Throwable{
    arena = Arena.ofAuto();

    var vm = JavaVM.getInstance();
    var env = arena.allocate(ValueLayout.ADDRESS);
    int result = vm.getEnv(env, JVMTI_VERSION_21);
    if(result != JniEnv.JNI_OK){
      throw new RuntimeException("GetEnv() returns " + result);
    }
    jvmtiEnv = env.get(ValueLayout.ADDRESS, 0)
                  .reinterpret(ValueLayout.ADDRESS.byteSize());

    functionTable = jvmtiEnv.get(ValueLayout.ADDRESS, 0)
                            .reinterpret(ValueLayout.ADDRESS.byteSize() * JVMTI_MAX_POSITION);
  }

  public static JvmtiEnv getInstance() throws Throwable{
    if(instance == null){
      instance = new JvmtiEnv();
    }
    return instance;
  }

  public long getRawAddress(){
    return jvmtiEnv.address();
  }

  public MemorySegment getMemorySegment(){
    return jvmtiEnv;
  }

  public MemorySegment getLoadedClassesAddr(){
    if(GetLoadedClassesAddr == null){
      GetLoadedClassesAddr = functionTable.getAtIndex(ValueLayout.ADDRESS, JVMTI_GetLoadedClasses_POSITION - 1);
    }
    return GetLoadedClassesAddr;
  }

  public MemorySegment deallocateAddr(){
    if(DeallocateAddr == null){
      DeallocateAddr = functionTable.getAtIndex(ValueLayout.ADDRESS, JVMTI_Deallocate_POSITION - 1);
    }
    return DeallocateAddr;
  }

  public int deallocate(MemorySegment mem) throws Throwable{
    if(Deallocate == null){
      Deallocate = Linker.nativeLinker()
                         .downcallHandle(deallocateAddr(),
                                         FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                               ValueLayout.ADDRESS,
                                                               ValueLayout.ADDRESS));
    }
    return (int)Deallocate.invoke(jvmtiEnv, mem);
  }

  public int getClassSignature(MemorySegment klass, MemorySegment signature_ptr, MemorySegment generic_ptr) throws Throwable{
    if(GetClassSignature == null){
      GetClassSignature = Linker.nativeLinker()
                                .downcallHandle(functionTable.getAtIndex(ValueLayout.ADDRESS, JVMTI_GetClassSignature_POSITION - 1),
                                                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                      ValueLayout.ADDRESS,
                                                                      ValueLayout.ADDRESS,
                                                                      ValueLayout.ADDRESS,
                                                                      ValueLayout.ADDRESS));
    }
    return (int)GetClassSignature.invoke(jvmtiEnv, klass, signature_ptr, generic_ptr);
  }

}
