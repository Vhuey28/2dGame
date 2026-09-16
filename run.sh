#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

OS_NAME="$(uname -s)"
case "$OS_NAME" in
    Darwin*) NATIVES_DIR="natives/macos" ;;
    Linux*)  NATIVES_DIR="natives/linux" ;;
    *)       echo "Unsupported OS for run.sh: $OS_NAME (use run.ps1 on Windows)"; exit 1 ;;
esac

echo "Building with Maven..."
mvn -q clean package

echo "Starting game (natives: $NATIVES_DIR)..."
java -Djava.library.path="$NATIVES_DIR" -jar target/my2Dgame-1.0.0.jar