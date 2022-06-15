package com.haberdashervcs.common.io;

import java.util.Arrays;


/**
 * Wraps an immutable byte array.
 */
// TODO: Delete this?
public class HdBytes {

    public static HdBytes of(byte[] contents) {
        return new HdBytes(contents);
    }


    private final byte[] contents;

    private HdBytes(byte[] contents) {
        this.contents = Arrays.copyOf(contents, contents.length);
    }

    public byte[] getRawBytes() {
        return Arrays.copyOf(contents, contents.length);
    }
}
