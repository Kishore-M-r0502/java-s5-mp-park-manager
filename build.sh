#!/bin/sh
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/parking/*.java
echo "Build OK"
