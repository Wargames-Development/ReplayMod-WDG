#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

is_java8() {
    local home="$1"
    [ -x "$home/bin/java" ] || return 1
    "$home/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "1\.8\.|version "8'
}

java_arch() {
    local home="$1"
    "$home/bin/java" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/^[[:space:]]*os\.arch = //p' \
        | head -1
}

select_macos_java8() {
    local host_arch candidate arch
    host_arch="$(uname -m)"

    # Respect an explicitly selected Java 8 first, provided it can run 1.7.10's natives.
    if [ -n "${JAVA_HOME:-}" ] && is_java8 "$JAVA_HOME"; then
        arch="$(java_arch "$JAVA_HOME")"
        if [ "$host_arch" != "arm64" ] || [ "$arch" = "x86_64" ] || [ "$arch" = "amd64" ]; then
            printf '%s\n' "$JAVA_HOME"
            return 0
        fi
    fi

    # On Apple Silicon, Minecraft 1.7.10/LWJGL 2 needs an x86_64 Java 8 JVM.
    # Search installed JDKs rather than blindly accepting java_home's first Java 8.
    for candidate in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        [ -d "$candidate" ] || continue
        is_java8 "$candidate" || continue
        arch="$(java_arch "$candidate")"
        if [ "$host_arch" != "arm64" ] || [ "$arch" = "x86_64" ] || [ "$arch" = "amd64" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    candidate=$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)
    if [ -n "$candidate" ] && is_java8 "$candidate"; then
        arch="$(java_arch "$candidate")"
        if [ "$host_arch" != "arm64" ] || [ "$arch" = "x86_64" ] || [ "$arch" = "amd64" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    fi

    return 1
}

if [ "$(uname -s)" = "Darwin" ]; then
    JAVA_HOME_8="$(select_macos_java8 || true)"
    if [ -z "$JAVA_HOME_8" ]; then
        if [ "$(uname -m)" = "arm64" ]; then
            echo "ERROR: An x86_64 Java 8 JDK is required for Minecraft 1.7.10/LWJGL 2 development on Apple Silicon." >&2
            echo "Install an x86_64 Java 8 JDK (for example Temurin 8), then re-run this script." >&2
        else
            echo "ERROR: Java 8 JDK not found." >&2
        fi
        exit 1
    fi
    export JAVA_HOME="$JAVA_HOME_8"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
printf '=== JAVA ===\n%s\n' "$JAVA_VERSION"
printf 'JAVA_HOME: %s\n' "${JAVA_HOME:-<system>}"
printf 'JVM architecture: %s\n\n' "$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*os\.arch = //p' | head -1)"

java -version 2>&1 | grep -Eq 'version "1\.8\.|version "8' || {
    echo "ERROR: ReplayMod-WDG 1.7.10 must be set up with Java 8." >&2
    exit 1
}

if [ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ]; then
    JVM_ARCH="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*os\.arch = //p' | head -1)"
    case "$JVM_ARCH" in
        x86_64|amd64) ;;
        *)
            echo "ERROR: Apple Silicon dev launches require an x86_64 Java 8 JVM because Minecraft 1.7.10 uses x86_64 LWJGL 2 natives." >&2
            exit 1
            ;;
    esac
fi

printf '=== 1.7.10 WORKSPACE ===\n'
./gradlew --no-daemon setupDecompWorkspace idea

printf '\n=== BUILD VALIDATION ===\n'
./gradlew --no-daemon clean build

printf '\n=== READY ===\n'
printf 'ReplayMod-WDG is configured as a native Minecraft 1.7.10 project.\n'
