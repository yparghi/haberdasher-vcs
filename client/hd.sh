#!/usr/bin/env bash

set -e

if [[ -z "$NO_BUILD" ]]; then
  NO_BUILD="";
fi
set -u

BASE=$(dirname $0)
SKIPTESTS_FLAG="-DskipTests"

if [[ -z "$NO_BUILD" ]]; then
    (cd "$BASE/../common" && mvn install "$SKIPTESTS_FLAG")
    (cd "$BASE/../client" && mvn install "$SKIPTESTS_FLAG")
    (cd "$BASE/../client" && mvn compile assembly:single)
fi

java \
    -D"haberdasher.logging.path=/tmp/hd.log" \
    -jar "$BASE/../client/target/hd-client-1.0-SNAPSHOT-jar-with-dependencies.jar" \
    "$@"
