#!/usr/bin/env bash

# Canonical one-click verification entry point for local runs and GitHub Actions.
# It always preserves command output and test reports, even when the build fails.

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${1:-${CI_LOG_DIR:-ci-output}}"
cd "$ROOT_DIR"
mkdir -p "$OUTPUT_DIR"

if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/java" ]]; then
  unset JAVA_HOME
fi
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  if [[ -n "$JAVA_HOME" ]]; then
    export JAVA_HOME
  fi
fi

overall_status=0
declare -a step_results=()

run_step() {
  local name="$1"
  shift
  local log_file="$OUTPUT_DIR/${name}.log"
  echo "=== ${name} ==="
  echo "command: $*"
  set +e
  "$@" 2>&1 | tee "$log_file"
  local status=${PIPESTATUS[0]}
  step_results+=("${name}|${status}|${log_file}")
  if [[ "$status" -ne 0 ]]; then
    overall_status=1
    echo "${name}: FAILED (${status})"
  else
    echo "${name}: PASSED"
  fi
  echo
}

{
  echo "# Automated test run"
  echo
  echo "- started_at: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "- repository: $ROOT_DIR"
  echo "- commit: $(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "- branch: $(git branch --show-current 2>/dev/null || echo unknown)"
  echo "- java: ${JAVA_HOME:-system default}"
  if [[ -f app/build.gradle.kts ]]; then
    version_name=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
    version_code=$(grep 'versionCode' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')
    echo "- version_name: ${version_name}"
    echo "- version_code: ${version_code}"
  fi
  echo
  echo "## Working tree"
  git status --short 2>/dev/null || true
} | tee "$OUTPUT_DIR/metadata.log"

chmod +x ./gradlew 2>/dev/null || true
run_step "diff-check" git diff --check
run_step "gradle" ./gradlew --no-daemon clean test assembleRelease --stacktrace

# Keep the standard Gradle reports beside the console logs for one-click download.
if [[ -d app/build/test-results ]]; then
  mkdir -p "$OUTPUT_DIR/test-results"
  cp -R app/build/test-results/. "$OUTPUT_DIR/test-results/"
fi
if [[ -d app/build/reports/tests ]]; then
  mkdir -p "$OUTPUT_DIR/test-reports"
  cp -R app/build/reports/tests/. "$OUTPUT_DIR/test-reports/"
fi
if [[ -d app/build/reports ]]; then
  mkdir -p "$OUTPUT_DIR/gradle-reports"
  while IFS= read -r report; do
    relative_path="${report#app/build/reports/}"
    mkdir -p "$OUTPUT_DIR/gradle-reports/$(dirname "$relative_path")"
    cp "$report" "$OUTPUT_DIR/gradle-reports/$relative_path"
  done < <(find app/build/reports -maxdepth 2 -type f \( -name '*.html' -o -name '*.xml' \) 2>/dev/null)
fi

apk_count=$(find app/build/outputs/apk -type f -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')
{
  echo "# Automated test summary"
  echo
  echo "- finished_at: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "- overall_status: ${overall_status}"
  echo "- apk_count: ${apk_count}"
  echo
  echo "## Steps"
  for result in "${step_results[@]}"; do
    IFS='|' read -r name status log_file <<< "$result"
    if [[ "$status" -eq 0 ]]; then
      echo "- ✅ ${name}"
    else
      echo "- ❌ ${name} (exit ${status})"
    fi
    echo "  - log: ${log_file}"
  done
  echo
  echo "## APKs"
  if [[ "$apk_count" -eq 0 ]]; then
    echo "- No APK was produced. Inspect gradle.log."
  else
    while IFS= read -r apk; do
      if command -v sha256sum >/dev/null 2>&1; then
        digest=$(sha256sum "$apk" | awk '{print $1}')
      else
        digest=$(shasum -a 256 "$apk" | awk '{print $1}')
      fi
      echo "- ${apk}"
      echo "  - sha256: ${digest}"
    done < <(find app/build/outputs/apk -type f -name '*.apk' | sort)
  fi
} | tee "$OUTPUT_DIR/summary.md"

exit "$overall_status"
