#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.7"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_HOME_DIR="$(find "$CACHE_DIR" -maxdepth 2 -type d -name "gradle-${GRADLE_VERSION}" 2>/dev/null | head -n 1 || true)"

if [ -z "${GRADLE_HOME_DIR:-}" ]; then
  mkdir -p "$CACHE_DIR"
  TMP_ZIP="/tmp/gradle-${GRADLE_VERSION}-bin.zip"
  curl -fsSL "$DIST_URL" -o "$TMP_ZIP"
  unzip -q "$TMP_ZIP" -d "$CACHE_DIR"
  rm -f "$TMP_ZIP"
  GRADLE_HOME_DIR="$(find "$CACHE_DIR" -maxdepth 2 -type d -name "gradle-${GRADLE_VERSION}" 2>/dev/null | head -n 1)"
fi

exec "${GRADLE_HOME_DIR}/bin/gradle" "$@"
