/*
 * Copyright (C) 2025, Yasumasa Suenaga
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

import java.lang.foreign.FunctionDescriptor;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.internal.aarch64.AArch64AsmBuilder;
import com.yasuenag.ffmasm.internal.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.internal.amd64.AVXAsmBuilder;
import com.yasuenag.ffmasm.internal.amd64.SSEAsmBuilder;


/**
 * Base class of assembly builder.
 *
 * @author Yasumasa Suenaga
 */
public class AsmBuilder{

  /**
   * Builder class for AMD64
   */
  public static final class AMD64 extends AMD64AsmBuilder<AMD64>{
    public AMD64(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }
  }

  /**
   * Builder class for SSE
   */
  public static final class SSE extends SSEAsmBuilder<SSE>{
    public SSE(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }
  }

  /**
   * Builder class for AVX
   */
  public static final class AVX extends AVXAsmBuilder<AVX>{
    public AVX(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }
  }

  /**
   * Builder class for AArch64
   */
  public static final class AArch64 extends AArch64AsmBuilder{
    public AArch64(CodeSegment seg, FunctionDescriptor desc) throws UnsupportedPlatformException{
      super(seg, desc);
    }
  }

}
