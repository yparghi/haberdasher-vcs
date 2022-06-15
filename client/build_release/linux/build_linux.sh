#!/usr/bin/env bash

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

cd common
mvn install "$SKIPTESTS_FLAG"

cd ../client
mvn install "$SKIPTESTS_FLAG"
mvn compile assembly:single

JAR="target/hd-client-1.0-SNAPSHOT-jar-with-dependencies.jar"
OUT_DIR="build_release/linux/build_out/haberdasher-$VERSION"

rm -rf $OUT_DIR
mkdir -p $OUT_DIR

cp -r build_release/linux/jre_out/hd-jre "$OUT_DIR/hd-jre"
cp "$JAR" "$OUT_DIR/hd-client-$VERSION.jar"
cp build_release/linux/hd_command.sh $OUT_DIR/hd
sed -i 's/::VAR_VERSION::/'"$VERSION"'/g' $OUT_DIR/hd
chmod +x $OUT_DIR/hd

cd build_release/linux/build_out
OUT_PATH="/tmp/haberdasher-client-linux-$VERSION.tar.gz"
tar cz haberdasher-$VERSION > "$OUT_PATH"
echo "To upload: $ ./upload_linux.sh $OUT_PATH"

