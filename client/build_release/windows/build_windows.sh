#!/usr/bin/env bash
#
# Assumes you're running this in a Unix-y shell like Git Bash.
# Also assumes python is installed.

set -e
set -u
set -x

THIS_DIR=$(dirname $0)
BASE_DIR=$THIS_DIR/../../..
# TODO: Find a way to write the version number into the 'version' command's output.
SKIPTESTS_FLAG="-DskipTests"

cd $BASE_DIR
VERSION=$(< client/src/main/resources/version.txt)
VERSION=${VERSION// /-}
VERSION=${VERSION,,}  # Lower case
echo "Version: $VERSION"

MVN="$HOME/src/maven/apache-maven-3.6.3/bin/mvn"

cd common
$MVN  install "$SKIPTESTS_FLAG"

cd ../client
$MVN  install "$SKIPTESTS_FLAG"
$MVN  compile assembly:single

JAR="target/hd-client-1.0-SNAPSHOT-jar-with-dependencies.jar"
OUT_DIR="build_release/windows/build_out/haberdasher-$VERSION"

rm -rf $OUT_DIR
mkdir -p $OUT_DIR

cp -r build_release/windows/jre_out/hd-jre "$OUT_DIR/hd-jre"
cp "$JAR" "$OUT_DIR/hd-client-$VERSION.jar"

cp build_release/windows/hd_bash_command.sh $OUT_DIR/hd
sed -i 's/::VAR_VERSION::/'"$VERSION"'/g' $OUT_DIR/hd
chmod +x $OUT_DIR/hd

cp build_release/windows/hd_bat_command.bat $OUT_DIR/hd.bat
sed -i 's/::VAR_VERSION::/'"$VERSION"'/g' $OUT_DIR/hd.bat
chmod +x $OUT_DIR/hd.bat

cd build_release/windows/build_out
# The python script will add the '.zip' extension
OUT_PATH="/tmp/haberdasher-client-windows-$VERSION"
python ../make_zip.py haberdasher-$VERSION $OUT_PATH
echo "To upload: $ ./upload_windows.sh $OUT_PATH.zip"

