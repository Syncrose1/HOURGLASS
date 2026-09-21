#!/usr/bin/env bash
GRADLE_HOME="$(cd "$(dirname "$0")/gradle" && pwd)"
exec "$GRADLE_HOME/bin/gradle" "$@"
