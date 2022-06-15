package com.haberdashervcs.common.io.rab;


import com.google.common.base.Preconditions;

public final class ByteArrayRandomAccessBytes implements RandomAccessBytes {

    public static ByteArrayRandomAccessBytes of(byte[] bytes) {
        return new ByteArrayRandomAccessBytes(bytes);
    }

    private static final ByteArrayRandomAccessBytes EMPTY = ByteArrayRandomAccessBytes.of(new byte[0]);
    public static ByteArrayRandomAccessBytes empty() {
        return EMPTY;
    }


    private final byte[] bytes;

    private ByteArrayRandomAccessBytes(byte[] bytes) {
        this.bytes = bytes;
    }


    @Override
    public byte at(int index) {
        return bytes[index];
    }


    @Override
    public int readInto(int fromIdx, int numBytes, byte[] dest) {
        Preconditions.checkArgument(fromIdx + numBytes <= bytes.length);
        System.arraycopy(bytes, fromIdx, dest, 0, numBytes);
        return numBytes;
    }


    @Override
    public int length() {
        return bytes.length;
    }
}
