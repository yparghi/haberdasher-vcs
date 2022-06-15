#!/usr/bin/env bash

set -e
set -u

THIS_DIR=$(dirname $0)
JAVA_CMD="$THIS_DIR/Haberdasher.app/Contents/Frameworks/hd-jre/bin/java"
VERSION_NUMBER="::VAR_VERSION::"

"$JAVA_CMD" \
    -Dhaberdasher.logging.path="$THIS_DIR/hd.log" \
    -jar "$THIS_DIR/Haberdasher.app/Contents/Frameworks/hd-client-$VERSION_NUMBER.jar" \
    "$@"

