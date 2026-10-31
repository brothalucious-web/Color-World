#!/bin/sh
# Compiles the Minecraft-free rule code and runs the self test. Needs only Java 21+ (a JDK, or a JRE that includes the compiler module).
set -e
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
if command -v javac >/dev/null 2>&1; then
  javac -d "$OUT" src/main/java/com/colorworld/core/*.java tools/LogicSelfTest.java
else
  java -m jdk.compiler/com.sun.tools.javac.Main -d "$OUT" src/main/java/com/colorworld/core/*.java tools/LogicSelfTest.java
fi
java -cp "$OUT" LogicSelfTest
