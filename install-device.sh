#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ADB_BIN="${ADB:-adb}"
REQUESTED_SERIAL="${ANDROID_SERIAL:-}"
PAIR_ENDPOINT=""
CONNECT_ENDPOINT=""

usage() {
    cat <<EOF
Usage:
  $0                         Auto-detect one USB or paired wireless device
  $0 DEVICE_SERIAL          Use a connected device from "adb devices"
  $0 HOST:PORT              Connect to a wireless device, then install
  $0 --connect HOST:PORT    Connect to a wireless device, then install
  $0 --pair HOST:PAIR_PORT [HOST:DEBUG_PORT]
                             Pair wirelessly, connect, then install

For Android 11+, find both ports under Developer options → Wireless debugging.
The pairing port and debugging port are usually different.
EOF
}

fail() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "adb was not found. Install Android platform-tools or add adb to PATH."

case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
    --connect)
        [[ $# -eq 2 ]] || fail "--connect requires HOST:PORT. Run $0 --help for examples."
        CONNECT_ENDPOINT="$2"
        REQUESTED_SERIAL="$2"
        ;;
    --pair)
        [[ $# -eq 2 || $# -eq 3 ]] || fail "--pair requires HOST:PAIR_PORT and optionally HOST:DEBUG_PORT."
        PAIR_ENDPOINT="$2"
        CONNECT_ENDPOINT="${3:-}"
        [[ -n "$CONNECT_ENDPOINT" ]] && REQUESTED_SERIAL="$CONNECT_ENDPOINT"
        ;;
    "")
        ;;
    -* )
        fail "Unknown option '$1'. Run $0 --help for usage."
        ;;
    *)
        [[ $# -eq 1 ]] || fail "Pass only one device serial or HOST:PORT. Run $0 --help for usage."
        REQUESTED_SERIAL="$1"
        [[ "$REQUESTED_SERIAL" == *:* ]] && CONNECT_ENDPOINT="$REQUESTED_SERIAL"
        ;;
esac

connect_wireless() {
    local endpoint="$1"
    local output
    printf 'Connecting to wireless device at %s…\n' "$endpoint"
    output="$("$ADB_BIN" connect "$endpoint" 2>&1)" || fail "Could not connect to $endpoint: $output"
    if [[ "$output" != connected\ to* && "$output" != already\ connected\ to* ]]; then
        fail "Could not connect to $endpoint: $output"
    fi
}

discover_wireless_endpoints() {
    local name service endpoint remainder
    while read -r name service endpoint remainder; do
        [[ "$service" == _adb-tls-connect._tcp* ]] || continue
        [[ -n "$endpoint" ]] && printf '%s\n' "$endpoint"
    done < <("$ADB_BIN" mdns services 2>/dev/null || true)
}

list_ready_physical_devices() {
    local serial status remainder
    while IFS=$'\t ' read -r serial status remainder; do
        [[ "$status" == "device" ]] || continue
        [[ "$serial" == emulator-* ]] && continue
        printf '%s\n' "$serial"
    done < <("$ADB_BIN" devices)
}

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

if [[ -n "$PAIR_ENDPOINT" ]]; then
    if [[ ! -t 0 ]]; then
        fail "Wireless pairing needs an interactive terminal so the pairing code can be entered."
    fi
    read -r -s -p "Enter the six-digit wireless pairing code: " pairing_code
    printf '\n'
    [[ "$pairing_code" =~ ^[0-9]{6}$ ]] || fail "The pairing code must contain six digits."
    pair_output="$(printf '%s\n' "$pairing_code" | "$ADB_BIN" pair "$PAIR_ENDPOINT" 2>&1)" || \
        fail "Could not pair with $PAIR_ENDPOINT: $pair_output"
    [[ "$pair_output" == Successfully\ paired\ to* ]] || fail "Could not pair with $PAIR_ENDPOINT: $pair_output"
    printf '%s\n' "$pair_output"
fi

if [[ -n "$CONNECT_ENDPOINT" ]]; then
    connect_wireless "$CONNECT_ENDPOINT"
fi

if [[ -z "$REQUESTED_SERIAL" ]]; then
    physical_devices=()
    while IFS= read -r device; do
        [[ -n "$device" ]] && physical_devices+=("$device")
    done < <(list_ready_physical_devices)

    if [[ ${#physical_devices[@]} -eq 0 ]]; then
        wireless_endpoints=()
        while IFS= read -r endpoint; do
            [[ -n "$endpoint" ]] && wireless_endpoints+=("$endpoint")
        done < <(discover_wireless_endpoints | sort -u)
        if [[ ${#wireless_endpoints[@]} -eq 1 ]]; then
            connect_wireless "${wireless_endpoints[0]}"
            REQUESTED_SERIAL="${wireless_endpoints[0]}"
            physical_devices=()
            while IFS= read -r device; do
                [[ -n "$device" ]] && physical_devices+=("$device")
            done < <(list_ready_physical_devices)
        elif [[ ${#wireless_endpoints[@]} -gt 1 ]]; then
            printf 'Multiple paired wireless devices were discovered:\n' >&2
            printf '  %s\n' "${wireless_endpoints[@]}" >&2
            fail "Run $0 HOST:PORT to choose one."
        fi
    fi

    case "${#physical_devices[@]}" in
        0)
            fail "No Android phone is ready. Connect USB, or enable Wireless debugging and run $0 --pair HOST:PAIR_PORT HOST:DEBUG_PORT."
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
    fail "Android device '$REQUESTED_SERIAL' is not connected or authorized. Check Wireless debugging and confirm that both devices are on the same network."
fi

printf 'Building Pandora with JDK 17…\n'
(cd "$PROJECT_DIR" && ./gradlew :app:assembleDebug)

[[ -f "$APK_PATH" ]] || fail "Build completed but the APK was not found at $APK_PATH"

printf 'Installing on %s…\n' "$REQUESTED_SERIAL"
"$ADB_BIN" -s "$REQUESTED_SERIAL" install -r "$APK_PATH"

printf 'Pandora is installed on %s.\n' "$REQUESTED_SERIAL"
