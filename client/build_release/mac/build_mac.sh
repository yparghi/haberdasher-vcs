#!/usr/bin/env zsh

set -e
set -u
set -x

THIS_DIR=$(dirname $0)
BASE_DIR=$THIS_DIR/../../..

cd $BASE_DIR
VERSION=$(< client/src/main/resources/version.txt)
VERSION=${VERSION// /-}
VERSION=${VERSION:l}  # Lower case
echo "Version: $VERSION"
CERT_NAME="$1"

SKIPTESTS_FLAG="-DskipTests"

cd common
mvn install "$SKIPTESTS_FLAG"

cd ../client
mvn install "$SKIPTESTS_FLAG"
mvn compile assembly:single
OUT_DIR="build_release/mac/build_out/haberdasher-$VERSION"
JAR="$PWD/target/hd-client-1.0-SNAPSHOT-jar-with-dependencies.jar"

cd build_release/mac
rm -rf build_out
mkdir build_out

cp -r jre_out/hd-jre build_out/hd-jre
# Rename dirs with dots in their names -- codesign treats such dirs
# incorrectly as app bundles.
for dotdir in $(ls -d build_out/hd-jre/legal/*/); do
    mv $dotdir ${dotdir//./}
done

cp "$JAR" "build_out/hd-client-$VERSION.jar"
cp hd_command.sh build_out/hd
sed -i '' 's/::VAR_VERSION::/'"$VERSION"'/g' build_out/hd
chmod +x build_out/hd

# Build the app bundle
mkdir build_out/Haberdasher.app
mkdir -p build_out/Haberdasher.app/Contents/Frameworks

mv build_out/hd-client-$VERSION.jar build_out/Haberdasher.app/Contents/Frameworks/.
mv build_out/hd-jre build_out/Haberdasher.app/Contents/Frameworks/.
cp Info.plist build_out/Haberdasher.app/Contents/Info.plist

cd build_out
./hd version
rm hd.log

chmod -R +rw Haberdasher.app hd
for file in $(find Haberdasher.app -type f); do
    codesign --force --options runtime --timestamp --entitlements ../entitlements.xml --sign "$CERT_NAME" $file
done

# Sign the jni libs in the jar, the hard way
md5 < Haberdasher.app/Contents/Frameworks/hd-client-$VERSION.jar
jar xf Haberdasher.app/Contents/Frameworks/hd-client-$VERSION.jar org/sqlite/native/Mac/{aarch64,x86_64}/libsqlitejdbc.jnilib
find org -type f | xargs codesign --force --options runtime --timestamp --entitlements ../entitlements.xml --sign "$CERT_NAME"
jar uf Haberdasher.app/Contents/Frameworks/hd-client-$VERSION.jar org/sqlite/native/Mac/{aarch64,x86_64}/libsqlitejdbc.jnilib
md5 < Haberdasher.app/Contents/Frameworks/hd-client-$VERSION.jar

OUT_PATH="/tmp/haberdasher-client-mac-$VERSION.zip"
rm -f $OUT_PATH
zip -r $OUT_PATH Haberdasher.app

echo "To notarize: $" ./notarize_and_upload.sh $OUT_PATH

