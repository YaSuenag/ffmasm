.text

# jlong Java_com_yasuenag_ffmasm_benchmark_funccall.FuncCallComparison_invokeJNI(JNIEnv *env)
.global Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_invokeJNI
.type   Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_invokeJNI, @function
Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_invokeJNI:
  push %rbp
  mov  %rsp, %rbp
  rdtsc
  shl  $32,  %rdx
  or   %rdx, %rax
  leave
  ret
