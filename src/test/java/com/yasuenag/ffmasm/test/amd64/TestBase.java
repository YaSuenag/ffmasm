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
package com.yasuenag.ffmasm.test.amd64;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.Register;


public class TestBase{

  public static record ArgRegister(
    Register arg1,
    Register arg2,
    Register arg3,
    Register arg4,
    Register returnReg
  ){}

  protected static ArgRegister argReg;

  @BeforeAll
  public static void init(){
    String osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      argReg = new ArgRegister(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.RAX);
    }
    else if(osName.startsWith("Windows")){
      argReg = new ArgRegister(Register.RCX, Register.RDX, Register.R8, Register.R9, Register.RAX);
    }
    else{
      throw new RuntimeException(new UnsupportedPlatformException(osName));
    };
  }

  /**
   * Show PID, address of CodeSegment, then waits stdin input.
   */
  public void showDebugMessage(CodeSegment seg) throws IOException{
    System.out.println("PID: " + ProcessHandle.current().pid());
    System.out.println("Addr: 0x" + Long.toHexString(seg.getAddr().address()));
    System.in.read();
  }

}
