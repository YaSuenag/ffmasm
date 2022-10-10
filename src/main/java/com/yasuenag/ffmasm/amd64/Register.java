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


/**
 * Enum for AMD64 CPU register
 *
 * @author Yasumasa Suenaga
 */
public enum Register{

  AL(0, 8),
  CL(1, 8),
  DL(2, 8),
  BL(3, 8),
  AH(4, 8),
  CH(5, 8),
  DH(6, 8),
  BH(7, 8),

  AX(0, 16),
  CX(1, 16),
  DX(2, 16),
  BX(3, 16),
  SP(4, 16),
  BP(5, 16),
  SI(6, 16),
  DI(7, 16),

  EAX(0, 32),
  ECX(1, 32),
  EDX(2, 32),
  EBX(3, 32),
  ESP(4, 32),
  EBP(5, 32),
  ESI(6, 32),
  EDI(7, 32),
  R8D(8, 32),
  R9D(9, 32),
  R10D(10, 32),
  R11D(11, 32),
  R12D(12, 32),
  R13D(13, 32),
  R14D(14, 32),
  R15D(15, 32),

  RAX(0, 64),
  RCX(1, 64),
  RDX(2, 64),
  RBX(3, 64),
  RSP(4, 64),
  RBP(5, 64),
  RSI(6, 64),
  RDI(7, 64),
  R8(8, 64),
  R9(9, 64),
  R10(10, 64),
  R11(11, 64),
  R12(12, 64),
  R13(13, 64),
  R14(14, 64),
  R15(15, 64);

  private final int encoding;

  private final int width;

  private Register(int encoding, int width){
    this.encoding = encoding;
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
