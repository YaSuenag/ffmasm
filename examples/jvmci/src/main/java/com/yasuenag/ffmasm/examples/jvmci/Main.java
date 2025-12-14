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
package com.yasuenag.ffmasm.examples.jvmci;

import java.lang.reflect.*;

import com.yasuenag.ffmasm.aarch64.*;
import com.yasuenag.ffmasm.amd64.*;

import com.yasuenag.ffmasmtools.jvmci.aarch64.*;
import com.yasuenag.ffmasmtools.jvmci.amd64.*;


public class Main{

  public static int getPid(){
    throw new UnsupportedOperationException("This method should be overriden by jvmci-adapter in ffmasm");
  }

  private static void installAMD64Code(Method method) throws Exception{
    new JVMCIAMD64AsmBuilder()
                    .emitPrologue()
/* mov %rax, $39 */ .movImm(com.yasuenag.ffmasm.amd64.Register.RAX, 39) // getpid
/* syscall       */ .syscall()
                    .emitEpilogue()
                    .install(method, 16);
  }

  private static void installAArch64Code(Method method) throws Exception{
    new JVMCIAArch64AsmBuilder()
                    .emitPrologue()
/* movz x8, $172 */ .movz(com.yasuenag.ffmasm.aarch64.Register.X8, 172, HWShift.None) // getpid
/* svc #0        */ .svc(0)
                    .emitEpilogue()
                    .install(method, 16);
  }

  public static void main(String[] args) throws Exception{
    System.out.println("PID: " + ProcessHandle.current().pid());

    var method = Main.class.getMethod("getPid");
    if(System.getProperty("os.arch").equals("amd64")){
      installAMD64Code(method);
    }
    else{
      installAArch64Code(method);
    }

    System.out.println("PID from syscall: " + getPid());
  }
}
