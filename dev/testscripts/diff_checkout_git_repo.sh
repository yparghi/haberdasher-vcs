#!/usr/bin/env bash

set -e
set -u
set -x


NTH_COMMIT="$1"

cd ~/src/hd-test-repos
rm -rf test-git-checkout
mkdir test-git-checkout
cd test-git-checkout

~/src/haberdasher/client/hd.sh init localhost:15367 some_org some_org test-cli-token
bash ~/src/haberdasher/client/hd_nobuild.sh checkout /

bash ~/src/haberdasher/client/hd_nobuild.sh sync test_git_import "$NTH_COMMIT"
~/src/haberdasher/client/hd_nobuild.sh status

cd ~/src/harp
git checkout master
set +e
git branch -D TEMP-diff-git-checkout
set -e

NTH_SHA=$(git rev-list master --reverse | head -n "$NTH_COMMIT" | tail -n 1)
git checkout "$NTH_SHA" -b TEMP-diff-git-checkout

diff -r ~/src/harp ~/src/hd-test-repos/test-git-checkout -x "hdlocal" -x "hdlocal.db" -x ".git"
