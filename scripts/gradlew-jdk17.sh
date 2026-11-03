#!/usr/bin/env bash
# Runs the Gradle wrapper with JDK 17 (system default java is 25, too new for AGP).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="${BS_ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
exec ./gradlew "$@"
