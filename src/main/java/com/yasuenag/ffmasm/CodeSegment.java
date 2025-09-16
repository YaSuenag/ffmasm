/*
 * Copyright (C) 2022, 2025, Yasumasa Suenaga
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
package com.yasuenag.ffmasm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;

import com.yasuenag.ffmasm.internal.ExecMemory;
import com.yasuenag.ffmasm.internal.linux.LinuxExecMemory;
import com.yasuenag.ffmasm.internal.windows.WindowsExecMemory;


/**
 * Memory segment for executables.
 *
 * @author Yasumasa Suenaga
 */
public class CodeSegment implements AutoCloseable{

  /**
   * Default size of code segment.
   */
  public static final long DEFAULT_CODE_SEGMENT_SIZE = 4096L;

  /**
   * Holder for method information. This is used for perfmap dumping.
   *
   * @param name method name
   * @param address start address of the method
   * @param size method size
   */
  public static record MethodInfo(String name, long address, int size){
    @Override
    public String toString(){
      return String.format("0x%x 0x%x %s", address, size, name);
    }
  }

  private final ExecMemory mem;

  private final MemorySegment addr;

  private final long size;

  private final Set<MethodInfo> methods;

  private long tail;

  private Thread perfMapDumper;

  /**
   * Allocate memory for this code segment with default size (4096 bytes).
   * @throws PlatformException thrown when native function call failed.
   * @throws UnsupportedPlatformException thrown when the platform is not supported.
   */
  public CodeSegment() throws PlatformException, UnsupportedPlatformException{
    this(DEFAULT_CODE_SEGMENT_SIZE);
  }

  /**
   * Allocate memory for this code segment.
   * @param size size of code segment.
   * @throws PlatformException thrown when native function call failed.
   * @throws UnsupportedPlatformException thrown when the platform is not supported.
   */
  public CodeSegment(long size) throws PlatformException, UnsupportedPlatformException{
    String osName = System.getProperty("os.name");
    if(osName.equals("Linux")){
      mem = new LinuxExecMemory();
    }
    else if(osName.startsWith("Windows")){
      mem = new WindowsExecMemory();
    }
    else{
      throw new UnsupportedPlatformException(osName + " is unsupported.");
    }

    this.size = size;
    this.addr = mem.allocate(size);
    this.methods = new HashSet<>();
    this.tail = 0L;
    this.perfMapDumper = null;
  }

  /**
   * Release memory for this code segment.
   */
  @Override
  public void close() throws Exception{
    if(perfMapDumper != null){
      disablePerfMapDumper();
    }
    mem.deallocate(addr, size);
  }

  /**
   * Class to register calling close() as Cleaner action.
   */
  public static class CleanerAction implements Runnable{

    private final CodeSegment seg;

    /**
     * @param seg <code>CodeSegment</code> instance to close.
     */
    public CleanerAction(CodeSegment seg){
      this.seg = seg;
    }

    /**
     * Close associated <code>CodeSegment</code>.
     * All of exceptions are ignored during <code>close()</code> operation.
     */
    @Override
    public void run(){
      try{
        seg.close();
      }
      catch(Exception e){
        // ignore
      }
    }

  }

  /**
   * Get slice of this segment from the tail.
   *
   * @return Slice of this segment from the tail.
   */
  public MemorySegment getTailOfMemorySegment(){
    return addr.asSlice(tail);
  }

  /**
   * Align the tail to 16 bytes
   */
  public void alignTo16Bytes(){
    if((tail & 0xf) > 0){ // not aligned
      tail = (tail + 0x10) & 0xfffffffffffffff0L;
    }
  }

  /**
   * Get the tail of this segment.
   *
   * @return the tail of this segment.
   */
  public long getTail(){
    return tail;
  }

  /**
   * Increment the tail with given size.
   * @param size value to increment
   */
  public void incTail(long size){
    this.tail += size;
  }

  /**
   * Get MemorySegment which relates to this segment.
   *
   * @return MemorySegment of this segment.
   */
  public MemorySegment getAddr(){
    return addr;
  }

  /**
   * Add method info. It will be dumped to perf map as related method of this CodeSegment.
   * @param name Method name
   * @param address Address of the method
   * @param size Size of the method (machine code)
   * @return MethodInfo of the method info.
   * @throws IllegalArgumentException if the address is out of range from this CodeSegment.
   */
  public MethodInfo addMethodInfo(String name, long address, int size){
    if((address < addr.address()) || ((addr.address() + this.size) < (address + size))){
      throw new IllegalArgumentException("Address is out of range from CodeSegment.");
    }
    var methodInfo = new MethodInfo(name, address, size);
    methods.add(methodInfo);
    return methodInfo;
  }

  private void dumpPerfMap(Path path){
    try(var writer = new PrintWriter(Files.newOutputStream(path))){
      methods.stream()
             .sorted(Comparator.comparing(MethodInfo::address))
             .map(MethodInfo::toString)
             .forEachOrdered(writer::println);
    }
    catch(IOException e){
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Enable perf map dumper at shutdown hook for dumping all of functions in this CodeSegment.
   * @param path Path to dumpfile
   */
  public synchronized void enablePerfMapDumper(Path path){
    perfMapDumper = new Thread(() -> dumpPerfMap(path));
    Runtime.getRuntime().addShutdownHook(perfMapDumper);
  }

  /**
   * Disable perf map dumper
   * @throws IllegalStateException if enablePerfMapDumper() is not called before.
   */
  public synchronized void disablePerfMapDumper(){
    if(perfMapDumper == null){
      throw new IllegalStateException("PerfMapDumper is not enabled.");
    }
    Runtime.getRuntime().removeShutdownHook(perfMapDumper);
    perfMapDumper = null;
  }

}
