#!/usr/bin/env bash
# CyberFerret pre-commit framework entry point.
#
# Required environment variable:
#   CYBER_FERRET_PASSWORD  – decryption password for the signature dictionary

set -euo pipefail

# ── OS-specific cache directory ────────────────────────────────────────────────
case "$(uname -s 2>/dev/null || echo Unknown)" in
    Linux*)        CF_CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/CyberFerret" ;;
    Darwin*)       CF_CACHE_DIR="$HOME/Library/Caches/CyberFerret" ;;
    CYGWIN*|MINGW*|MSYS*) CF_CACHE_DIR="${LOCALAPPDATA:-${APPDATA:-$HOME/AppData/Local}}/CyberFerret" ;;
    *)             CF_CACHE_DIR="$HOME/.cache/CyberFerret" ;;
esac

mkdir -p "$CF_CACHE_DIR"

# ── Download latest jar if a newer release is available ───────────────────────
JAR_PATH="$CF_CACHE_DIR/cyberferret-cli.jar"
VERSION_FILE="$CF_CACHE_DIR/.cyberferret-version"
GITHUB_API_URL="https://api.github.com/repos/exadmin/CyberFerret/releases/latest"

RELEASE_JSON="$(curl -sf --connect-timeout 10 "$GITHUB_API_URL")" \
    || { echo "[CyberFerret] ERROR: cannot reach GitHub API" >&2; exit 1; }

# Use python3 for reliable JSON parsing; fall back to grep/sed if unavailable.
if command -v python3 >/dev/null 2>&1; then
    LATEST_TAG="$(printf '%s' "$RELEASE_JSON" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')"
    JAR_URL="$(printf '%s' "$RELEASE_JSON" \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); print(next(a["browser_download_url"] for a in d["assets"] if a["name"]=="cyberferret-cli.jar"))')"
else
    LATEST_TAG="$(printf '%s' "$RELEASE_JSON" \
        | grep '"tag_name"' | head -1 \
        | sed 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')"
    JAR_URL="$(printf '%s' "$RELEASE_JSON" \
        | grep '"browser_download_url"' | grep 'cyberferret-cli\.jar' | head -1 \
        | sed 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')"
fi

[ -n "$LATEST_TAG" ] || { echo "[CyberFerret] ERROR: failed to parse release tag" >&2; exit 1; }
[ -n "$JAR_URL" ]    || { echo "[CyberFerret] ERROR: cyberferret-cli.jar asset not found in release $LATEST_TAG" >&2; exit 1; }

CACHED_TAG="$(cat "$VERSION_FILE" 2>/dev/null || true)"
if [ ! -f "$JAR_PATH" ] || [ "$CACHED_TAG" != "$LATEST_TAG" ]; then
    echo "[CyberFerret] Downloading $LATEST_TAG ..." >&2
    _tmp="${JAR_PATH}.tmp.$$"
    if curl -fL --connect-timeout 60 -o "$_tmp" "$JAR_URL"; then
        mv "$_tmp" "$JAR_PATH"
        printf '%s' "$LATEST_TAG" > "$VERSION_FILE"
        echo "[CyberFerret] Download complete." >&2
    else
        rm -f "$_tmp"
        echo "[CyberFerret] ERROR: jar download failed" >&2
        exit 1
    fi
fi

# ── Build staged-file list ─────────────────────────────────────────────────────
REPO_ROOT="$(git rev-parse --show-toplevel)"
FILES_LIST="$(mktemp)"
trap 'rm -f "$FILES_LIST"' EXIT

# Pass relative paths – CyberFerretCLI resolves them against REPO_ROOT.
git diff --cached --name-only > "$FILES_LIST"

if [ ! -s "$FILES_LIST" ]; then
    echo "[CyberFerret] No staged files to scan." >&2
    exit 0
fi

# ── Run CyberFerret (PWD = cache dir so all temp/cache files land there) ──────
cd "$CF_CACHE_DIR"
exec java -cp "$JAR_PATH" \
    com.github.exadmin.cyberferret.CyberFerretCLI \
    "$REPO_ROOT" "$FILES_LIST"
