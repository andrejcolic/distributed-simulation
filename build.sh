#!/usr/bin/env bash
# Compiles the given framework (excluding TestG.java, which does not compile) + your code into bin/
set -e
rm -rf bin && mkdir -p bin
javac -encoding UTF-8 -d bin \
  $(find src framework -name '*.java' | grep -v TestG.java)
echo "Build done in bin/"
