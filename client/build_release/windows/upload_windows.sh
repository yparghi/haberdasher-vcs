#!/usr/bin/env bash

set -e
set -u
set -x

ZIP_PATH="$1"

GCS_PATH="gs://haberdasher-public/$(basename $ZIP_PATH)"
gsutil cp $ZIP_PATH $GCS_PATH
gsutil acl ch -u AllUsers:R "$GCS_PATH"

