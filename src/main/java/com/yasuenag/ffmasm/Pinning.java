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
package com.yasuenag.ffmasm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import com.yasuenag.ffmasm.CodeSegment;
import com.yasuenag.ffmasm.amd64.AMD64AsmBuilder;
import com.yasuenag.ffmasm.amd64.Register;


/**
 * Pinning implementation
 * !!CAUTION!! Pinning supports Linux only!
 *
 * This class provides features of GetPrimitiveArrayCritical() / ReleasePrimitiveArrayCritical()
 * JNI functions. It means the change on pinned MemorySegment is propagated to the original array.
 * You need to get instance of Pinning from getInstance() method.
 *
 * This class has native private method (jniWrapper()) to redirect to these JNI functions.
 * However we do not have the implementation, so we have to create it with ffmasm.
 *
 * We can get JNIEnv* via GetEnv() JNI function. However we have to get JavaVM* to call it.
 * So we call JNI_GetCreatedJavaVMs() which is exported function from JVM at first,
 * then we get a pointer of GetEnv() from JavaVM function table. Then we can get pointers of
 * GetPrimitiveArrayCritical() / ReleasePrimitiveArrayCritical() from JNI function table.
 *
 * To register native functions dynamically, we need to call RegisterNatives() JNI function,
 * we need to know jclass of the holder class to do it. We can use FindClass() JNI function
 * normally for this purpose, but we cannot do that because FindClass() checks caller, and
 * it attempts to find only from system class loader when the caller is MethodHandle.
 * (See JavaThread::security_get_caller_class() in HotSpot)
 * ffmasm would be loaded from application class loader, so we need to get jclass by another
 * method.
 *
 * We can get jclasses of all of loaded classes via GetLoadedClasses() JVMTI function.
 * Get jvmtiEnv* with same procedure with JNIEnv*, and get a pointer of GetLoadedClasses()
 * from the function table. However lifecycle of jclasses is JNI local, it means they will be
 * invalid when function call (via ffmasm) are ended (return to the app). So we need to create
 * stub code to call GetLoadedClasses() and to find out a target jclass at once.
 *
 * Stub code calls GetLoadedClasses() at first, then passes jclasses and number of them to
 * callback function (Setup::callback). Callback function iterates jclasses, and compare
 * class signature from GetClassSignature() JVMTI function with strcmp(). The reason of
 * using strcmp() is that we do not need to create new Java instance for String, and don't
 * need to reinterpret MemorySegment of class signature with its length. We would release
 * class signature with Deallocate() JVMTI function of course when it can be dispose.
 *
 * When we find a target jclass at the callback, we register wrapper function of native
 * method via RegisterNatives() JVMTI function. Then callback would be finished.
 *
 * Finally, stub code would release memory of jclasses, then all of register processes
 * are done!
 *
 * @author Yasumasa Suenaga
 */
public final class Pinning{

  /* from jni.h */
  private static final int JNI_VERSION_21 = 0x00150000;
  private static final int JNI_OK         = 0;
  /* from jvmti.h */
  private static final int JVMTI_VERSION_21 = 0x30150000;
  private static final int JVMTI_ERROR_NONE = 0;

  /* indices / positions are from API doc */
  private static final int JavaVM_GetEnv_INDEX = 6;

  private static final int JNI_RegisterNatives_INDEX = 215;
  private static final int JNI_GetPrimitiveArrayCritical_INDEX = 222;
  private static final int JNI_ReleasePrimitiveArrayCritical_INDEX = 223;

  private static final int JVMTI_Deallocate_POSITION = 47;
  private static final int JVMTI_GetClassSignature_POSITION = 48;
  private static final int JVMTI_GetLoadedClasses_POSITION = 78;

  /* Address size shouldn't be changed in run time. */
  private static final long ADDRESS_SIZE = ValueLayout.ADDRESS.byteSize();

  private final static MemorySegment jvmtiEnv;
  private final static MemorySegment jniEnv;

  private final static MemorySegment DeallocateAddr;
  private final static MethodHandle GetClassSignature;
  private final static MethodHandle strcmp;

  private static final Arena arena;
  private static final CodeSegment seg;

  private static MethodHandle GetEnv;
  private static Pinning instance;

  private final Map<MemorySegment, Object> pinnedMap;

  private MemorySegment addrGetPrimitiveArrayCritical;

  private MemorySegment addrReleasePrimitiveArrayCritical;

  private static MemorySegment jniWrapperImpl;
  private native long jniWrapper(long funcAddr, Object array, long arg2, long arg3);

  private static SymbolLookup getJvmLookup() throws UnsupportedPlatformException{
    String osName = System.getProperty("os.name");
    Path jvmPath;
    if(osName.equals("Linux")){
      jvmPath = Path.of(System.getProperty("java.home"), "lib", "server", "libjvm.so");
    }
    else{
      throw new UnsupportedPlatformException(osName + " is unsupported.");
    }

    return SymbolLookup.libraryLookup(jvmPath, arena);
  }

  private static MemorySegment getVM() throws Throwable{
    var JNI_GetCreatedJavaVMs = Linker.nativeLinker()
                                      .downcallHandle(getJvmLookup().find("JNI_GetCreatedJavaVMs").get(),
                                                      FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                            ValueLayout.ADDRESS,
                                                                            ValueLayout.JAVA_INT,
                                                                            ValueLayout.ADDRESS));
    var vms = arena.allocateArray(ValueLayout.ADDRESS, 1);
    int result = (int)JNI_GetCreatedJavaVMs.invoke(vms, 1, MemorySegment.NULL);
    if(result != JNI_OK){
      throw new RuntimeException(STR."JNI_GetCreatedJavaVMs() returns \{result}");
    }
    return vms.getAtIndex(ValueLayout.ADDRESS, 0)
              .reinterpret(ADDRESS_SIZE);
  }

  private static MemorySegment getEnvInternal(MemorySegment vm, int version) throws Throwable{
    if(GetEnv == null){
      var vmFuncs = vm.getAtIndex(ValueLayout.ADDRESS, 0)
                      .reinterpret(ADDRESS_SIZE * (JavaVM_GetEnv_INDEX + 1));
      GetEnv = Linker.nativeLinker()
                     .downcallHandle(vmFuncs.getAtIndex(ValueLayout.ADDRESS, JavaVM_GetEnv_INDEX),
                                     FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                           ValueLayout.ADDRESS,
                                                           ValueLayout.ADDRESS,
                                                           ValueLayout.JAVA_INT));
    }
    var env = arena.allocateArray(ValueLayout.ADDRESS, 1);
    int result = (int)GetEnv.invoke(vm, env, version);
    if(result != JNI_OK){
      throw new RuntimeException(STR."GetEnv() returns \{result}");
    }
    return env.getAtIndex(ValueLayout.ADDRESS, 0)
              .reinterpret(ADDRESS_SIZE);
  }

  private static MemorySegment getJNIEnv(MemorySegment vm) throws Throwable{
    return getEnvInternal(vm, JNI_VERSION_21);
  }

  private static MemorySegment getJvmtiEnv(MemorySegment vm) throws Throwable{
    return getEnvInternal(vm, JVMTI_VERSION_21);
  }

  private static void createWrapper() throws UnsupportedPlatformException{
    var desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG,  // return value (pinned address)
                                     ValueLayout.ADDRESS,    // arg1 (JNIEnv *)
                                     ValueLayout.ADDRESS,    // arg2 (jclass)
                                     ValueLayout.ADDRESS,    // arg3 (funcAddr)
                                     ValueLayout.ADDRESS,    // arg4 (array)
                                     ValueLayout.JAVA_LONG,  // arg5 (arg2)
                                     ValueLayout.JAVA_LONG); // arg6 (arg3)
    jniWrapperImpl = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
               /* push %rbp      */ .push(Register.RBP)
               /* mov %rsp, %rbp */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
               /* mov %rdx, %r10 */ .movMR(Register.RDX, Register.R10, OptionalInt.empty()) // move arg3 (arg1 in Java)  to r10
               /* mov %rcx, %rsi */ .movMR(Register.RCX, Register.RSI, OptionalInt.empty()) // move arg4 (arg2 in Java)  to arg2
               /* mov %r8,  %rdx */ .movMR(Register.R8,  Register.RDX, OptionalInt.empty()) // move arg5 (arg3 in Java)  to arg3
               /* mov %r9,  %rcx */ .movMR(Register.R9,  Register.RCX, OptionalInt.empty()) // move arg6 (arg4 in Java)  to arg4
               /* call %r10      */ .call(Register.R10)
               /* leave          */ .leave()
               /* ret            */ .ret()
                                    .getMemorySegment();
  }

  static{
    arena = Arena.ofAuto();
    try{
      seg = new CodeSegment();
      var segForCleaner = seg;
      Cleaner.create()
             .register(Pinning.class, () -> {
               try{
                 segForCleaner.close();
               }
               catch(Exception e){
                 // ignore
               }
             });

      var vm = getVM();
      jniEnv = getJNIEnv(vm); // JNIEnv*
      jvmtiEnv = getJvmtiEnv(vm); // jvmtiEnv*

      createWrapper();
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }

    // Get JVMTI function table
    var jvmtiFuncs = jvmtiEnv.get(ValueLayout.ADDRESS, 0) // jvmtiEnv = jvmtiInterface_1_*
                             .reinterpret(ADDRESS_SIZE * JVMTI_GetClassSignature_POSITION);
    var linker = Linker.nativeLinker();
    DeallocateAddr = jvmtiFuncs.getAtIndex(ValueLayout.ADDRESS, JVMTI_Deallocate_POSITION - 1);
    GetClassSignature = linker.downcallHandle(jvmtiFuncs.getAtIndex(ValueLayout.ADDRESS, JVMTI_GetClassSignature_POSITION - 1),
                                              FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.ADDRESS,
                                                                    ValueLayout.ADDRESS));

    strcmp = linker.downcallHandle(linker.defaultLookup()
                                         .find("strcmp")
                                         .get(),
                                   FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                         ValueLayout.ADDRESS,
                                                         ValueLayout.ADDRESS));
  }

  private static class Setup{

    private static void registerNatives(MemorySegment klass) throws Throwable{
      var jniFuncs = jniEnv.get(ValueLayout.ADDRESS, 0) // JNIEnv = JNINativeInterface_*
                           .reinterpret(ADDRESS_SIZE * (JNI_RegisterNatives_INDEX + 1));
      var regNatives = Linker.nativeLinker()
                             .downcallHandle(jniFuncs.getAtIndex(ValueLayout.ADDRESS, JNI_RegisterNatives_INDEX),
                                             FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                   ValueLayout.ADDRESS,
                                                                   ValueLayout.ADDRESS,
                                                                   ValueLayout.ADDRESS,
                                                                   ValueLayout.JAVA_INT));

      var layout = MemoryLayout.sequenceLayout(1, MemoryLayout.structLayout(
                                                    ValueLayout.ADDRESS.withName("name"),
                                                    ValueLayout.ADDRESS.withName("signature"),
                                                    ValueLayout.ADDRESS.withName("fnPtr")));
      var nativeMethods = arena.allocate(layout);
      var idx0PathElement = MemoryLayout.PathElement.sequenceElement(0);
      layout.varHandle(idx0PathElement, MemoryLayout.PathElement.groupElement("name"))
            .set(nativeMethods, arena.allocateUtf8String("jniWrapper"));
      layout.varHandle(idx0PathElement, MemoryLayout.PathElement.groupElement("signature"))
            .set(nativeMethods, arena.allocateUtf8String("(JLjava/lang/Object;JJ)J"));
      layout.varHandle(idx0PathElement, MemoryLayout.PathElement.groupElement("fnPtr"))
            .set(nativeMethods, jniWrapperImpl);

      int result = (int)regNatives.invoke(jniEnv, klass, nativeMethods, 1);
      if(result != JNI_OK){
        throw new RuntimeException(STR."RegisterNatives() returns \{result}");
      }
    }

    public static void callback(MemorySegment classes, int class_count, int resultGetLoadedClasses){
      if(resultGetLoadedClasses != JVMTI_ERROR_NONE){
        throw new RuntimeException(STR."GetLoadedClasses() returns \{resultGetLoadedClasses}");
      }

      var Deallocate = Linker.nativeLinker()
                             .downcallHandle(DeallocateAddr,
                                             FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                   ValueLayout.ADDRESS,
                                                                   ValueLayout.ADDRESS));
      classes = classes.reinterpret(ADDRESS_SIZE * class_count);
      var sigPtr = arena.allocate(ValueLayout.ADDRESS);
      var sigWithSlash = Pinning.class.getCanonicalName().replace('.', '/');
      var targetSig = arena.allocateUtf8String(STR."L\{sigWithSlash};");
      try{
        for(int idx = 0; idx < class_count; idx++){
          var klass = classes.getAtIndex(ValueLayout.ADDRESS, idx);
          int result = (int)GetClassSignature.invoke(jvmtiEnv, klass, sigPtr, MemorySegment.NULL);
          if(result != JVMTI_ERROR_NONE){
            throw new RuntimeException(STR."GetClassSignature() returns \{result}");
          }

          var sig = sigPtr.get(ValueLayout.ADDRESS, 0);
          int cmp = (int)strcmp.invoke(sig, targetSig);
          Deallocate.invoke(jvmtiEnv, sig);
          if(cmp == 0){
            registerNatives(klass);
            return;
          }
        }
      }
      catch(Throwable t){
        throw new RuntimeException(t);
      }
    }

    public static void setupNatives() throws Throwable{
      // Get JVMTI function table
      var jvmtiFuncs = jvmtiEnv.get(ValueLayout.ADDRESS, 0) // jvmtiEnv = jvmtiInterface_1_*
                               .reinterpret(ADDRESS_SIZE * JVMTI_GetLoadedClasses_POSITION);
      var linker = Linker.nativeLinker();
      var GetLoadedClassesAddr = jvmtiFuncs.getAtIndex(ValueLayout.ADDRESS, JVMTI_GetLoadedClasses_POSITION - 1);
      var hndCallback = MethodHandles.lookup()
                                     .findStatic(Setup.class, "callback",
                                                 MethodType.methodType(void.class,
                                                                       MemorySegment.class,
                                                                       int.class,
                                                                       int.class));
      var cbStub = linker.upcallStub(hndCallback,
                                     FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
                                                               ValueLayout.JAVA_INT,
                                                               ValueLayout.JAVA_INT),
                                     arena);

      var desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,  // arg1 (jvmtiEnv*)
                                           ValueLayout.ADDRESS,  // arg2 (Addess of GetLoadedClasses())
                                           ValueLayout.ADDRESS,  // arg3 (Address of callback)
                                           ValueLayout.ADDRESS); // arg4 (Address of Deallocate())
      var setupFunc = AMD64AsmBuilder.create(AMD64AsmBuilder.class, seg, desc)
      // prologue
             /* push %rbp         */ .push(Register.RBP)
             /* mov %rsp, %rbp    */ .movMR(Register.RSP, Register.RBP, OptionalInt.empty())
      // evacutate
             /* mov %rsi, %r10    */ .movMR(Register.RSI, Register.R10, OptionalInt.empty()) // GetLoadedClasses
             /* push %r12         */ .push(Register.R12)
             /* mov %rdx, %r12    */ .movMR(Register.RDX, Register.R12, OptionalInt.empty()) // callback
             /* push %rdi         */ .push(Register.RDI) // jvmtiEnv*
             /* push %rcx         */ .push(Register.RCX) // Deallocate
      // call GetLoadedClasses()
             /* sub $16, %rsp     */ .sub(Register.RSP, 16, OptionalInt.empty())
             /* lea 8(%rsp), %rdx */ .lea(Register.RDX, Register.RSP, 8) // classes
             /* mov %rsp, %rsi    */ .movMR(Register.RSP, Register.RSI, OptionalInt.empty()) // count
             /* sub $8, %rsp      */ .sub(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
             /* call %r10         */ .call(Register.R10)
             /* add $8, %rsp      */ .add(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
      // call callback(jclass *classes, jint class_count)
             /* pop %rsi          */ .pop(Register.RSI, OptionalInt.empty())
             /* mov (%rsp), %rdi  */ .movRM(Register.RDI, Register.RSP, OptionalInt.of(0))
             /* mov %rax, %rdx    */ .movMR(Register.RAX, Register.RDX, OptionalInt.empty()) // result of GetLoadedClasses()
             /* call %r12         */ .call(Register.R12)
      // call Deallocate()
             /* pop %rsi          */ .pop(Register.RSI, OptionalInt.empty()) // classes
             /* pop %r10          */ .pop(Register.R10, OptionalInt.empty()) // Deallocate
             /* pop %rdi          */ .pop(Register.RDI, OptionalInt.empty()) // jvmtiEnv*
             /* sub $8, %rsp      */ .sub(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
             /* call %r10         */ .call(Register.R10)
             /* add $8, %rsp      */ .add(Register.RSP, 8, OptionalInt.empty()) // for stack alignment
      // epilogue
             /* pop %r12          */ .pop(Register.R12, OptionalInt.empty()) // classes
             /* leave             */ .leave()
             /* ret               */ .ret()
                                     .build();

      setupFunc.invoke(jvmtiEnv, GetLoadedClassesAddr, cbStub, DeallocateAddr);
    }

  }

  private Pinning() throws Throwable{
    pinnedMap = new HashMap<>();

    // Get JNI function table
    var jniFuncs = jniEnv.get(ValueLayout.ADDRESS, 0) // JNIEnv = JNINativeInterface_*
                         .reinterpret(ADDRESS_SIZE * (JNI_ReleasePrimitiveArrayCritical_INDEX + 1));

    // Get addresses of JNI critical array functions
    addrGetPrimitiveArrayCritical = jniFuncs.getAtIndex(ValueLayout.ADDRESS, JNI_GetPrimitiveArrayCritical_INDEX);
    addrReleasePrimitiveArrayCritical = jniFuncs.getAtIndex(ValueLayout.ADDRESS, JNI_ReleasePrimitiveArrayCritical_INDEX);

    // Setup native function
    Setup.setupNatives();
  }

  /**
   * Pin array object.
   * Note that pinned long time, it might causes of preventing JVM behavior (e.g. GC)
   * See GetPrimitiveArrayCritical() JNI document for details.
   *
   * @param obj Primitive array to pin.
   * @return MemorySegment of pinned array
   * @throws IllegalArgumentException if obj is not an array.
   */
  public MemorySegment pin(Object obj){
    if(!obj.getClass().isArray()){
      throw new IllegalArgumentException("obj should be array type");
    }

    long rawAddr = jniWrapper(addrGetPrimitiveArrayCritical.address(), obj, 0L, 0L);
    if(rawAddr == 0L){
      throw new RuntimeException("GetPrimitiveArrayCritical() returns NULL");
    }

    var addr = MemorySegment.ofAddress(rawAddr);
    pinnedMap.put(addr, obj);
    return addr;
  }

  /**
   * Unpin array object.
   *
   * @param addr Pinned MemorySegment
   * @throws IllegalArgumentException if addr is not a pinned MemorySegment
   */
  public void unpin(MemorySegment addr){
    Object obj = pinnedMap.get(addr);
    if(obj == null){
      throw new IllegalArgumentException(STR."Address 0x\{Long.toHexString(addr.address())} is not pinned");
    }
    jniWrapper(addrReleasePrimitiveArrayCritical.address(), obj, addr.address(), 0L);
    pinnedMap.remove(addr);
  }

  /**
   * Get instance of Pinning.
   *
   * @return Pinning insntance
   * @throws RuntimeException if an error happens at initialization.
   */
  public static Pinning getInstance(){
    if(instance == null){
      try{
        instance = new Pinning();
      }
      catch(Throwable t){
        throw new RuntimeException(t);
      }
    }
    return instance;
  }

}
