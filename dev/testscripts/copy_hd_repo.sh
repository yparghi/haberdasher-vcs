#!/usr/bin/env bash

set -e
set -u

DEST="$(readlink -f $1)"
echo "Using dest $DEST"

cd ~/src/haberdasher

for folder in common server client; do
    cp -r $folder $DEST/$folder
    rm -r $DEST/$folder/target
done

rm -rf $DEST/client/build_release

