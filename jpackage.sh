#!/bin/bash
# =============================================================================
# jpackage Build Script for Chronicle Conquest
# =============================================================================
# Creates a native installer (.dmg for macOS, .deb/.rpm for Linux)
# with a bundled JRE so users don't need Java installed.
#
# Usage:
#   ./jpackage.sh [mac|linux|all] [display-version]
#
# Examples:
#   ./jpackage.sh mac          # Build macOS installer (v1.0.0)
#   ./jpackage.sh linux        # Build Linux installer
#   ./jpackage.sh all          # Build for both platforms
#   ./jpackage.sh mac 1.1.0    # Build macOS installer labeled v1.1.0
#
# Prerequisites:
#   - JDK 17+ with jpackage (included in JDK 14+)
#   - Maven installed
#   - Native libraries in natives/macos or natives/linux (for JInput)
# =============================================================================

set -e

# --- Configuration -----------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PROJECT_NAME="ChronicleConquest"
MAIN_CLASS="my2Dgame.Main"
DISPLAY_VERSION="${2:-1.0.0}"    # Version shown to users
# Read actual Maven version from pom.xml for the JAR filename
MAVEN_VERSION=$(grep "<version>" pom.xml | head -1 | sed 's/.*<version>\([0-9.]*\)<\/version>.*/\1/')
JAR_NAME="my2Dgame-${MAVEN_VERSION}.jar"

# --- Detect host OS ----------------------------------------------------------
HOST_OS="$(uname -s)"
case "$HOST_OS" in
    Darwin*) HOST_OS="mac" ;;
    Linux*)  HOST_OS="linux" ;;
    *)       echo "ERROR: Unsupported host OS for jpackage: $HOST_OS"; exit 1 ;;
esac

TARGET="${1:-$HOST_OS}"       # Default to host OS, or use argument

echo "=========================================="
echo " Chronicle Conquest — jpackage Builder"
echo "=========================================="
echo "Host OS:      $HOST_OS"
echo "Target:       $TARGET"
echo "Maven ver:    $MAVEN_VERSION"
echo "Display ver:  $DISPLAY_VERSION"
echo "Main Class:   $MAIN_CLASS"
echo ""

# --- Validate target ---------------------------------------------------------
if [ "$TARGET" != "mac" ] && [ "$TARGET" != "linux" ] && [ "$TARGET" != "all" ]; then
    echo "ERROR: Target must be 'mac', 'linux', or 'all'"
    exit 1
fi

# --- Build JAR with Maven ----------------------------------------------------
echo ">>> Building JAR with Maven..."
mvn -q clean package -DskipTests

if [ ! -f "target/$JAR_NAME" ]; then
    echo "ERROR: JAR not found at target/$JAR_NAME"
    echo "Run 'mvn package' manually to debug."
    exit 1
fi
echo "JAR built: target/$JAR_NAME"
echo ""

# --- Helper: prepare input directory with JAR + natives ----------------------
prepare_input() {
    local dest="$1"
    local platform="$2"

    rm -rf "$dest"
    mkdir -p "$dest"

    # Copy the JAR (named by Maven version)
    cp "target/$JAR_NAME" "$dest/"

    # Copy native libraries (for JInput controllers)
    if [ -d "natives/$platform" ]; then
        mkdir -p "$dest/natives"
        cp -R "natives/$platform"/* "$dest/natives/"
        echo "  Bundled $platform native libraries"
    fi
}

# --- Build macOS (.dmg) ------------------------------------------------------
build_mac() {
    echo ">>> Building macOS installer..."

    INPUT_DIR="build/jpackage-input-mac"
    prepare_input "$INPUT_DIR" "mac"

    jpackage \
        --type dmg \
        --name "$PROJECT_NAME" \
        --app-version "$DISPLAY_VERSION" \
        --main-jar "$JAR_NAME" \
        --main-class "$MAIN_CLASS" \
        --input "$INPUT_DIR" \
        --dest "dist/" \
        --java-options "-Djava.library.path=$INPUT_DIR/natives" \
        --verbose

    echo "macOS installer (.dmg) created in dist/"
}

# --- Build Linux (.deb) ------------------------------------------------------
build_linux() {
    echo ">>> Building Linux installer..."

    INPUT_DIR="build/jpackage-input-linux"
    prepare_input "$INPUT_DIR" "linux"

    jpackage \
        --type deb \
        --name "$PROJECT_NAME" \
        --app-version "$DISPLAY_VERSION" \
        --main-jar "$JAR_NAME" \
        --main-class "$MAIN_CLASS" \
        --input "$INPUT_DIR" \
        --dest "dist/" \
        --java-options "-Djava.library.path=$INPUT_DIR/natives" \
        --verbose

    echo "Linux installer (.deb) created in dist/"
}

# --- Main --------------------------------------------------------------------
echo ">>> Running jpackage..."
echo ""

if [ "$TARGET" == "mac" ] || [ "$TARGET" == "all" ]; then
    build_mac
fi

if [ "$TARGET" == "linux" ] || [ "$TARGET" == "all" ]; then
    build_linux
fi

echo ""
echo "=========================================="
echo " Build complete!"
echo " Installers are in: dist/"
echo "=========================================="
