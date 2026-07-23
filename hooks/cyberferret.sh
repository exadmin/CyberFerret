#!/usr/bin/env bash
# CyberFerret pre-commit framework entry point.
#
# Required environment variable:
#   CYBER_FERRET_PASSWORD - decryption password for the signature dictionary

set -euo pipefail

# Select the release asset for the host platform.
case "$(uname -s 2>/dev/null || printf 'Unknown\n')" in
    Linux*) CF_OS="linux"; CF_EXTENSION="" ;;
    Darwin*) CF_OS="darwin"; CF_EXTENSION="" ;;
    CYGWIN*|MINGW*|MSYS*) CF_OS="windows"; CF_EXTENSION=".exe" ;;
    *)
        echo "[CyberFerret] ERROR: unsupported operating system" >&2
        exit 1
        ;;
esac

case "$(uname -m 2>/dev/null || printf 'Unknown\n')" in
    x86_64|amd64) CF_ARCH="amd64" ;;
    arm64|aarch64) CF_ARCH="arm64" ;;
    *)
        echo "[CyberFerret] ERROR: unsupported architecture" >&2
        exit 1
        ;;
esac

# Use the platform cache directory.
case "$CF_OS" in
    linux) CF_CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/CyberFerret" ;;
    darwin) CF_CACHE_DIR="$HOME/Library/Caches/CyberFerret" ;;
    windows) CF_CACHE_DIR="${LOCALAPPDATA:-${APPDATA:-$HOME/AppData/Local}}/CyberFerret" ;;
esac

mkdir -p "$CF_CACHE_DIR"

# Download the latest native CLI when the cached release is outdated.
BINARY_NAME="cfcli-${CF_OS}-${CF_ARCH}${CF_EXTENSION}"
CLI_PATH="$CF_CACHE_DIR/$BINARY_NAME"
VERSION_FILE="$CF_CACHE_DIR/.cfcli-${CF_OS}-${CF_ARCH}-version"
GITHUB_API_URL="https://api.github.com/repos/exadmin/CyberFerret/releases/latest"

RELEASE_JSON="$(curl -sf --connect-timeout 10 "$GITHUB_API_URL")" \
    || { echo "[CyberFerret] ERROR: cannot reach GitHub API" >&2; exit 1; }

# Use python3 for reliable JSON parsing; fall back to grep/sed if unavailable.
if command -v python3 >/dev/null 2>&1; then
    LATEST_TAG="$(printf '%s' "$RELEASE_JSON" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')"
    ASSET_URL="$(printf '%s' "$RELEASE_JSON" \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); name=sys.argv[1]; print(next((a["browser_download_url"] for a in d["assets"] if a["name"]==name), ""))' "$BINARY_NAME")"
else
    LATEST_TAG="$(printf '%s' "$RELEASE_JSON" \
        | grep '"tag_name"' | head -1 \
        | sed 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')"
    ASSET_URL="$(printf '%s' "$RELEASE_JSON" \
        | grep '"browser_download_url"' | grep -F "/$BINARY_NAME\"" | head -1 \
        | sed 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')"
fi

[ -n "$LATEST_TAG" ] || { echo "[CyberFerret] ERROR: failed to parse release tag" >&2; exit 1; }
[ -n "$ASSET_URL" ] || {
    echo "[CyberFerret] ERROR: $BINARY_NAME asset not found in release $LATEST_TAG" >&2
    exit 1
}

CACHED_TAG="$(cat "$VERSION_FILE" 2>/dev/null || true)"
if [ ! -x "$CLI_PATH" ] || [ "$CACHED_TAG" != "$LATEST_TAG" ]; then
    echo "[CyberFerret] Downloading $LATEST_TAG ..." >&2
    _tmp="${CLI_PATH}.tmp.$$"
    if curl -fL --connect-timeout 60 -o "$_tmp" "$ASSET_URL"; then
        chmod 0755 "$_tmp"
        mv "$_tmp" "$CLI_PATH"
        printf '%s' "$LATEST_TAG" > "$VERSION_FILE"
        echo "[CyberFerret] Download complete." >&2
    else
        rm -f "$_tmp"
        echo "[CyberFerret] ERROR: CLI download failed" >&2
        exit 1
    fi
fi

# Build the staged-file list.
REPO_ROOT="$(git rev-parse --show-toplevel)"
FILES_LIST="$(mktemp)"
trap 'rm -f "$FILES_LIST"' EXIT

# Pass relative paths. CyberFerret resolves them against REPO_ROOT.
git diff --cached --name-only > "$FILES_LIST"

if [ ! -s "$FILES_LIST" ]; then
    echo "[CyberFerret] No staged files to scan." >&2
    exit 0
fi

# Run CyberFerret in quick mode and preserve its exit code.
cd "$CF_CACHE_DIR"
SCAN_STATUS=0
"$CLI_PATH" --mode=quick "$REPO_ROOT" "$FILES_LIST" || SCAN_STATUS=$?
exit "$SCAN_STATUS"
