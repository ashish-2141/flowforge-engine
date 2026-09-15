#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION="3.9.9"
MAVEN_HOME_DIR="${BASE_DIR}/.mvn/wrapper/apache-maven-${MAVEN_VERSION}"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

MAVEN_ARCHIVE="${BASE_DIR}/.mvn/wrapper/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

if [ ! -x "${MAVEN_HOME_DIR}/bin/mvn" ]; then
  mkdir -p "${BASE_DIR}/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "${MAVEN_URL}" -o "${MAVEN_ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "${MAVEN_URL}" -O "${MAVEN_ARCHIVE}"
  else
    echo "Maven is not installed and neither curl nor wget is available." >&2
    exit 1
  fi
  tar -xzf "${MAVEN_ARCHIVE}" -C "${BASE_DIR}/.mvn/wrapper"
  rm -f "${MAVEN_ARCHIVE}"
fi

exec "${MAVEN_HOME_DIR}/bin/mvn" "$@"
