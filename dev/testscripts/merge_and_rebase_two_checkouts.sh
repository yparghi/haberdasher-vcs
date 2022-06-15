#!/usr/bin/env bash

# Tests merge and rebase logic using two separate local checkouts.

set -e
set -u
set -x

CMD_BUILD="$HOME/src/haberdasher/client/hd.sh"
CMD="$HOME/src/haberdasher/client/hd_nobuild.sh"
BASE="$HOME/src/hd-test-repos"

cd $BASE
rm -rf merge-test-1 && mkdir merge-test-1
rm -rf merge-test-2 && mkdir merge-test-2


cd $BASE/merge-test-1
"$CMD_BUILD" init localhost:15367 some_org some_org test-cli-token
~/src/haberdasher/client/hd_nobuild.sh checkout /
$CMD branch create merge-test-1

mkdir subfolder
echo "text on 1" > subfolder/file1.txt
$CMD commit "test@haberdashervcs.com" "Add a new file in branch 1"


cd $BASE/merge-test-2
"$CMD" init localhost:15367 some_org some_org test-cli-token
~/src/haberdasher/client/hd_nobuild.sh checkout /
$CMD branch create merge-test-2

mkdir subfolder
echo "text on 2" > subfolder/file2.txt
$CMD commit "test@haberdashervcs.com" "Add a new file in branch 2"


cd $BASE/merge-test-1
$CMD push
$CMD merge
$CMD status


cd $BASE/merge-test-2
$CMD push
$CMD merge
$CMD status

# TODO: Rebase
