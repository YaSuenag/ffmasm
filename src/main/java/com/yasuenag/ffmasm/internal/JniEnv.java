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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;


public class JniEnv{

  /* from jni.h */
  public static final int  JNI_OK         = 0;
  private static final int JNI_VERSION_21 = 0x00150000;

  /* Indices from API doc */
  private static final int JNI_RegisterNatives_INDEX = 215;
  private static final int JNI_GetPrimitiveArrayCritical_INDEX = 222;
  private static final int JNI_ReleasePrimitiveArrayCritical_INDEX = 223;
  private static final int JNI_IsVirtualThread_INDEX = 234;
  private static final int JNI_MAX_INDEX = JNI_IsVirtualThread_INDEX;

  /* MemoryLayout for JNINativeMethod */
  public static final StructLayout layoutJNINativeMethod;
  public static final MemoryLayout.PathElement peName;
  public static final MemoryLayout.PathElement peSignature;
  public static final MemoryLayout.PathElement peFnPtr;

  /* 
   * Keep instance of this class.
   * JNIEnv* is associated with JavaThread in HotSpot.
   * So keep this insntance with ThreadLocal.
   */
  private static ThreadLocal<JniEnv> instance = new ThreadLocal(){
    @Override
    protected JniEnv initialValue(){
      try{
        return new JniEnv();
      }
      catch(Throwable t){
        throw new RuntimeException(t);
      }
    }
  };

  private final Arena arena;
  private final MemorySegment jniEnv;
  private final MemorySegment functionTable;

  /* function addresses / handles */
  private MethodHandle RegisterNatives;
  private MemorySegment GetPrimitiveArrayCriticalAddr;
  private MemorySegment ReleasePrimitiveArrayCriticalAddr;

  static{
    layoutJNINativeMethod = MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("name"),
                                                      ValueLayout.ADDRESS.withName("signature"),
                                                      ValueLayout.ADDRESS.withName("fnPtr"));
    peName = MemoryLayout.PathElement.groupElement("name");
    peSignature = MemoryLayout.PathElement.groupElement("signature");
    peFnPtr = MemoryLayout.PathElement.groupElement("fnPtr");
  }

  private JniEnv() throws Throwable{
    arena = Arena.ofAuto();

    var vm = JavaVM.getInstance();
    var env = arena.allocate(ValueLayout.ADDRESS);
    int result = vm.getEnv(env, JNI_VERSION_21);
    if(result != JniEnv.JNI_OK){
      throw new RuntimeException("GetEnv() returns " + result);
    }
    jniEnv = env.get(ValueLayout.ADDRESS, 0)
                .reinterpret(ValueLayout.ADDRESS.byteSize());

    functionTable = jniEnv.get(ValueLayout.ADDRESS, 0) // JNIEnv = JNINativeInterface_*
                          .reinterpret(ValueLayout.ADDRESS.byteSize() * (JNI_MAX_INDEX + 1));
  }

  public static JniEnv getInstance() throws Throwable{
    return instance.get();
  }

  public int registerNatives(MemorySegment clazz, MemorySegment methods, int nMethods) throws Throwable{
    if(RegisterNatives == null){
      RegisterNatives = Linker.nativeLinker()
                              .downcallHandle(functionTable.getAtIndex(ValueLayout.ADDRESS, JNI_RegisterNatives_INDEX),
                                              FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.JAVA_INT));
    }
    return (int)RegisterNatives.invoke(jniEnv, clazz, methods, nMethods);
  }

  public MemorySegment getPrimitiveArrayCriticalAddr(){
    if(GetPrimitiveArrayCriticalAddr == null){
      GetPrimitiveArrayCriticalAddr = functionTable.getAtIndex(ValueLayout.ADDRESS, JNI_GetPrimitiveArrayCritical_INDEX);
    }
    return GetPrimitiveArrayCriticalAddr;
  }

  public MemorySegment releasePrimitiveArrayCriticalAddr(){
    if(ReleasePrimitiveArrayCriticalAddr == null){
      ReleasePrimitiveArrayCriticalAddr = functionTable.getAtIndex(ValueLayout.ADDRESS, JNI_ReleasePrimitiveArrayCritical_INDEX);
    }
    return ReleasePrimitiveArrayCriticalAddr;
  }

}
