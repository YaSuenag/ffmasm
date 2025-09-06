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
 * Enum for AArch64 CPU register
 *
 * @author Yasumasa Suenaga
 */
public enum Register{

  X0(0, 64),
  X1(1, 64),
  X2(2, 64),
  X3(3, 64),
  X4(4, 64),
  X5(5, 64),
  X6(6, 64),
  X7(7, 64),
  X8(8, 64),
  X9(9, 64),
  X10(10, 64),
  X11(11, 64),
  X12(12, 64),
  X13(13, 64),
  X14(14, 64),
  X15(15, 64),
  X16(16, 64),
  X17(17, 64),
  X18(18, 64),
  X19(19, 64),
  X20(20, 64),
  X21(21, 64),
  X22(22, 64),
  X23(23, 64),
  X24(24, 64),
  X25(25, 64),
  X26(26, 64),
  X27(27, 64),
  X28(28, 64),
  X29(29, 64),
  X30(30, 64),
  SP(-1, 64);

  private final int encoding;

  private final int width;

  private Register(int encoding, int width){
    this.encoding = encoding & 0b11111;
    this.width = width;
  }

  /**
   * Register encoding
   * @return Register encoding
   */
  public int encoding(){
    return encoding;
  }

  /**
   * Register width in bits
   * @return Register width
   */
  public int width(){
    return width;
  }

}
