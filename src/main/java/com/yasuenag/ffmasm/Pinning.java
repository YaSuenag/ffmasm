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
package com.yasuenag.ffmasm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;
import com.yasuenag.ffmasm.internal.JniEnv;


/**
 * Pinning implementation
 *
 * This class provides features of GetPrimitiveArrayCritical() / ReleasePrimitiveArrayCritical()
 * JNI functions. It means the change on pinned MemorySegment is propagated to the original array.
 * You need to get instance of Pinning from getInstance() method.
 */
public final class Pinning{

  private static final Arena arena;
  private static final CodeSegment seg;

  private static Pinning instance;

  private final Map<MemorySegment, Object> pinnedMap;

  private static MemorySegment pinWrapperImpl;
  private native long pinWrapper(Object array);

  private static MemorySegment unpinWrapperImpl;
  private native long unpinWrapper(Object array, MemorySegment carray);

  private static void createWrapper() throws Throwable{
    var pinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                        ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                        ValueLayout.ADDRESS,   // arg2 (jobject)
                                        ValueLayout.ADDRESS);  // arg3 (array)
    pinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
               /* push %rbp      */ .push(Register.RBP)
               /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
               /* mov %rdx, %rsi */ .movMR(Register.RDX, Register.RSI, OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
               /* xor %rdx, %rdx */ .xorMR(Register.RDX, Register.RDX, OptionalInt.empty()) // xor arg3
               /* mov addr, %r10 */ .movImm(Register.R10, JniEnv.getInstance().getPrimitiveArrayCriticalAddr().address()) // address of GetPrimitiveArrayCritical()
               /* call %r10      */ .call(Register.R10)
               /* leave          */ .leave()
               /* ret            */ .ret()
                                    .getMemorySegment();

    var unpinDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, // return value (pinned address)
                                          ValueLayout.ADDRESS,   // arg1 (JNIEnv *)
                                          ValueLayout.ADDRESS,   // arg2 (jobject)
                                          ValueLayout.ADDRESS,   // arg3 (array)
                                          ValueLayout.ADDRESS);  // arg4 (carray)
    unpinWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, pinDesc)
                 /* push %rbp      */ .push(Register.RBP)
                 /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
                 /* mov %rdx, %rsi */ .movMR(Register.RDX, Register.RSI, OptionalInt.empty()) // move arg3 (arg1 in Java)  to arg2
                 /* mov %rcx, %rdx */ .movMR(Register.RCX, Register.RDX, OptionalInt.empty()) // move arg4 (arg2 in Java)  to arg3
                 /* xor %rcx, %rcx */ .xorMR(Register.RCX, Register.RCX, OptionalInt.empty()) // xor arg4
                 /* mov addr, %r10 */ .movImm(Register.R10, JniEnv.getInstance().releasePrimitiveArrayCriticalAddr().address()) // address of ReleasePrimitiveArrayCritical()
                 /* call %r10      */ .call(Register.R10)
                 /* leave          */ .leave()
                 /* ret            */ .ret()
                                      .getMemorySegment();

  }

  static{
    arena = Arena.ofAuto();
    try{
      seg = new CodeSegment();
      var segForCleaner = seg;
      Cleaner.create()
             .register(Pinning.class, () -> {
               try{
                 segForCleaner.close();
               }
               catch(Exception e){
                 // ignore
               }
             });

      createWrapper();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }

  }

  private Pinning() throws Throwable{
    pinnedMap = new HashMap<>();

    var clazz = this.getClass();
    var methodMap = Map.of(clazz.getDeclaredMethod("pinWrapper", Object.class), pinWrapperImpl, 
                           clazz.getDeclaredMethod("unpinWrapper", Object.class, MemorySegment.class), unpinWrapperImpl);
    var register = NativeRegister.create(clazz);
    register.registerNatives(methodMap);
  }

  /**
   * Pin array object.
   * Note that pinned long time, it might causes of preventing JVM behavior (e.g. GC)
   * See GetPrimitiveArrayCritical() JNI document for details.
   *
   * @param obj Primitive array to pin.
   * @return MemorySegment of pinned array
   * @throws IllegalArgumentException if obj is not an array.
   */
  public MemorySegment pin(Object obj){
    if(!obj.getClass().isArray()){
      throw new IllegalArgumentException("obj should be array type");
    }

    long rawAddr = pinWrapper(obj);
    if(rawAddr == 0L){
      throw new RuntimeException("GetPrimitiveArrayCritical() returns NULL");
    }

    var addr = MemorySegment.ofAddress(rawAddr);
    pinnedMap.put(addr, obj);
    return addr;
  }

  /**
   * Unpin array object.
   *
   * @param addr Pinned MemorySegment
   * @throws IllegalArgumentException if addr is not a pinned MemorySegment
   */
  public void unpin(MemorySegment addr){
    Object obj = pinnedMap.get(addr);
    if(obj == null){
      throw new IllegalArgumentException(STR."Address 0x\{Long.toHexString(addr.address())} is not pinned");
    }
    unpinWrapper(obj, addr);
    pinnedMap.remove(addr);
  }

  /**
   * Get instance of Pinning.
   *
   * @return Pinning insntance
   * @throws RuntimeException if an error happens at initialization.
   */
  public static Pinning getInstance(){
    if(instance == null){
      try{
        instance = new Pinning();
      }
      catch(Throwable t){
        throw new RuntimeException(t);
      }
    }
    return instance;
  }

}
