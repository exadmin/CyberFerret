#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

HOOK_REPOSITORY="$TEST_ROOT/hook-repository"
CONSUMER_REPOSITORY="$TEST_ROOT/consumer-repository"
FAKE_BIN_DIR="$TEST_ROOT/bin"
MARKER_FILE="$TEST_ROOT/cfcli-invoked"

mkdir -p "$HOOK_REPOSITORY/hooks" "$CONSUMER_REPOSITORY" "$FAKE_BIN_DIR"
cp "$REPOSITORY_ROOT/.pre-commit-hooks.yaml" "$HOOK_REPOSITORY/.pre-commit-hooks.yaml"
cp "$REPOSITORY_ROOT/hooks/cyberferret.sh" "$HOOK_REPOSITORY/hooks/cyberferret.sh"

git -C "$HOOK_REPOSITORY" init --quiet
git -C "$HOOK_REPOSITORY" config user.email "cyberferret-hook-test@example.invalid"
git -C "$HOOK_REPOSITORY" config user.name "CyberFerret hook test"
git -C "$HOOK_REPOSITORY" add .pre-commit-hooks.yaml hooks/cyberferret.sh
git -C "$HOOK_REPOSITORY" commit --quiet -m "test: prepare hook repository"
HOOK_REVISION="$(git -C "$HOOK_REPOSITORY" rev-parse HEAD)"

cat > "$FAKE_BIN_DIR/uname" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
    -s) printf 'Linux\n' ;;
    -m) printf 'x86_64\n' ;;
    *) printf 'Linux\n' ;;
esac
EOF

cat > "$FAKE_BIN_DIR/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

output_file=""
url=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -o|--output)
            output_file="$2"
            shift 2
            ;;
        -*)
            shift
            ;;
        *)
            url="$1"
            shift
            ;;
    esac
done

if [ -z "$output_file" ]; then
    printf '%s\n' "$CF_TEST_RELEASE_JSON"
    exit 0
fi

if [ "$url" != "https://downloads.example/cfcli-linux-amd64" ]; then
    printf 'Unexpected asset URL: %s\n' "$url" >&2
    exit 1
fi
cp "$CF_TEST_CLI" "$output_file"
EOF

cat > "$TEST_ROOT/cfcli-linux-amd64" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 3 ]
[ "$1" = "--mode=quick" ]
[ -d "$2" ]
[ -f "$3" ]
grep --fixed-strings --line-regexp "staged.txt" "$3" >/dev/null
printf 'invoked\n' > "$CF_TEST_MARKER"
EOF
chmod +x "$FAKE_BIN_DIR/uname" "$FAKE_BIN_DIR/curl" "$TEST_ROOT/cfcli-linux-amd64"

git -C "$CONSUMER_REPOSITORY" init --quiet
git -C "$CONSUMER_REPOSITORY" config user.email "cyberferret-consumer-test@example.invalid"
git -C "$CONSUMER_REPOSITORY" config user.name "CyberFerret consumer test"
printf 'safe content\n' > "$CONSUMER_REPOSITORY/staged.txt"
git -C "$CONSUMER_REPOSITORY" add staged.txt

cat > "$CONSUMER_REPOSITORY/.pre-commit-config.yaml" <<EOF
repos:
  - repo: file://$HOOK_REPOSITORY
    rev: $HOOK_REVISION
    hooks:
      - id: cyberferret
EOF

(
    cd "$CONSUMER_REPOSITORY"
    CF_TEST_RELEASE_JSON='{"tag_name":"cfcli-v2.0.4","assets":[{"name":"cfcli-linux-amd64","browser_download_url":"https://downloads.example/cfcli-linux-amd64"}]}' \
    CF_TEST_CLI="$TEST_ROOT/cfcli-linux-amd64" \
    CF_TEST_MARKER="$MARKER_FILE" \
    PRE_COMMIT_HOME="$TEST_ROOT/pre-commit-cache" \
    XDG_CACHE_HOME="$TEST_ROOT/cache" \
    PATH="$FAKE_BIN_DIR:$PATH" \
        pre-commit run cyberferret --config .pre-commit-config.yaml --verbose
)

test -f "$MARKER_FILE"
