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
package com.yasuenag.ffmasm.amd64;

import java.lang.foreign.FunctionDescriptor;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;


/**
 * Builder for AVX hand-assembling
 *
 * @author Yasumasa Suenaga
 */
public class AVXAsmBuilder extends SSEAsmBuilder{

  /**
   * Constructor.
   *
   * @param seg CodeSegment which is used by this builder.
   * @param desc FunctionDescriptor for this builder. It will be used by build().
   */
  protected AVXAsmBuilder(CodeSegment seg, FunctionDescriptor desc){
    super(seg, desc);
  }

  /**
   * {@inheritDoc}
   */
  public static AVXAsmBuilder create(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
    seg.alignTo16Bytes();
    return new AVXAsmBuilder(seg, desc);
  }

}
