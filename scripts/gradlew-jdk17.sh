#!/usr/bin/env bash
# Runs the Gradle wrapper with JDK 17 (system default java is 25, too new for AGP).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
exec ./gradlew "$@"
