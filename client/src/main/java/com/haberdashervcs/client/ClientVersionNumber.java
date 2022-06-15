package com.haberdashervcs.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public final class ClientVersionNumber {

    private static String VERSION = null;

    public static synchronized String getVersion() {
        if (VERSION != null) {
            return VERSION;
        }

        try {
            byte[] versionFileContents = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("version.txt")
                    .readAllBytes();
            VERSION = new String(versionFileContents, StandardCharsets.UTF_8);
        } catch (IOException ioEx) {
            throw new RuntimeException(ioEx);
        }

        return VERSION;
    }


    private ClientVersionNumber() {
        throw new UnsupportedOperationException();
    }

}
