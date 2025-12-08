#!/bin/bash

MODE=$1
BENCH=$2
BASEDIR=$(dirname $0)

EXECUTABLE=$JAVA_HOME/bin/java
FUNCCALL_JAR=`ls $BASEDIR/target/original-ffmasm-benchmark-funccall-*.jar`
FFMASM_JAR=`ls $HOME/.m2/repository/com/yasuenag/ffmasm/*-SNAPSHOT/ffmasm-*-SNAPSHOT.jar | tail -n 1`
JVMCI_ADAPTER_JAR=`ls $HOME/.m2/repository/com/yasuenag/jvmci-adapter/*/jvmci-adapter-*.jar | tail -n 1`

JVM_OPTS="-cp $FUNCCALL_JAR:$FFMASM_JAR:$JVMCI_ADAPTER_JAR -Djava.library.path=$BASEDIR/target --enable-native-access=ALL-UNNAMED -Xms4g -Xmx4g -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.site=ALL-UNNAMED --add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED --add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED --add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED -XX:+UseEpsilonGC -XX:+AlwaysPreTouch -XX:+PreserveFramePointer -Xlog:jit+compilation=debug,jit+inlining=debug:file=$BASEDIR/target/$BENCH-jit.log::filesize=0"
APP_OPTS="com.yasuenag.ffmasm.benchmark.funccall.FuncCallComparison iterate $BENCH"

if [ "$MODE" = "perf" ]; then
  JVM_OPTS="$JVM_OPTS -XX:+UnlockDiagnosticVMOptions -XX:+DumpPerfMapAtExit"
  exec perf record -g -F 99 -- $EXECUTABLE $JVM_OPTS $APP_OPTS
elif [ "$MODE" = "run" ]; then
  exec $EXECUTABLE $JVM_OPTS $APP_OPTS
else
  echo 'Unknown mode'
fi
