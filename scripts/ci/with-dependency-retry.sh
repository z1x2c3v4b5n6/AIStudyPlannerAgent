#!/usr/bin/env bash
set -euo pipefail
kind=${1:?Pass maven or npm}
shift
case "$kind" in
  maven|npm) ;;
  *) exit 64 ;;
esac
if (($# == 0)); then exit 64; fi
log=$(mktemp)
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3; do
  set +e
  "$@" 2>&1 | tee "$log"
  codes=("${PIPESTATUS[@]}")
  set -e
  status=${codes[0]}
  if ((codes[1] != 0)); then exit "${codes[1]}"; fi
  if ((status == 0)); then exit 0; fi

  category="${kind}_other_failure"
  if [[ "$kind" == maven ]]; then
    if grep -Eqi 'COMPILATION ERROR|maven-compiler-plugin.*(Compilation failure|Fatal error compiling)' "$log"; then
      category=java_compilation
    elif grep -Eqi 'There are test failures|Failures: [1-9]|Errors: [1-9]|maven-surefire-plugin.*test failures' "$log"; then
      category=java_tests
    elif grep -Eqi 'Could not transfer artifact|Failed to read artifact descriptor|Could not resolve dependencies|Non-resolvable parent POM|Failed to collect dependencies' "$log"; then
      category=maven_dependency
      if grep -Eqi '(^|[^0-9])(429|502|503|504)([^0-9]|$)|Too Many Requests|Connection reset|timed out|Temporary failure in name resolution' "$log"; then
        category=maven_dependency_transient
      fi
    fi
  elif grep -Eqi 'npm (ERR!|error).*(E429|E502|E503|E504|ECONNRESET|ETIMEDOUT|EAI_AGAIN)' "$log"; then
    category=npm_dependency_transient
  elif grep -Eqi 'npm (ERR!|error).*(E404|E401|E403|ENOTFOUND|ERESOLVE)' "$log"; then
    category=npm_dependency
  fi

  printf 'ci_failure category=%s attempt=%s/3 exit=%s\n' "$category" "$attempt" "$status" >&2
  if [[ "${CI:-}" == true ]]; then
    printf '::warning title=%s::Command failed on attempt %s/3, exit %s\n' "$category" "$attempt" "$status"
  fi
  if [[ "$category" != *_transient || "$attempt" == 3 ]]; then exit "$status"; fi
  sleep "$((attempt * 8))"
done
