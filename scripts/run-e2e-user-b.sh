#!/usr/bin/env bash
#
# Shows the manual tester what User B has to do during the frontend E2E tests.
#
# Run it in a second terminal before or while scripts/run-frontend-e2e-tests.sh runs.
# Each time a test needs User B, one line starting with "USER B:" is shown. Do that step
# on User B's device, then press Enter here: the test on User A's emulator carries on.
# Stop it with Ctrl+C once the tests have finished.
#
# If User B's emulator is also connected to adb, set ANDROID_SERIAL to User A's emulator
# first, as for the tests (see Testing.md, "Running the tests on User A's device only").
#
# Usage: ./scripts/run-e2e-user-b.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT/frontend/local.properties"

# must match LOG_TAG, USER_B_DONE_FILE and USER_B_DONE in the E2E tests
LOG_TAG="FriendSystemTest"
DONE_FILE="/data/local/tmp/e2e-user-b-done"
DONE="done"

info() { printf '\033[34m==> %s\033[0m\n' "$1"; }
die()  { printf '\033[31mERROR: %s\033[0m\n' "$1"; exit 1; }

# adb from ANDROID_HOME, sdk.dir in frontend/local.properties, or the PATH
find_adb() {
  local sdk_dir="${ANDROID_HOME:-}"
  if [ -z "$sdk_dir" ] && [ -f "$LOCAL_PROPERTIES" ]; then
    sdk_dir="$(grep -E '^\s*sdk\.dir\s*=' "$LOCAL_PROPERTIES" | head -n 1 | sed -E 's/^\s*sdk\.dir\s*=\s*//; s/"//g; s/\\:/:/g' || true)"
  fi
  if [ -n "$sdk_dir" ] && [ -x "$sdk_dir/platform-tools/adb" ]; then
    echo "$sdk_dir/platform-tools/adb"
  elif command -v adb >/dev/null 2>&1; then
    command -v adb
  else
    die "adb not found. Set ANDROID_HOME or sdk.dir in frontend/local.properties."
  fi
}

ADB="$(find_adb)"

# only steps logged from now on, not ones from an earlier run
SINCE="$("$ADB" shell "date '+%m-%d %H:%M:%S.000'" </dev/null | tr -d '\r')"
[ -n "$SINCE" ] || die "No device found. Start User A's emulator first."

info "Waiting for User B steps. Keep this open while the E2E tests run, Ctrl+C to stop."
"$ADB" logcat -v raw -s "$LOG_TAG:I" -T "$SINCE" | while IFS= read -r line; do
  line="${line%$'\r'}"
  case "$line" in
    "USER B: "*)
      printf '\n\033[33m%s\033[0m\n' "$line"
      # the pipe is this loop's input, so Enter is read from the terminal
      read -r -p "Press Enter when done" _ </dev/tty
      "$ADB" shell "echo $DONE > $DONE_FILE" </dev/null
      info "Sent, the test carries on."
      ;;
  esac
done
