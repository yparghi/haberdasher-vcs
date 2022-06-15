package com.haberdashervcs.common.io.rab;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;


public class RabInputStreamTest {

    @Test
    public void simpleStringReadOneByteAtATime() throws Exception {
        String text = ""
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        ByteArrayRandomAccessBytes rab = ByteArrayRandomAccessBytes.of(bytes);
        RabInputStream rabIn = new RabInputStream(rab, 8);

        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; ++i) {
            result[i] = (byte)(rabIn.read() & 0xff);
        }

        Assert.assertArrayEquals(bytes, result);
    }


    @Test
    public void simpleStringReadIntoByteArray() throws Exception {
        String text = ""
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        ByteArrayRandomAccessBytes rab = ByteArrayRandomAccessBytes.of(bytes);
        RabInputStream rabIn = new RabInputStream(rab, 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[8];
        int transferred = 0;
        while (transferred < bytes.length) {
            int bytesRead = rabIn.read(buf);
            out.write(buf, 0, bytesRead);
            if (bytesRead == -1) {
                break;
            } else {
                transferred += bytesRead;
            }
        }

        String result = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(text, result);
    }


    @Test
    public void testEmpty() throws Exception {
        ByteArrayRandomAccessBytes rab = ByteArrayRandomAccessBytes.empty();
        RabInputStream rabIn = new RabInputStream(rab, 8);

        byte[] buf = new byte[8];
        int bytesRead = rabIn.read(buf, 0, 8);
        Assert.assertEquals(-1, bytesRead);

        int readAgain = rabIn.read();
        Assert.assertEquals(-1, readAgain);
    }

}