package com.haberdashervcs.common.diff;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;


public final class TextOrBinaryChecker {


    public static final class TextOrBinaryResult {

        private final boolean isText;

        private TextOrBinaryResult(boolean isText) {
            this.isText = isText;
        }

        public boolean isText() {
            return isText;
        }

        public boolean isBinary() {
            return !isText;
        }
    }


    /**
     * Just checks for any null (\0) byte, like Git does.
     */
    public static TextOrBinaryResult check(RandomAccessBytes contents) {
        final int bufLen = Math.min(8192, contents.length());
        for (int i = 0; i < bufLen; ++i) {
            if (contents.at(i) == 0) {
                return new TextOrBinaryResult(false);
            }
        }
        return new TextOrBinaryResult(true);
    }


    public static TextOrBinaryResult check(byte[] contents) {
        for (int i = 0; i < contents.length; ++i) {
            if (contents[i] == 0) {
                return new TextOrBinaryResult(false);
            }
        }
        return new TextOrBinaryResult(true);
    }


    // TODO: Something more rigorous
    // TODO: Toss this and use RAB method instead, everywhere? Or, port this logic to the RAB method?
    public static Optional<String> convertToString(byte[] contents) {

        if (contents.length == 0) {
            return Optional.of("");
        }

        // Temporary solution (for example it may judge UTF-16 to be binary), thanks to:
        //     https://stackoverflow.com/a/13533390
        int ascii = 0;
        int other = 0;

        for(int i = 0; i < contents.length; i++) {
            byte b = contents[i];
            if (b < 0x09) {
                other++;
            } else if (b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D) {
                ascii++;
            } else if (b >= 0x20  &&  b <= 0x7E) {
                ascii++;
            } else {
                other++;
            }
        }

        float percentOther = other / ((float) (contents.length));
        percentOther *= 100.0;
        //System.out.println("TEMP! score: " + percentOther);

        if (percentOther > 5.0) {
            return Optional.empty();
        } else {
            return Optional.of(new String(contents, StandardCharsets.UTF_8));
        }
    }
}
