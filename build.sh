#!/bin/bash

# Build script for my2Dgame
# This script compiles the Java source files and runs the game

set -e

cd "$(dirname "$0")/my2Dgame"

echo "Cleaning bin directory..."
rm -rf bin
mkdir -p bin

echo "Compiling Java source files..."
find src -name "*.java" ! -name "._*" > sources_list.txt
javac -d bin @sources_list.txt
rm sources_list.txt

echo "Copying resource files..."
cp -r res/* bin/

echo "Build complete!"
echo ""
echo "To run the game, execute:"
echo "  java -cp bin my2Dgame.Main"
