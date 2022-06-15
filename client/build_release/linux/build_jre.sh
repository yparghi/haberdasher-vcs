#!/usr/bin/env bash

set -e
set -u
set -x

THIS_DIR=$(dirname $0)

rm -rf $THIS_DIR/jre_out
mkdir -p $THIS_DIR/jre_out
cd $THIS_DIR/jre_out

# TODO: Investigate jlink options to lower the runtime size.
#
# NOTE for Linux: The openjdk that I got from Debian apt-get creates HUGE jre's.
# I specifically use the one downloaded from jdk.java.net to avoid that.
#
# These modules were found purely by trial and error. Note the jdk.crypo.ec
# module is added because I got weird SSL errors (handshake_failure) when using
# the mac client.
~/src/java/jdk-11/bin/jlink --output hd-jre --add-modules java.base,java.sql,java.desktop,jdk.crypto.ec

