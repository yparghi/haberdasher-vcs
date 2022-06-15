package com.haberdashervcs.common.io.rab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class FileRandomAccessBytesTest {

    @Test
    public void readInto() throws Exception {
        String text = ""
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        Path tempFile = Files.createTempFile("hd-test-filerab-", ".txt");
        Files.write(tempFile, bytes);
        FileRandomAccessBytes rab = FileRandomAccessBytes.of(tempFile.toFile());

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