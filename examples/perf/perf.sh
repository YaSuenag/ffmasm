#!/bin/bash

BASEDIR=$(dirname $0)

perf record -k 1 $JAVA_HOME/bin/java -jar $BASEDIR/target/perf-example-0.1.0.jar && \
  perf inject --jit -i perf.data -o perf.jit.data && \
  echo 'Completed. run "perf report -i perf.jit.data"'
