#!/bin/sh
# Maven Wrapper script
# Automatically downloads and uses the correct Maven version

set -e

# Determine the project base directory
if [ -z "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
  export MAVEN_PROJECTBASEDIR
fi

WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Read distribution URL from properties
if [ -f "$WRAPPER_PROPERTIES" ]; then
  MAVEN_DIST_URL=$(grep 'distributionUrl' "$WRAPPER_PROPERTIES" | cut -d'=' -f2- | tr -d '\r')
fi

MAVEN_DIST_URL="${MAVEN_DIST_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip}"

# Maven home directory
MAVEN_HOME="${MAVEN_USER_HOME:-$HOME/.m2/wrapper/dists}"
MAVEN_VERSION=$(echo "$MAVEN_DIST_URL" | sed 's|.*/apache-maven-\(.*\)-bin\.zip|\1|')
MAVEN_DIR="$MAVEN_HOME/apache-maven-$MAVEN_VERSION"

# Download and extract Maven if not already present
if [ ! -d "$MAVEN_DIR" ]; then
  echo "Downloading Maven $MAVEN_VERSION..."
  mkdir -p "$MAVEN_HOME"
  DOWNLOAD_FILE="$MAVEN_HOME/apache-maven-$MAVEN_VERSION-bin.zip"

  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "$DOWNLOAD_FILE" "$MAVEN_DIST_URL"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$DOWNLOAD_FILE" "$MAVEN_DIST_URL"
  else
    echo "Error: curl or wget is required to download Maven" >&2
    exit 1
  fi

  unzip -q -d "$MAVEN_HOME" "$DOWNLOAD_FILE"
  rm -f "$DOWNLOAD_FILE"
  chmod +x "$MAVEN_DIR/bin/mvn"
fi

# Execute Maven
exec "$MAVEN_DIR/bin/mvn" "$@"
