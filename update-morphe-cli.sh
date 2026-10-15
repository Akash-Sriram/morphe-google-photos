#!/usr/bin/env bash
# Auto-updater for Morphe Desktop CLI
# Checks GitHub releases for MorpheApp/morphe-desktop, downloads the latest (including pre-releases),
# and updates the project's scripts and symlinks.

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="MorpheApp/morphe-desktop"

echo "🔍 Checking latest Morphe Desktop release from https://github.com/$REPO ..."

LATEST_URL=""
LATEST_TAG=""
LATEST_JAR_NAME=""

# 1. Try using GitHub CLI (authenticated, no rate limits)
if command -v gh >/dev/null 2>&1; then
    RELEASE_DATA=$(gh api "repos/$REPO/releases" --jq '.[0] | {tag: .tag_name, url: (.assets[] | select(.name | endswith("-all.jar")) | .browser_download_url), name: (.assets[] | select(.name | endswith("-all.jar")) | .name)}' 2>/dev/null || true)
    if [ -n "$RELEASE_DATA" ]; then
        LATEST_TAG=$(echo "$RELEASE_DATA" | grep -o '"tag": *"[^"]*"' | head -n 1 | cut -d'"' -f4)
        LATEST_URL=$(echo "$RELEASE_DATA" | grep -o '"url": *"[^"]*"' | head -n 1 | cut -d'"' -f4)
        LATEST_JAR_NAME=$(echo "$RELEASE_DATA" | grep -o '"name": *"[^"]*"' | head -n 1 | cut -d'"' -f4)
    fi
fi

# 2. Fallback to curl
if [ -z "$LATEST_URL" ]; then
    LATEST_URL=$(curl -s "https://api.github.com/repos/$REPO/releases" | grep -o 'https://github.com/'"$REPO"'/releases/download/[^"]*-all.jar' | head -n 1)
    if [ -n "$LATEST_URL" ]; then
        LATEST_JAR_NAME=$(basename "$LATEST_URL")
        LATEST_TAG=$(echo "$LATEST_URL" | sed -E 's|.*/releases/download/([^/]+)/.*|\1|')
    fi
fi

if [ -z "$LATEST_URL" ] || [ -z "$LATEST_JAR_NAME" ]; then
    echo "❌ Failed to retrieve latest release information from GitHub." >&2
    exit 1
fi

echo "📦 Upstream latest release: $LATEST_TAG ($LATEST_JAR_NAME)"

TARGET_JAR="$PROJECT_DIR/$LATEST_JAR_NAME"

if [ -f "$TARGET_JAR" ]; then
    echo "✅ $LATEST_JAR_NAME is already downloaded and up to date."
else
    echo "⬇️ Downloading $LATEST_JAR_NAME from $LATEST_URL ..."
    curl -L --progress-bar -o "$TARGET_JAR" "$LATEST_URL"
    chmod +x "$TARGET_JAR"
    echo "✅ Downloaded successfully to $TARGET_JAR"
fi

# Maintain 'morphe-desktop-latest.jar' symlink
ln -sf "$TARGET_JAR" "$PROJECT_DIR/morphe-desktop-latest.jar"

# Verify CLI version
INSTALLED_VER=$(java -jar "$TARGET_JAR" -V 2>/dev/null || echo "Unknown")
echo "🚀 Active CLI: $INSTALLED_VER"

# Update scripts to point to latest version
update_script_cli() {
    local script_file="$1"
    if [ -f "$script_file" ]; then
        sed -i -E 's|morphe-desktop-[0-9]+\.[0-9]+\.[0-9]+-[^"'\'' ]*-all\.jar|'"$LATEST_JAR_NAME"'|g' "$script_file"
        echo "📝 Updated $script_file"
    fi
}

update_script_cli "$PROJECT_DIR/morphe-cli.sh"
update_script_cli "$PROJECT_DIR/fast-build.sh"
update_script_cli "$PROJECT_DIR/morphe-google-photos/fast-build.sh"

echo "🎉 All scripts are now configured to use $LATEST_JAR_NAME ($LATEST_TAG)!"
