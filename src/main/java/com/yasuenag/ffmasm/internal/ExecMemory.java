/*
 * Copyright (C) 2022 Yasumasa Suenaga
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

import java.lang.foreign.MemoryAddress;

import com.yasuenag.ffmasm.PlatformException;


/**
 * Interface for acquiring / releasing memory for execution code.
 * Implemented class should manage platform-dependent memory which can execute code in it.
 *
 * @author Yasumasa Suenaga
 */
public interface ExecMemory{

  /**
   * Allocate memory which can execute code in it.
   *
   * @param size required size
   * @return platform memory address
   * @throws PlatformMemoryException thrown when memory allocation fails.
   */
  public MemoryAddress allocate(long size) throws PlatformException;

  /**
   * Deallocate memory which is pointed addr.
   *
   * @param addr platform memory address
   * @param size required size
   * @throws PlatformMemoryException thrown when memory deallocation fails.
   */
  public void deallocate(MemoryAddress addr, long size) throws PlatformException;

}
