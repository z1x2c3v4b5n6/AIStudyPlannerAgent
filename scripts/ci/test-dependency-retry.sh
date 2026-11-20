#!/usr/bin/env bash
set -euo pipefail
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
export TEST_COUNT="$dir/count"
cat > "$dir/sleep" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "$dir/sleep"
export PATH="$dir:$PATH"
script=$(dirname "$0")/with-dependency-retry.sh
bash "$script" maven bash -c '
    if [[ -e "$TEST_COUNT" ]]; then exit 0; fi
    touch "$TEST_COUNT"
    echo "Non-resolvable parent POM: Could not transfer artifact (429 Too Many Requests)"
    exit 1
'
rm -f "$TEST_COUNT"
if bash "$script" maven bash -c '
    echo called >> "$TEST_COUNT"
    echo "Registry emitted 429 Too Many Requests before compilation"
    echo "COMPILATION ERROR"
    exit 42
'; then
    echo 'Compilation failure must remain a failure' >&2
    exit 1
else
    [[ "$?" == 42 ]] && [[ $(wc -l < "$TEST_COUNT") == 1 ]]
fi
