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
  R15(15, 64),

  XMM0(0, 128),
  XMM1(1, 128),
  XMM2(2, 128),
  XMM3(3, 128),
  XMM4(4, 128),
  XMM5(5, 128),
  XMM6(6, 128),
  XMM7(7, 128),
  XMM8(8, 128),
  XMM9(9, 128),
  XMM10(10, 128),
  XMM11(11, 128),
  XMM12(12, 128),
  XMM13(13, 128),
  XMM14(14, 128),
  XMM15(15, 128),

  YMM0(0, 256),
  YMM1(1, 256),
  YMM2(2, 256),
  YMM3(3, 256),
  YMM4(4, 256),
  YMM5(5, 256),
  YMM6(6, 256),
  YMM7(7, 256),
  YMM8(8, 256),
  YMM9(9, 256),
  YMM10(10, 256),
  YMM11(11, 256),
  YMM12(12, 256),
  YMM13(13, 256),
  YMM14(14, 256),
  YMM15(15, 256);

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
