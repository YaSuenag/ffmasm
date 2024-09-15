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
package com.yasuenag.ffmasm.internal.linux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.JitDump;
import com.yasuenag.ffmasm.PlatformException;
import com.yasuenag.ffmasm.UnsupportedPlatformException;


public class PerfJitDump implements JitDump{

  // These constants come from tools/perf/util/jitdump.h in Linux Kernel
  private static final int JITHEADER_MAGIC = 0x4A695444;
  private static final int JITHEADER_VERSION = 1;
  private static final int JIT_CODE_LOAD = 0;
  private static final int JIT_CODE_CLOSE = 3;

  // from bits/time.h
  private static final int CLOCK_MONOTONIC = 1;

  private static final long PAGE_SIZE = 4096;


  private static final MethodHandle mhGetTid;
  private static final StructLayout structTimespec;
  private static final VarHandle hndSec;
  private static final VarHandle hndNSec;
  private static final MethodHandle mhClockGettime;


  private final FileChannel ch;

  private final int fd;

  private final MemorySegment jitdump;

  private long codeIndex;


  static{
    var linker = Linker.nativeLinker();
    var lookup = linker.defaultLookup();
    FunctionDescriptor desc;

    desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
    mhGetTid = linker.downcallHandle(lookup.find("gettid").get(), desc);

    structTimespec = MemoryLayout.structLayout(
                       ValueLayout.JAVA_LONG.withName("tv_sec"),
                       ValueLayout.JAVA_LONG.withName("tv_nsec")
                     );
    hndSec = structTimespec.varHandle(MemoryLayout.PathElement.groupElement("tv_sec"));
    hndNSec = structTimespec.varHandle(MemoryLayout.PathElement.groupElement("tv_nsec"));
    // __CLOCKID_T_TYPE is defined as __S32_TYPE in bits/typesizes.h
    desc = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                 ValueLayout.JAVA_INT,
                                 ValueLayout.ADDRESS);
    mhClockGettime = linker.downcallHandle(lookup.find("clock_gettime").get(), desc);
  }

  private static int getTid(){
    try{
      return (int)mhGetTid.invokeExact();
    }
    catch(Throwable t){
      throw new RuntimeException("Exception happened at gettid() call.", t);
    }
  }

  private static long getTimestamp(){
    try(var arena = Arena.ofConfined()){
      var timespec = arena.allocate(structTimespec);
      int ret = (int)mhClockGettime.invokeExact(CLOCK_MONOTONIC, timespec);

      return ((long)hndSec.get(timespec, 0L) * 1_000_000_000) + (long)hndNSec.get(timespec, 0L);
    }
    catch(Throwable t){
      throw new RuntimeException("Exception happened at gettid() call.", t);
    }
  }

  // from tools/perf/util/jitdump.h in Linux Kernel
  //   struct jitheader {
  //           uint32_t magic;         /* characters "jItD" */
  //           uint32_t version;       /* header version */
  //           uint32_t total_size;    /* total size of header */
  //           uint32_t elf_mach;      /* elf mach target */
  //           uint32_t pad1;          /* reserved */
  //           uint32_t pid;           /* JIT process id */
  //           uint64_t timestamp;     /* timestamp */
  //           uint64_t flags;         /* flags */
  //   };
  protected void writeHeader() throws IOException{
    final int headerSize = 40; // sizeof(struct jitheader)
    var buf = ByteBuffer.allocate(headerSize).order(ByteOrder.nativeOrder());

    short elfMach = -1;
    try(var ch = FileChannel.open(Path.of("/proc/self/exe"), StandardOpenOption.READ)){
      var buf2 = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder());
      ch.read(buf2, 18); // id (16 bytes) + e_type (2 bytes)
      buf2.flip();
      elfMach = buf2.getShort();
    }

    // magic
    buf.putInt(JITHEADER_MAGIC);
    // version
    buf.putInt(JITHEADER_VERSION);
    // total_size
    buf.putInt(headerSize);
    // elf_mach
    buf.putInt(elfMach);
    // pad1
    buf.putInt(0);
    // pid
    buf.putInt((int)ProcessHandle.current().pid());
    // timestamp
    buf.putLong(getTimestamp());
    // flags
    buf.putLong(0L);

    buf.flip();
    ch.write(buf);
  }

  public PerfJitDump(Path dir) throws UnsupportedPlatformException, PlatformException, IOException{
    // According to jit_detect() in tools/perf/util/jitdump.c in Linux Kernel,
    // dump file should be named "jit-<pid>.dump".
    var jitdumpPath = dir.resolve(String.format("jit-%d.dump", ProcessHandle.current().pid()));
    ch = FileChannel.open(jitdumpPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);

    // find FD of jitdump file
    int fdWork = -1;
    try(var links = Files.newDirectoryStream(Path.of("/proc/self/fd"))){
      for(var link : links){
        try{
          if(Files.isSameFile(jitdumpPath, Files.readSymbolicLink(link))){
            fdWork = Integer.parseInt(link.getFileName().toString());
            break;
          }
        }
        catch(NoSuchFileException e){
          // ignore
        }
      }
    }
    if(fdWork == -1){
      throw new IllegalStateException("FD of jitdump is not found.");
    }
    fd = fdWork;

    // from tools/perf/jvmti/jvmti_agent.c in Linux Kernel
    jitdump = LinuxExecMemory.mmap(MemorySegment.NULL, PAGE_SIZE, LinuxExecMemory.PROT_READ | LinuxExecMemory.PROT_EXEC, LinuxExecMemory.MAP_PRIVATE, fd, 0);
    codeIndex = 0;

    writeHeader();
  }

  // from tools/perf/util/jitdump.h in Linux Kernel
  //   struct jr_prefix {
  //           uint32_t id;
  //           uint32_t total_size;
  //           uint64_t timestamp;
  //   };
  //
  //   struct jr_code_load {
  //           struct jr_prefix p;
  //
  //           uint32_t pid;
  //           uint32_t tid;
  //           uint64_t vma;
  //           uint64_t code_addr;
  //           uint64_t code_size;
  //           uint64_t code_index;
  //   };
  @Override
  public synchronized void writeFunction(CodeSegment.MethodInfo method){
    // sizeof(jr_code_load) == 56, null char of method name should be included.
    final int totalSize = 56 + method.name().length() + 1 + method.size();
    var buf = ByteBuffer.allocate(totalSize).order(ByteOrder.nativeOrder());

    // id
    buf.putInt(JIT_CODE_LOAD);
    // total_size
    buf.putInt(totalSize);
    // timestamp
    buf.putLong(getTimestamp());
    // pid
    buf.putInt((int)ProcessHandle.current().pid());
    // tid
    buf.putInt(getTid());
    // vma
    buf.putLong(method.address());
    // code_addr
    buf.putLong(method.address());
    // code_size
    buf.putLong(method.size());
    // code_index
    buf.putLong(codeIndex++);

    // method name
    buf.put(method.name().getBytes());
    buf.put((byte)0); // NUL

    // code
    var seg = MemorySegment.ofAddress(method.address()).reinterpret(method.size());
    buf.put(seg.toArray(ValueLayout.JAVA_BYTE));

    buf.flip();
    try{
      ch.write(buf);
    }
    catch(IOException e){
      throw new UncheckedIOException(e);
    }
  }

  // from tools/perf/util/jitdump.h in Linux Kernel
  //   struct jr_prefix {
  //           uint32_t id;
  //           uint32_t total_size;
  //           uint64_t timestamp;
  //   };
  //
  //   struct jr_code_close {
  //           struct jr_prefix p;
  //   };
  @Override
  public synchronized void close() throws Exception{
    final int headerSize = 16; // sizeof(jr_code_close)
    var buf = ByteBuffer.allocate(headerSize).order(ByteOrder.nativeOrder());

    // id
    buf.putInt(JIT_CODE_CLOSE);
    // total_size
    buf.putInt(16); // sizeof(jr_code_close)
    // timestamp
    buf.putLong(getTimestamp());

    buf.flip();
    ch.write(buf);

    LinuxExecMemory.munmap(jitdump, PAGE_SIZE);
    ch.close();
  }

}
