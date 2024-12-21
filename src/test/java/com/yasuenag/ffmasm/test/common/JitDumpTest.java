/*
 * Copyright (C) 2024, Yasumasa Suenaga
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
package com.yasuenag.ffmasm.test.common;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.JitDump;
import com.yasuenag.ffmasm.UnsupportedPlatformException;
import com.yasuenag.ffmasm.internal.linux.PerfJitDump;


public class JitDumpTest{

  private File getDumpFileInstance(){
    return new File(String.format("/tmp/jit-%d.dump", ProcessHandle.current().pid()));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  public void testGetInstanceOnLinux(){
    try(var jitdump = JitDump.getInstance(Path.of("/tmp"))){
      Assertions.assertEquals(PerfJitDump.class, jitdump.getClass());
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    var dumpfile = getDumpFileInstance();
    Assertions.assertTrue(dumpfile.exists());

    // cleanup
    dumpfile.delete();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  public void testMemoryPermissions(){
    var dumpfile = getDumpFileInstance();
    String line = null;

    try(var jitdump = JitDump.getInstance(Path.of("/tmp"))){
      try(var lines = Files.lines(Path.of("/proc/self/maps"))){
        line = lines.filter(s -> s.endsWith(dumpfile.getPath()))
                    .findFirst()
                    .get();
      }
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    Assertions.assertEquals("r-xp", line.split("\\s+")[1]);

    // cleanup
    dumpfile.delete();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  public void testJitDumpHeader(){
    var dumpfile = getDumpFileInstance();

    try(var jitdump = JitDump.getInstance(Path.of("/tmp"))){
      // Create jitdump file. Do nothing at here.
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    try(var ch = FileChannel.open(dumpfile.toPath(), StandardOpenOption.READ)){
      final int headerSize = 40; // sizeof(struct jitheader)
      final int closeHeaderSize = 16; // sizeof(jr_code_close)
      Assertions.assertEquals(headerSize + closeHeaderSize, ch.size());

      var buf = ByteBuffer.allocate(headerSize)
                          .order(ByteOrder.nativeOrder());
      ch.read(buf);
      buf.flip();

      // magic
      Assertions.assertEquals(0x4A695444, buf.getInt());
      // version
      Assertions.assertEquals(1, buf.getInt());
      // total_size
      Assertions.assertEquals(headerSize, buf.getInt());
      // elf_mach (skip)
      buf.position(buf.position() + 4);
      // pad1
      Assertions.assertEquals(0, buf.getInt());
      // pid
      Assertions.assertEquals((int)ProcessHandle.current().pid(), buf.getInt());
      // timestamp (skip)
      buf.position(buf.position() + 8);
      // flag
      Assertions.assertEquals(0L, buf.getLong());

      buf = ByteBuffer.allocate(closeHeaderSize)
                      .order(ByteOrder.nativeOrder());
      ch.read(buf);
      buf.flip();

      // id
      Assertions.assertEquals(3, buf.getInt());
      // total_size
      Assertions.assertEquals(closeHeaderSize, buf.getInt());
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    // cleanup
    dumpfile.delete();
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  public void testJitDumpFunctionEntry(){
    var dumpfile = getDumpFileInstance();
    var info = new CodeSegment.MethodInfo("func", 0x1234, 0);

    try(var jitdump = JitDump.getInstance(Path.of("/tmp"))){
      jitdump.writeFunction(info);
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    try(var ch = FileChannel.open(dumpfile.toPath(), StandardOpenOption.READ)){
      final int fileHeaderSize = 40; // sizeof(struct jitheader)
      final int functionEntrySize = 56 + info.name().length() + 1 + info.size(); // sizeof(jr_code_load) == 56, null char of method name should be included.

      var buf = ByteBuffer.allocate(functionEntrySize)
                          .order(ByteOrder.nativeOrder());

      // Skip file header
      ch.position(fileHeaderSize);

      // Read function entry
      ch.read(buf);
      buf.flip();

      // id
      Assertions.assertEquals(0, buf.getInt());
      // total_size
      Assertions.assertEquals(functionEntrySize, buf.getInt());
      // timestamp (skip)
      buf.position(buf.position() + 8);
      // pid
      Assertions.assertEquals((int)ProcessHandle.current().pid(), buf.getInt());
      // tid (skip)
      buf.position(buf.position() + 4);
      // vma
      Assertions.assertEquals(0x1234L, buf.getLong());
      // code_addr
      Assertions.assertEquals(0x1234L, buf.getLong());
      // code_size
      Assertions.assertEquals(0L, buf.getLong());
      // code_index
      Assertions.assertEquals(0L, buf.getLong());
      // function name
      byte[] nameInBytes = new byte[info.name().length()];
      buf.get(nameInBytes);
      Assertions.assertEquals("func", new String(nameInBytes));
      Assertions.assertEquals((byte)0, buf.get());
    }
    catch(Exception e){
      Assertions.fail(e);
    }

    // cleanup
    dumpfile.delete();
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  public void testGetInstanceOnWindows(){
    Assertions.assertThrows(UnsupportedPlatformException.class, () -> JitDump.getInstance(Path.of("C:\\tmp")));
  }

}
