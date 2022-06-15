#!/usr/bin/env bash

# Testing plan, Nov. 2021:
# 1. Verify (with a diff script) each commit's correctness, after running each import command.
# 2. Verify (with the same script) the correctness of a 'sync' command to each commit in the git repo.
# 3. Verify with 'checkout'.

set -e
set -u
set -x

cd ~/src/hd-test-repos
rm -rf test-git-import
mkdir test-git-import
cd test-git-import


~/src/haberdasher/client/hd.sh init localhost:15367 some_org some_org test-cli-token
bash ~/src/haberdasher/client/hd_nobuild.sh checkout /

~/src/haberdasher/client/hd_nobuild.sh branch create test_git_import
~/src/haberdasher/client/hd_nobuild.sh status

~/src/haberdasher/client/hd_nobuild.sh import_git ~/src/harp master
