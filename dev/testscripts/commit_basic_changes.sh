#!/usr/bin/env bash

# Commits basic changes, like a new file, new folder, deleted folder, etc.
#
# Example sync command for testing:
# $ ~/src/haberdasher/client/hd.sh sync commit_basic_changes 4

set -e
set -u
set -x

BRANCH_NAME="$1"
hd="bash $HOME/src/haberdasher/client/hd_nobuild.sh"

$hd branch create "$BRANCH_NAME"
$hd status


echo -e "COMMIT: 1\n====="
mkdir subfolder
echo "new on branch" > subfolder/new.txt
$hd commit "Add a new file"
$hd status


echo -e "COMMIT: 2\n====="
echo -n -e '\xc2\xc2\xc2\xc2' > subfolder/binary.bin
$hd commit "Add a small binary file"
$hd status


echo -e "COMMIT: 3\n====="
echo -n -e '\xc2\xc2\xc2\xc2\xc3\xc3\xc3\xc3' > subfolder/binary.bin
$hd commit "Change the small binary file"
$hd status


echo -e "COMMIT: 4\n====="
mkdir newfolder
echo "newfolder_file.txt contents" > newfolder/newfolder_file.txt
$hd commit "Add a new folder"
$hd status


echo -e "COMMIT: 5\n====="
rm -r newfolder
$hd commit "Remove the new folder"
$hd status


echo -e "COMMIT: 6\n====="
rm subfolder/new.txt
mkdir subfolder/new.txt
echo "replacing file with folder" > subfolder/new.txt/somefile.txt
$hd commit "Replace a file with a folder"
$hd status


echo -e "COMMIT: 7\n====="
rm subfolder/new.txt/somefile.txt
rmdir subfolder/new.txt
echo "replacing folder with file" > subfolder/new.txt
$hd commit "Replace a folder with a file"
$hd status


echo -e "COMMIT: 8\n====="
cp ~/src/haberdasher/dev/testscripts/large_text_file.txt subfolder/large_text_file.txt
$hd commit "Add a large file"
$hd status


echo -e "COMMIT: 9\n====="
echo -e "\nSome new text added for Haberdasher" >> subfolder/large_text_file.txt
$hd commit "Modify a large file"
$hd status


echo -e "COMMIT: 10\n====="
cp ~/src/haberdasher/dev/testscripts/large_text_file_2.txt subfolder/large_text_file.txt
$hd commit "Make a large change to a large file"
$hd status


echo -e "COMMIT: 11\n====="
cp ~/src/haberdasher/dev/testscripts/large_binary_file.bin subfolder/large_binary_file.bin
$hd commit "Add a large binary file"
$hd status


echo -e "COMMIT: 12\n====="
echo -n -e "\xc2\xc2\xc2\xc2\xc3\xc3\xc3\xc3" >> subfolder/large_binary_file.bin
$hd commit "Modify a large binary file"
$hd status
