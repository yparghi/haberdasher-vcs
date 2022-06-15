package com.haberdashervcs.common.io.rab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Accesses any backing data source as if it were a byte array, by an index into its bytes.
 */
public interface RandomAccessBytes {

    /**
     * Callers are responsible for buffering and closing the given OutputStream.
     */
    static void copyToStream(RandomAccessBytes contents, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int idx = 0;
        final int length = contents.length();
        while (idx < length) {
            int numBytes = Math.min(buf.length, length - idx);
            int bytesRead = contents.readInto(idx, numBytes, buf);
            out.write(buf, 0, bytesRead);
            idx += bytesRead;
        }
    }


    // Use at your own risk.
    static byte[] toByteArray(RandomAccessBytes contents) {
        final int length = contents.length();
        byte[] out = new byte[length];
        byte[] buf = new byte[1024];
        int idx = 0;

        while (idx < length) {
            int numBytes = Math.min(buf.length, length - idx);
            int bytesRead = contents.readInto(idx, numBytes, buf);
            System.arraycopy(buf, 0, out, idx, bytesRead);
            idx += bytesRead;
        }

        return out;
    }


    static InputStream asInputStream(RandomAccessBytes contents) {
        return new RabInputStream(contents, 8192);
    }


    // TODO! Toss this for readInto() ?
    byte at(int index);

    /**
     * Reads some bytes into the given buffer, starting at index 0. Returns the number of bytes actually read.
     */
    int readInto(int fromIdx, int numBytes, byte[] dest);

    int length();
}
