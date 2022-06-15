#!/usr/bin/env bash

set -e
set -u
set -x

THIS_DIR=$(dirname $0)
cd $THIS_DIR/build_out

ZIP_PATH="$1"

xcrun notarytool submit --keychain-profile "yashnotary" --wait $ZIP_PATH

rm $ZIP_PATH
zip -r $ZIP_PATH hd Haberdasher.app

GCS_PATH="gs://haberdasher-public/$(basename $ZIP_PATH)"
gsutil cp $ZIP_PATH $GCS_PATH
gsutil acl ch -u AllUsers:R "$GCS_PATH"

