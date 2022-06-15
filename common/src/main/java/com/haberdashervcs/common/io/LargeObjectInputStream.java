package com.haberdashervcs.common.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class LargeObjectInputStream extends InputStream {

    private static final HdLogger LOG = HdLoggers.create(LargeObjectInputStream.class);


    private String objectId;
    private final InputStream underlying;
    private final int bytesToRead;

    private int bytesReadSoFar = 0;
    private boolean isClosed = false;

    LargeObjectInputStream(String objectId, InputStream underlying, int bytesToRead) {
        this.objectId = objectId;
        this.underlying = underlying;
        this.bytesToRead = bytesToRead;
    }


    @Override
    public int read() throws IOException {
        if (bytesReadSoFar >= bytesToRead) {
            return -1;
        }

        int nextByte = underlying.read();
        if (nextByte == -1) {
            throw new EOFException("Unexpected EOF in large object stream at byte " + bytesReadSoFar);
        }
        ++bytesReadSoFar;
        return nextByte;
    }


    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }


    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (bytesReadSoFar >= bytesToRead) {
            return -1;
        }

        int lengthToRead = Math.min(length, (bytesToRead - bytesReadSoFar));
        int bytesRead = underlying.read(buffer, offset, lengthToRead);
        if (bytesRead >= 0) {
            bytesReadSoFar += bytesRead;
        }
        return bytesRead;
    }


    @Override
    public int available() throws IOException {
        return bytesToRead - bytesReadSoFar;
    }


    @Override
    public void close() throws IOException {
        if (isClosed) {
            return;
        }

        if (bytesReadSoFar != bytesToRead) {
            throw new IllegalStateException(String.format(
                    "Not all object stream bytes were consumed: %d of %d",
                    bytesReadSoFar, bytesToRead));
        }

        isClosed = true;
    }


    @Override
    public boolean markSupported() {
        return false;
    }


    public String getObjectId() {
        return objectId;
    }


    boolean isClosed() {
        return isClosed;
    }

}
