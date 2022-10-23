.text

# jlong Java_com_yasuenag_ffmasm_benchmark_FFMComparison_rdtsc(JNIEnv *env)
.global Java_com_yasuenag_ffmasm_benchmark_FFMComparison_rdtsc
.type   Java_com_yasuenag_ffmasm_benchmark_FFMComparison_rdtsc, @function
Java_com_yasuenag_ffmasm_benchmark_FFMComparison_rdtsc:
  push %rbp
  mov  %rsp, %rbp
  rdtsc
  shl  $32,  %rdx
  or   %rdx, %rax
  leave
  ret
