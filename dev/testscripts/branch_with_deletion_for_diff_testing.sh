#!/usr/bin/env bash

# Creates a branch with a deleted folder, to test the diffing logic for deletions.

set -e
set -u
set -x

bash ~/src/haberdasher/dev/testscripts/commit_basic_changes.sh

cd ~/src/hd-test-repos/basic-1

bash ~/src/haberdasher/client/hd_nobuild.sh push
bash ~/src/haberdasher/client/hd_nobuild.sh merge
bash ~/src/haberdasher/client/hd_nobuild.sh status

bash ~/src/haberdasher/client/hd_nobuild.sh branch create test_deletion_diff

rm -r subfolder
bash ~/src/haberdasher/client/hd_nobuild.sh commit "test@haberdashervcs.com" "Delete a subfolder"
bash ~/src/haberdasher/client/hd_nobuild.sh push
bash ~/src/haberdasher/client/hd_nobuild.sh status
