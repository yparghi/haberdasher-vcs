package com.haberdashervcs.server.browser;

import java.nio.charset.StandardCharsets;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.diff.TextOrBinaryChecker;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;


public final class ContentsForDisplay {

    private static final int DISPLAY_BYTES = 8192;


    public static ContentsForDisplay forContents(RandomAccessBytes contents) {
        return new ContentsForDisplay(contents);
    }


    private final RandomAccessBytes contents;

    private ContentsForDisplay(RandomAccessBytes contents) {
        this.contents = Preconditions.checkNotNull(contents);
    }


    public String getDisplay() {
        int len = Math.min(DISPLAY_BYTES, contents.length());
        byte[] buf = new byte[len];
        for (int i = 0; i < len; ++i) {
            buf[i] = contents.at(i);
        }

        TextOrBinaryChecker.TextOrBinaryResult binaryCheck = TextOrBinaryChecker.check(
                ByteArrayRandomAccessBytes.of(buf));
        if (binaryCheck.isBinary()) {
            return "Binary file";
        } else {
            // TODO: Different encodings? Try to auto-detect?
            String strContents = new String(buf, StandardCharsets.UTF_8);
            if (contents.length() > len) {
                strContents += "\n(Truncated due to size)";
            }
            return strContents;
        }
    }

}
