#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ADB_BIN="${ADB:-adb}"
REQUESTED_SERIAL="${1:-${ANDROID_SERIAL:-}}"

fail() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "adb was not found. Install Android platform-tools or add adb to PATH."

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" || "$($JAVA_HOME/bin/java -version 2>&1 | head -n 1)" != *'17.'* ]]; then
    for candidate in \
        /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
        if [[ -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    fail "JDK 17 was not found. Install it with: brew install openjdk@17"
fi

if [[ "$($JAVA_HOME/bin/java -version 2>&1 | head -n 1)" != *'17.'* ]]; then
    fail "Pandora requires JDK 17, but JAVA_HOME points to a different version: $JAVA_HOME"
fi

if [[ -z "$REQUESTED_SERIAL" ]]; then
    physical_devices=()
    while IFS=$'\t' read -r serial status; do
        [[ "$status" == "device" ]] || continue
        [[ "$serial" == emulator-* ]] && continue
        physical_devices+=("$serial")
    done < <("$ADB_BIN" devices)

    case "${#physical_devices[@]}" in
        0)
            fail "No physical Android device is ready. Connect it, enable USB debugging, and accept the authorization prompt."
            ;;
        1)
            REQUESTED_SERIAL="${physical_devices[0]}"
            ;;
        *)
            printf 'Multiple physical devices are connected:\n' >&2
            printf '  %s\n' "${physical_devices[@]}" >&2
            fail "Run $0 <device-serial> to choose one."
            ;;
    esac
fi

if ! "$ADB_BIN" -s "$REQUESTED_SERIAL" get-state 2>/dev/null | grep -qx 'device'; then
    fail "Android device '$REQUESTED_SERIAL' is not connected or authorized."
fi

printf 'Building Pandora with JDK 17…\n'
(cd "$PROJECT_DIR" && ./gradlew :app:assembleDebug)

[[ -f "$APK_PATH" ]] || fail "Build completed but the APK was not found at $APK_PATH"

printf 'Installing on %s…\n' "$REQUESTED_SERIAL"
"$ADB_BIN" -s "$REQUESTED_SERIAL" install -r "$APK_PATH"

printf 'Pandora is installed on %s.\n' "$REQUESTED_SERIAL"
