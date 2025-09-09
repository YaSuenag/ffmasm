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
package com.yasuenag.ffmasm.examples.disas;

import java.lang.foreign.*;
import java.util.*;

import com.yasuenag.ffmasm.*;
import com.yasuenag.ffmasm.amd64.*;

import com.yasuenag.ffmasmtools.disas.Disassembler;


public class Main{

  public static MemorySegment createRDTSC() throws Exception{
    var seg = new CodeSegment();
    return new AsmBuilder.AMD64(seg)
    /* push %rbp      */ .push(Register.RBP)
    /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
    /* rdtsc          */ .rdtsc()
    /* shl $32, %rdx  */ .shl(Register.RDX, (byte)32, OptionalInt.empty())
    /* or %rdx, %rax  */ .orMR(Register.RDX, Register.RAX, OptionalInt.empty())
    /* leave          */ .leave()
    /* ret            */ .ret()
                         .getMemorySegment();
  }

  public static void main(String[] args) throws Exception{
    var rdtsc = createRDTSC();
    Disassembler.dumpToStdout(rdtsc);
  }

}
