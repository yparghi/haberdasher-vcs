package com.haberdashervcs.common.diff;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import com.haberdashervcs.common.HdConstants;


public final class HdHasher {

    private HdHasher() {
        throw new UnsupportedOperationException();
    }


    public static String hashLocalFile(Path path) throws IOException {
        try {
            byte[] buffer = new byte[1024];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream fileSource = new BufferedInputStream(Files.newInputStream(path));

            int totalBytesRead = 0;
            int bytesRead;
            while ((bytesRead = fileSource.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            fileSource.close();

            if (totalBytesRead > HdConstants.MAX_FILE_SIZE_BYTES) {
                throw new UnsupportedOperationException(String.format(
                        "Can't commit file %s of size %d. Haberdasher has a maximum file size of %s.",
                        path, totalBytesRead, HdConstants.MAX_FILE_SIZE_FOR_DISPLAY));
            }

            byte[] hash = digest.digest();
            return String.format("%064x", new BigInteger(1, hash));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }


    public static String sha256HashString(String s) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(s.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            return String.format("%064x", new BigInteger(1, hash));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

}
