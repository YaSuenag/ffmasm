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

import java.lang.invoke.MethodHandle;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.util.Map;

import com.yasuenag.ffmasm.internal.JniEnv;
import com.yasuenag.ffmasm.internal.JvmtiEnv;
import com.yasuenag.ffmasm.internal.amd64.AMD64NativeRegister;


/**
 * Dynamic native method register.
 *
 * This class binds memory address to arbitrary native methods dynamically.
 * Note that 1st and 2nd arguments of JNI function are reserved.
 * (1st is <code>JNIEnv*</code>, 2nd is <code>jclass</code> or <code>jobject</code>)
 * You need to get instance of this class from {@link #create(Class<?>)}.
 *
 * We can get <code>JNIEnv*</code> via <code>GetEnv()</code> JNI function.
 * However we have to get <code>JavaVM*</code> to call it.
 * So we call <code>JNI_GetCreatedJavaVMs()</code> which is exported function
 * from JVM at first, then we get a pointer of <code>GetEnv()</code> from
 * <code>JavaVM</code> function table.
 *
 * To register native functions dynamically, we need to call
 * <code>RegisterNatives()</code> JNI function, we need to know jclass of
 * the holder class to do it.
 * We can use <code>FindClass()</code> JNI function normally for this purpose,
 * but we cannot do that because <code>FindClass()</code> checks caller, and
 * it attempts to find only from system class loader when the caller is
 * <code>MethodHandle</code>.
 * (See <code>JavaThread::security_get_caller_class()</code> in HotSpot)
 * ffmasm would be loaded from application class loader, so we need to get
 * <code>jclass</code> by another method.
 *
 * We can get <code>jclass</code>es of all of loaded classes via
 * <code>GetLoadedClasses()</code> JVMTI function. Get <code>jvmtiEnv*</code>
 * with same procedure with <code>JNIEnv*</code>, and get a pointer of
 * <code>GetLoadedClasses()</code> from the function table.
 * However lifecycle of <code>jclass</code>es is JNI local, it means they will
 * be invalid when function call (via ffmasm) are ended (return to the app).
 * So we need to create stub code to call <code>GetLoadedClasses()</code> and
 * to find out a target <code>jclass</code> at once.
 *
 * Stub code calls <code>GetLoadedClasses()</code> at first, then passes
 * <code>jclass</code>es and number of them to callback function.
 * Callback function iterates <code>jclass</code>es, and compare class signature
 * from <code>GetClassSignature()</code> JVMTI function with
 * <code>strcmp()</code>. The reason of using it is that we do not need to
 * create new Java instance for <code>String</code>, and don't need to
 * reinterpret <code>MemorySegment</code> of class signature with its length.
 * We would release class signature with <code>Deallocate()</code> JVMTI
 * function of course when it can be dispose.
 *
 * When we find a target <code>jclass</code> at the callback, we register
 * executable memory of native method via <code>RegisterNatives()</code> JNI
 * function. Then callback would be finished.
 *
 * Finally, stub code would release memory of <code>jclass</code>es, then
 * all of register processes are done!
 *
 * @author Yasumasa Suenaga
 */
public abstract class NativeRegister{

  private static final MemoryLayout layoutCallbackParam;
  private static final MemoryLayout.PathElement peClassSig;
  private static final MemoryLayout.PathElement peMethods;
  private static final MemoryLayout.PathElement peNumMethods;

  private static final MethodHandle strcmp;

  private final Class<?> klass;

  static{
    layoutCallbackParam = MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("class_sig"),
                                                    ValueLayout.ADDRESS.withName("methods"),
                                                    ValueLayout.JAVA_INT.withName("num_methods"));
    peClassSig = MemoryLayout.PathElement.groupElement("class_sig");
    peMethods = MemoryLayout.PathElement.groupElement("methods");
    peNumMethods = MemoryLayout.PathElement.groupElement("num_methods");

    var linker = Linker.nativeLinker();
    strcmp = linker.downcallHandle(linker.defaultLookup()
                                         .find("strcmp")
                                         .get(),
                                   FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                         ValueLayout.ADDRESS,
                                                         ValueLayout.ADDRESS));
  }

  protected static void callback(MemorySegment classes, int class_count, int resultGetLoadedClasses, MemorySegment callbackParam){
    if(resultGetLoadedClasses != JvmtiEnv.JVMTI_ERROR_NONE){
      throw new RuntimeException(STR."GetLoadedClasses() returns \{resultGetLoadedClasses}");
    }

    classes = classes.reinterpret(ValueLayout.ADDRESS.byteSize() * class_count);
    callbackParam = callbackParam.reinterpret(layoutCallbackParam.byteSize());
    try(var arena = Arena.ofConfined()){
      var sigPtr = arena.allocate(ValueLayout.ADDRESS);
      var targetSig = (MemorySegment)layoutCallbackParam.varHandle(peClassSig)
                                                        .get(callbackParam);
      var jvmtiEnv = JvmtiEnv.getInstance();
      for(int idx = 0; idx < class_count; idx++){
        var clazz = classes.getAtIndex(ValueLayout.ADDRESS, idx);
        int result = jvmtiEnv.getClassSignature(clazz, sigPtr, MemorySegment.NULL);
        if(result != JvmtiEnv.JVMTI_ERROR_NONE){
          throw new RuntimeException(STR."GetClassSignature() returns \{result}");
        }

        var sig = sigPtr.get(ValueLayout.ADDRESS, 0);
        int cmp = (int)strcmp.invoke(sig, targetSig);
        jvmtiEnv.deallocate(sig);
        if(cmp == 0){
          MemorySegment methods = (MemorySegment)layoutCallbackParam.varHandle(peMethods)
                                                                    .get(callbackParam);
          int nMethods = (int)layoutCallbackParam.varHandle(peNumMethods)
                                                 .get(callbackParam);
          result = JniEnv.getInstance()
                         .registerNatives(clazz, methods, nMethods);
          if(result != JniEnv.JNI_OK){
            throw new RuntimeException(STR."RegisterNatives() returns \{result}");
          }
          return;
        }
      }
    }
    catch(Throwable t){
      throw new RuntimeException(t);
    }

  }

  protected NativeRegister(Class<?> klass){
    this.klass = klass;
  }

  private String convertToJNITypeSignature(Class<?> clazz){
    String signature = clazz.isArray() ? "[" : "";
    if(clazz == boolean.class){
      signature += "Z";
    }
    else if(clazz == byte.class){
      signature += "B";
    }
    else if(clazz == char.class){
      signature += "C";
    }
    else if(clazz == short.class){
      signature += "S";
    }
    else if(clazz == int.class){
      signature += "I";
    }
    else if(clazz == long.class){
      signature += "J";
    }
    else if(clazz == float.class){
      signature += "F";
    }
    else if(clazz == double.class){
      signature += "D";
    }
    else if(clazz == void.class){
      signature += "V";
    }
    else{
      signature += "L" + clazz.getCanonicalName().replace('.', '/') + ";";
    };
    return signature;
  }

  private String getJNIMethodSignature(Method method){
    String signature = "(";
    for(var argType : method.getParameterTypes()){
      signature += convertToJNITypeSignature(argType);
    }
    signature += ")" + convertToJNITypeSignature(method.getReturnType());

    return signature;
  }

  protected abstract void callRegisterStub(MemorySegment callbackParam) throws Throwable;

  /**
   * Register executable memory to native methods.
   *
   * @param methods Map of <code>Method</code> and <code>MemorySegment</code>. You have to set <code>Method</code> object what you want to set to key of map.
   * @throws Throwable when some error happened.
   */
  public void registerNatives(Map<Method, MemorySegment> methods) throws Throwable{
    var classSig = "L" + klass.getCanonicalName().replace('.', '/') + ";";
    var layout = MemoryLayout.sequenceLayout(methods.size(), JniEnv.layoutJNINativeMethod);

    try(var arena = Arena.ofConfined()){
      var nativeMethods = arena.allocate(layout);
      int idx = 0;
      for(var es : methods.entrySet()){
        Method method = es.getKey();
        MemorySegment mem = es.getValue();
        var idxPath = MemoryLayout.PathElement.sequenceElement(idx);

        layout.varHandle(idxPath, JniEnv.peName)
              .set(nativeMethods, arena.allocateUtf8String(method.getName()));
        layout.varHandle(idxPath, JniEnv.peSignature)
              .set(nativeMethods, arena.allocateUtf8String(getJNIMethodSignature(method)));
        layout.varHandle(idxPath, JniEnv.peFnPtr)
              .set(nativeMethods, mem);

        idx++;
      }

      var callbackParam = arena.allocate(layoutCallbackParam);
      layoutCallbackParam.varHandle(peClassSig).set(callbackParam, arena.allocateUtf8String(classSig));
      layoutCallbackParam.varHandle(peMethods).set(callbackParam, nativeMethods);
      layoutCallbackParam.varHandle(peNumMethods).set(callbackParam, methods.size());

      callRegisterStub(callbackParam);
    }
  }

  /**
   * Create new instance of NativeRegister.
   *
   * @param klass <code>Class</code> what you want to set native methods.
   * @throws UnsupportedPlatformException when this method is called on unsupported platform.
   */
  public static NativeRegister create(Class<?> klass) throws UnsupportedPlatformException{
    var arch = System.getProperty("os.arch");
    if(arch.equals("amd64")){
      return new AMD64NativeRegister(klass);
    }
    else{
      throw new UnsupportedPlatformException(STR."\{arch} is not supported");
    }
  }

}
