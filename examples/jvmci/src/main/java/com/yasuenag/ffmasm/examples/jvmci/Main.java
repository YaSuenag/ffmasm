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

import com.yasuenag.ffmasm.amd64.*;

import com.yasuenag.ffmasmtools.jvmci.amd64.*;


public class Main{

  public static int getPid(){
    throw new UnsupportedOperationException("This method should be overriden by jvmci-adapter in ffmasm");
  }

  public static void main(String[] args) throws Exception{
    System.out.println("PID: " + ProcessHandle.current().pid());

    var method = Main.class.getMethod("getPid");
    new JVMCIAMD64AsmBuilder()
                    .emitPrologue()
/* mov %rax, $39 */ .movImm(Register.RAX, 39) // getpid
/* syscall       */ .syscall()
                    .emitEpilogue()
                    .install(method, 16);

    System.out.println("PID from syscall: " + getPid());
  }
}
