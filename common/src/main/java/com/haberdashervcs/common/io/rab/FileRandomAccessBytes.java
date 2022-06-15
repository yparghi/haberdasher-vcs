package com.haberdashervcs.common.io.rab;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class FileRandomAccessBytes implements RandomAccessBytes {

    private static final HdLogger LOG = HdLoggers.create(FileRandomAccessBytes.class);

    private static final int DEFAULT_BUFSIZE = 1 * 1024 * 1024;


    // TODO: Configurable buffer size?
    public static FileRandomAccessBytes of(File file) throws IOException {
        return new FileRandomAccessBytes(file);
    }


    private final RandomAccessFile raFile;
    private final int length;
    private final BufferIndexer bufferIndexer;
    private final int bufferSize;

    private int bufferOffset = -1;
    private byte[] buffer = null;

    private FileRandomAccessBytes(File file) throws IOException {
        this.raFile = new RandomAccessFile(file, "r");
        this.length = Math.toIntExact(raFile.length());
        this.bufferSize = Math.min(this.length, DEFAULT_BUFSIZE);
        this.bufferIndexer = new BufferIndexer(bufferSize, length);
    }


    /* Some notes, basic buffering strategy:
     * - Say my buffer size is 1MB...
     * - And I access byte 10,000,000 in a 100MB file.
     * - Just extend the buffer by 1MB? You're going to go out of bounds either to the left (lower) or right (higher)...
     * - So whichever direction, add the buffer in that direction?
     * - The new buffer is *centered* on the new index? or... no?......
     * - Should I write (and test) some NextBufferIndexer class to do these calculations?...
     *     - And it also does bounds checking?
     */


    @Override
    public byte at(int index) {
        try {
            checkBuffer(index);
            return buffer[index - bufferOffset];
        } catch (IOException ioEx) {
            throw new RuntimeException(ioEx);
        }
    }


    @Override
    public int readInto(int fromIdx, int numBytes, byte[] dest) {
        try {
            checkBuffer(fromIdx);
            int bufferIdxStart = fromIdx - bufferOffset;
            // We won't rebuffer in the middle of this read, only at the start of the next one.
            numBytes = Math.min(
                    numBytes,
                    buffer.length - bufferIdxStart);
            System.arraycopy(buffer, bufferIdxStart, dest, 0, numBytes);
            return numBytes;
        } catch (IOException ioEx) {
            throw new RuntimeException(ioEx);
        }
    }


    // TODO: Smarter buffering. Use a circular buffer? Use two, where one is for expanding to the left or right from
    // the other?
    private void checkBuffer(int index) throws IOException {
        Optional<BufferIndexer.BufferRange> rebuffer = bufferIndexer.shouldRebufferFor(index);
        if (rebuffer.isPresent()) {
            bufferOffset = rebuffer.get().getLeftIdx();
            // TODO: Reduce redundant byte reads/copies.
            buffer = new byte[bufferSize];
            raFile.seek(bufferOffset);
            raFile.readFully(buffer);
        }
    }


    @Override
    public int length() {
        return length;
    }

}
