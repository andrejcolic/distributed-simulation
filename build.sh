#!/usr/bin/env bash
# Prevodi dati okvir (bez TestG.java koji se ne kompajlira) + tvoj kod u bin/
set -e
TEST=assignment/public_tests/test_files/test
rm -rf bin && mkdir -p bin
javac -encoding UTF-8 -d bin \
  $(find src "$TEST/src" -name '*.java' | grep -v TestG.java)
echo "Build gotov u bin/"
