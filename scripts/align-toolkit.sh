#!/usr/bin/env bash
set -e

# Morphe Google Photos Toolkit Obfuscation Aligner
# Automatically scans any Google Photos APK and resolves/updates ToolkitMappings.java

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$REPO_DIR/../.." && pwd)"

# Find smali-dexlib2 and guava jars
DEXLIB2_JAR=$(find "$HOME/.gradle/caches/modules-2/files-2.1" -name "smali-dexlib2*.jar" 2>/dev/null | grep -v "sources" | head -n 1)
GUAVA_JAR=$(find "$HOME/.gradle/caches/modules-2/files-2.1" -name "guava*.jar" 2>/dev/null | grep -v "sources" | head -n 1)

if [ -z "$DEXLIB2_JAR" ] || [ -z "$GUAVA_JAR" ]; then
    echo "[-] Error: smali-dexlib2 or guava jar not found in ~/.gradle/caches."
    echo "    Run './gradlew build' once to populate Gradle dependency cache."
    exit 1
fi

VERSION_ONLY=false
if [ "$1" = "--version-only" ]; then
    VERSION_ONLY=true
    shift
fi

# Locate Target APK
TARGET_APK="$1"
if [ -z "$TARGET_APK" ]; then
    for cand in \
        $(ls -1 "$WORKSPACE_DIR/stock_apks/"*photos*.apk 2>/dev/null | sort -V | tail -n 1) \
        $(ls -1 "$WORKSPACE_DIR/stock_apks/"*photos*7.98*.apk 2>/dev/null | head -n 1) \
        $(ls -1 "$REPO_DIR/"*photos*.apk 2>/dev/null | head -n 1); do
        if [ -f "$cand" ]; then
            TARGET_APK="$cand"
            break
        fi
    done
fi

if [ -z "$TARGET_APK" ] || [ ! -f "$TARGET_APK" ]; then
    echo "[-] Error: Google Photos target APK not specified and none found in stock_apks/"
    echo "    Usage: $0 [--version-only] <path-to-photos.apk>"
    exit 1
fi

BIN_DIR="$HOME/.cache/morphe-toolkit-aligner"
mkdir -p "$BIN_DIR"

if [ ! -f "$BIN_DIR/app/morphe/scripts/ToolkitAligner.class" ] || [ "$SCRIPT_DIR/ToolkitAligner.java" -nt "$BIN_DIR/app/morphe/scripts/ToolkitAligner.class" ]; then
    if [ "$VERSION_ONLY" = false ]; then
        echo "[*] Compiling ToolkitAligner..."
    fi
    javac -cp "$DEXLIB2_JAR:$GUAVA_JAR" -d "$BIN_DIR" "$SCRIPT_DIR/ToolkitAligner.java"
fi

if [ "$VERSION_ONLY" = true ]; then
    java -cp "$BIN_DIR:$DEXLIB2_JAR:$GUAVA_JAR" app.morphe.scripts.ToolkitAligner --version-only "$TARGET_APK"
    exit 0
fi

MAPPINGS_FILE="$REPO_DIR/extensions/shared/library/src/main/java/app/morphe/extension/shared/patches/toolkit/ToolkitMappings.java"
if [ -n "$2" ] && [ -f "$2" ]; then
    MAPPINGS_FILE="$2"
fi

echo "[*] Running ToolkitAligner on $TARGET_APK..."
java -cp "$BIN_DIR:$DEXLIB2_JAR:$GUAVA_JAR" app.morphe.scripts.ToolkitAligner "$TARGET_APK" "$MAPPINGS_FILE"

echo "[+] Alignment complete. ToolkitMappings.java is up to date."
