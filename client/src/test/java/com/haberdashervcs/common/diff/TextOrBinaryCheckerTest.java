package com.haberdashervcs.common.diff;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import junit.framework.TestCase;
import org.junit.Test;


public class TextOrBinaryCheckerTest extends TestCase {

    @Test
    public void testNormalStrings() throws Exception {
        for (String s : Arrays.asList("apple", "banana", "pear")) {
            Optional<String> checkResult = TextOrBinaryChecker.convertToString(s.getBytes(StandardCharsets.UTF_8));
            assertEquals(s, checkResult.get());
        }
    }

    @Test
    public void testBasicBytes() throws Exception {
        byte[] invalid1 = bArr(0xc3);
        Optional<String> result1 = TextOrBinaryChecker.convertToString(invalid1);
        assertFalse(result1.isPresent());

        byte[] invalid2 = bArr(0xc2, 0xc2, 0xc2, 0xc2, 0xc3, 0xc3, 0xc3, 0xc3);
        Optional<String> result2 = TextOrBinaryChecker.convertToString(invalid2);
        assertFalse(result2.isPresent());
    }

    private byte[] bArr(Integer... ints) {
        byte[] out = new byte[ints.length];
        for (int i = 0; i < ints.length; ++i) {
            out[i] = (byte)((int)ints[i]);
        }
        return out;
    }
}