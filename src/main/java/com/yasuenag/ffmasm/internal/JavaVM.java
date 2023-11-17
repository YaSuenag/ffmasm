/*
 * Copyright (C) 2023, Yasumasa Suenaga
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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

import com.yasuenag.ffmasm.UnsupportedPlatformException;


public class JavaVM{

  /* Indices from API doc */
  private static final int JavaVM_GetEnv_INDEX = 6;
  private static final int JavaVM_AttachCurrentThreadAsDaemon_INDEX = 7;
  private static final int JavaVM_MAX_INDEX = JavaVM_AttachCurrentThreadAsDaemon_INDEX;

  /* Keep instance of this class as a singleton */
  private static JavaVM instance;

  private final Arena arena;
  private final MemorySegment vm;
  private final MemorySegment functionTable;

  /* function handles */
  private MethodHandle GetEnv;

  private SymbolLookup getJvmLookup() throws UnsupportedPlatformException{
    String osName = System.getProperty("os.name");
    Path jvmPath;
    if(osName.equals("Linux")){
      jvmPath = Path.of(System.getProperty("java.home"), "lib", "server", "libjvm.so");
    }
    else if(osName.startsWith("Windows")){
      jvmPath = Path.of(System.getProperty("java.home"), "bin", "server", "jvm.dll");
    }
    else{
      throw new UnsupportedPlatformException(osName + " is unsupported.");
    }

    return SymbolLookup.libraryLookup(jvmPath, arena);
  }

  private JavaVM() throws Throwable{
    arena = Arena.ofAuto();

    var JNI_GetCreatedJavaVMs = Linker.nativeLinker()
                                      .downcallHandle(getJvmLookup().find("JNI_GetCreatedJavaVMs").get(),
                                                      FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                            ValueLayout.ADDRESS,
                                                                            ValueLayout.JAVA_INT,
                                                                            ValueLayout.ADDRESS));
    var vms = arena.allocate(ValueLayout.ADDRESS, 1);
    int result = (int)JNI_GetCreatedJavaVMs.invoke(vms, 1, MemorySegment.NULL);
    if(result != JniEnv.JNI_OK){
      throw new RuntimeException(STR."JNI_GetCreatedJavaVMs() returns \{result}");
    }
    vm = vms.getAtIndex(ValueLayout.ADDRESS, 0)
            .reinterpret(ValueLayout.ADDRESS.byteSize());

    functionTable = vm.getAtIndex(ValueLayout.ADDRESS, 0)
                      .reinterpret(ValueLayout.ADDRESS.byteSize() * (JavaVM_MAX_INDEX + 1));
  }

  public static JavaVM getInstance() throws Throwable{
    if(instance == null){
      instance = new JavaVM();
    }
    return instance;
  }

  public int getEnv(MemorySegment penv, int version) throws Throwable{
    if(GetEnv == null){
      GetEnv = Linker.nativeLinker()
                     .downcallHandle(functionTable.getAtIndex(ValueLayout.ADDRESS, JavaVM_GetEnv_INDEX),
                                     FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                           ValueLayout.ADDRESS,
                                                           ValueLayout.ADDRESS,
                                                           ValueLayout.JAVA_INT));
    }

    return (int)GetEnv.invoke(vm, penv, version);
  }

}
