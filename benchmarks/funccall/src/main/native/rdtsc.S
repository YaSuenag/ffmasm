.text

# jlong Java_com_yasuenag_ffmasm_benchmark_funccall.FuncCallComparison_rdtsc(JNIEnv *env)
.global Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_rdtsc
.type   Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_rdtsc, @function
Java_com_yasuenag_ffmasm_benchmark_funccall_FuncCallComparison_rdtsc:
  push %rbp
  mov  %rsp, %rbp
  rdtsc
  shl  $32,  %rdx
  or   %rdx, %rax
  leave
  ret
