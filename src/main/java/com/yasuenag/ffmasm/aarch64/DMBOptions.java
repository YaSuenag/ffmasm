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
 * Enum for options of DMB instruction
 *
 * @author Yasumasa Suenaga
 */
public enum DMBOptions{

  OSHLD((byte)0b0001),
  OSHST((byte)0b0010),
  OSH((byte)0b0011),
  NSHLD((byte)0b0101),
  NSHST((byte)0b0110),
  NSH((byte)0b0111),
  ISHLD((byte)0b1001),
  ISHST((byte)0b1010),
  ISH((byte)0b1011),
  LD((byte)0b1101),
  ST((byte)0b1110),
  SY((byte)0b1111);

  private final byte crm;

  private DMBOptions(byte crm){
    this.crm = crm;
  }

  /**
   * CRm value of this option.
   * @return CRm
   */
  public int crm(){
    return crm;
  }

}
