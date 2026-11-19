#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
# Gradle 9.6.0 wrapper jar. services.gradle.org publishes only the checksum
# (gradle-9.6.0-wrapper.jar.sha256); the jar itself comes from the release tag.
WRAPPER_URL='https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar'
EXPECTED=497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7
if [ ! -f "$JAR" ]; then
  echo 'Baixando Gradle Wrapper oficial 9.6.0 com SHA-256 verificado...'
  TMP="${JAR}.tmp.$$"
  trap 'rm -f "$TMP"' EXIT HUP INT TERM
  curl --fail --location --silent --show-error "$WRAPPER_URL" -o "$TMP"
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$TMP" | cut -d ' ' -f1)
  else
    ACTUAL=$(shasum -a 256 "$TMP" | cut -d ' ' -f1)
  fi
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo 'ERRO: checksum do Gradle Wrapper inválido.' >&2
    exit 1
  fi
  mv "$TMP" "$JAR"
  trap - EXIT HUP INT TERM
fi
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA=java
else
  echo 'ERRO: Java não encontrado. Defina JAVA_HOME (JDK 17 ou 21) ou coloque java no PATH.' >&2
  exit 1
fi
exec "$JAVA" -cp "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
