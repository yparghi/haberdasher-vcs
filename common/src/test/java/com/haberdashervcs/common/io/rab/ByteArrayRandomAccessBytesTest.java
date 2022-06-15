package com.haberdashervcs.common.io.rab;

import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;


public class ByteArrayRandomAccessBytesTest extends TestCase {

    public void testReadInto() throws Exception {
        String text = ""
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayRandomAccessBytes rab = ByteArrayRandomAccessBytes.of(bytes);

        byte[] buf = new byte[16];
        int bytesRead = rab.readInto(0, 16, buf);
        assertEquals(16, bytesRead);
        for (int i = 0; i < bytesRead; ++i) {
            assertEquals("i = " + i, text.charAt(i), buf[i]);
        }

        bytesRead = rab.readInto(16, bytes.length - 16, buf);
        assertEquals(bytes.length - 16, bytesRead);
        for (int i = 0; i < bytesRead; ++i) {
            assertEquals("i = " + i, text.charAt(16 + i), buf[i]);
        }
    }

}