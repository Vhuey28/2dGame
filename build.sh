#!/bin/bash

# Build script for my2Dgame
# This script compiles the Java source files and runs the game

set -e

cd "$(dirname "$0")"

echo "Cleaning bin directory..."
rm -rf bin
mkdir -p bin

echo "Compiling Java source files..."
javac -d bin \
    src/my2Dgame/Main.java \
    src/my2Dgame/GamePanel.java \
    src/my2Dgame/KeyHandler.java \
    src/entity/Player.java \
    src/entity/Enemy.java \
    src/entity/Entity.java \
    src/tile/Tile.java \
    src/tile/tileManager.java

echo "Copying resource files..."
cp -r res/* bin/

echo "Build complete!"
echo ""
echo "To run the game, execute:"
echo "  java -cp bin my2Dgame.Main"
