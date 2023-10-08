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

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;

import com.yasuenag.ffmasm.internal.amd64.AMD64Pinning;


/**
 * Pinning implementation
 *
 * This class provides features of GetPrimitiveArrayCritical() / ReleasePrimitiveArrayCritical()
 * JNI functions. It means the change on pinned MemorySegment is propagated to the original array.
 * You need to get instance of Pinning from getInstance() method.
 */
public abstract class Pinning{

  private static Pinning instance;

  private final Map<MemorySegment, Object> pinnedMap;

  private native long pinWrapper(Object array);
  private native long unpinWrapper(Object array, MemorySegment carray);

  protected Pinning() throws Throwable{
    pinnedMap = new HashMap<>();
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
   * @throws Throwable if some error happens in initialization.
   */
  public static Pinning getInstance() throws Throwable{
    if(instance == null){
      var arch = System.getProperty("os.arch");
      if(arch.equals("amd64")){
        instance = new AMD64Pinning();
      }
      else{
        throw new UnsupportedPlatformException(STR."\{arch} is not supported");
       }
    }
    return instance;
  }

}
