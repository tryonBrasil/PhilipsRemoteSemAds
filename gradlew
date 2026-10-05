#!/bin/sh
set -e
if [ -z "$JAVA_HOME" ]; then echo "JAVA_HOME is not set"; exit 1; fi
GRADLE_VERSION=8.7
DIST=gradle-$GRADLE_VERSION-bin.zip
DIR="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION"
ZIP="$DIR/$DIST"
if [ ! -f "$ZIP" ]; then
  mkdir -p "$DIR"
  curl -fsSL -o "$ZIP" "https://services.gradle.org/distributions/$DIST"
  unzip -q "$ZIP" -d "$DIR"
fi
exec "$DIR/gradle-$GRADLE_VERSION/bin/gradle" "$@"
