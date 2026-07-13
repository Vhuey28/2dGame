#!/bin/bash

# Run script for my2Dgame
# This script copies the project to /tmp, builds it, and runs it
# This is necessary due to filesystem issues with the external drive

ORIGINAL_DIR="$(cd "$(dirname "$0")" && pwd)/my2Dgame"
WORK_DIR="/tmp/my2Dgame_work"

echo "Setting up game in temporary directory..."
rm -rf "$WORK_DIR"
cp -r "$ORIGINAL_DIR" "$WORK_DIR"

cd "$WORK_DIR"

echo "Cleaning bin directory..."
rm -rf bin
mkdir -p bin

echo "Compiling Java source files..."
# Compile all Java sources under src so new files are included
find src -name "*.java" ! -name "._*" > /tmp/sources_list.txt
javac -d bin @/tmp/sources_list.txt

echo "Copying resource files..."
cp -r res/* bin/

echo "Build complete! Starting game..."
echo ""

# Run the game
java -cp bin my2Dgame.Main