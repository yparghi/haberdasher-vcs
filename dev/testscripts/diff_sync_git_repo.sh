#!/usr/bin/env bash

set -e
set -u
set -x


NTH_COMMIT="$1"

cd ~/src/hd-test-repos/test-git-import
bash ~/src/haberdasher/client/hd_nobuild.sh sync test_git_import "$NTH_COMMIT"

cd ~/src/harp
git checkout master
set +e
git branch -D TEMP-diff-git-sync
set -e

NTH_SHA=$(git rev-list master --reverse | head -n "$NTH_COMMIT" | tail -n 1)
git checkout "$NTH_SHA" -b TEMP-diff-git-sync

diff -r ~/src/harp ~/src/hd-test-repos/test-git-import -x "hdlocal" -x "hdlocal.db" -x ".git"
