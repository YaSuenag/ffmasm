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
package com.yasuenag.ffmasm.internal.amd64;

import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.amd64.Register;


public record CallingRegisters(
  Register arg1,
  Register arg2,
  Register arg3,
  Register arg4,
  Register returnReg,
  Register savedReg1,
  Register tmpReg1){

  public static CallingRegisters getRegs() throws UnsupportedPlatformException{
    var arch = System.getProperty("os.arch");
    if(!arch.equals("amd64")){
      throw new UnsupportedPlatformException(STR."\{arch} is not supported.");
    }

    String osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      return new CallingRegisters(Register.RDI,
                                  Register.RSI,
                                  Register.RDX,
                                  Register.RCX,
                                  Register.RAX,
                                  Register.R12,
                                  Register.R10);
    }
    else if(osName.startsWith("Windows")){
      return new CallingRegisters(Register.RCX,
                                  Register.RDX,
                                  Register.R8,
                                  Register.R9,
                                  Register.RAX,
                                  Register.R12,
                                  Register.R10);
    }
    else{
      throw new UnsupportedPlatformException(STR."\{osName} is not supported.");
    }
  }

}
