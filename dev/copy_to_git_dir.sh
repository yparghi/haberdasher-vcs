#!/usr/bin/env bash

set -e
set -u

GIT_OUT_REPO="${1:-$HOME/src/haberdasher-git}"

rsync --archive --progress --delete-excluded \
    ~/src/haberdasher-hd/* \
    "$GIT_OUT_REPO"/ \
    --exclude .hdlocal \
    --exclude .idea \
    --exclude '*/target' \
    --exclude '*/build_out' \
    --exclude '*/jre_out'

