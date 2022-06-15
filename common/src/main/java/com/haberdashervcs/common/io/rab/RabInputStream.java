package com.haberdashervcs.common.io.rab;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;


final class RabInputStream extends InputStream {

    private final RandomAccessBytes contents;
    private final int entireLength;
    private int currentPos;
    private int bufStartIdx;
    private int bufSize;
    private final byte[] buf;

    RabInputStream(RandomAccessBytes contents, int bufferSize) {
        this.contents = contents;
        this.entireLength = contents.length();
        this.currentPos = 0;
        this.bufStartIdx = 0;
        this.bufSize = -1;
        this.buf = new byte[bufferSize];
    }


    @Override
    public int read() throws IOException {
        if (currentPos >= entireLength) {
            return -1;
        }

        rebufferIfNeeded();

        byte nextByte = buf[currentPos - bufStartIdx];
        ++currentPos;
        // Convert a byte to an int correctly, to avoid signed / two's-complement interpretation of the leading bit.
        return nextByte & 0xff;
    }


    @Override
    public int read(byte[] dest) throws IOException {
        return this.read(dest, 0, dest.length);
    }


    @Override
    public int read(byte[] dest, int off, int length) throws IOException {
        if (currentPos >= entireLength) {
            return -1;
        }

        rebufferIfNeeded();

        int bufCopyStartIdx = currentPos - bufStartIdx;
        int numBytesToCopy = Math.min(bufSize - bufCopyStartIdx, length);
        // TODO: Use our own byte-specific arraycopy to avoid boxing the bytes as Objects.
        System.arraycopy(buf, bufCopyStartIdx, dest, off, numBytesToCopy);
        currentPos += numBytesToCopy;
        return numBytesToCopy;
    }


    @Override
    public int available() throws IOException {
        return entireLength - currentPos;
    }


    @Override
    public void close() throws IOException {
        // No op.
    }


    @Override
    public boolean markSupported() {
        return false;
    }


    private void rebufferIfNeeded() {
        Preconditions.checkState(currentPos >= bufStartIdx);

        if (bufSize == -1) {
            Verify.verify(currentPos == 0);
            int bytesActuallyRead = contents.readInto(0, Math.min(buf.length, entireLength), buf);
            bufStartIdx = 0;
            bufSize = bytesActuallyRead;

        } else if (currentPos < bufStartIdx + bufSize) {
            return;

        } else {
            int newStartIdx = bufStartIdx + bufSize;
            int numBytesMax = Math.min(entireLength - newStartIdx, buf.length);
            int bytesActuallyRead = contents.readInto(newStartIdx, numBytesMax, buf);
            bufStartIdx = newStartIdx;
            bufSize = bytesActuallyRead;
        }
    }

}
