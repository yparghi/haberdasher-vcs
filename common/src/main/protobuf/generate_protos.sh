#!/usr/bin/env bash

# NOTE: We *do* check in generated code, to mitigate different people using different versions of protoc. (Maybe this
# isn't really necessary, but it's what I think for now.)

set -u
set -e
set -o pipefail
set -x

PROTOC_PATH="$1"
PROTO_DIR="$(dirname $0)"

"$PROTOC_PATH" \
    --proto_path="$PROTO_DIR" \
    --java_out="common/src/main/java" \
    branches.proto \
    commits.proto \
    files.proto \
    folders.proto \
    localdb.proto \
    merges.proto \
    repos.proto \
    reviews.proto \
    server.proto \
    tasks.proto \
    users.proto

