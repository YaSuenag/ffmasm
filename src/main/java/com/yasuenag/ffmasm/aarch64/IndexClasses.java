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
package com.yasuenag.ffmasm.aarch64;


/**
 * Enum for AArch64 Index Class
 *
 * @author Yasumasa Suenaga
 */
public class IndexClasses{

  public static enum LDP_STP{
    PostIndex((byte)0b0001),
    PreIndex((byte)0b0011),
    SignedOffset((byte)0b0010);

    private final byte vr;

    private LDP_STP(byte vr){
      this.vr = vr;
    }

    /**
     * Index class
     * @return value of this class
     */
    public byte vr(){
      return vr;
    }

  }

  public static enum LDR_STR{
    PostIndex((byte)0b000),
    PreIndex((byte)0b000),
    UnsignedOffset((byte)0b001);

    private final byte vr;

    private LDR_STR(byte vr){
      this.vr = vr;
    }

    /**
     * Index class
     * @return value of this class
     */
    public byte vr(){
      return vr;
    }

  }

}
