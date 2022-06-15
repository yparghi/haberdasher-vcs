package com.haberdashervcs.client.localdb.sqlite;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;


/**
 * Saves and retrieves large files on disk under .hdlocal/
 */
final class LocalFolderLargeFileStore {

    private static final String TOP_FOLDER_NAME = "large_files";


    private final Path topFolderPath;

    LocalFolderLargeFileStore(Path dotHdLocalDir) {
        this.topFolderPath = dotHdLocalDir.resolve(TOP_FOLDER_NAME);

    }


    private Path pathForId(String fileId, boolean create) throws IOException {
        Preconditions.checkArgument(fileId.length() == 64);
        // Reasoning: Dividing by the first 2 hex chars puts 1/256th of all files in each folder. So, for example,
        // 100,000 files in the store means 390 per folder (where our target is < 1000 files in a folder).
        Path subfolder = topFolderPath.resolve(fileId.substring(0, 2));
        if (create) {
            Files.createDirectories(subfolder);
        }
        return subfolder.resolve(fileId);
    }


    RandomAccessBytes get(String fileId) throws IOException {
        Path path = pathForId(fileId, false);
        File file = path.toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Large file path not found: " + path.toAbsolutePath());
        }
        return FileRandomAccessBytes.of(file);
    }


    void putRab(String fileId, RandomAccessBytes contents) throws IOException {
        if (contents.length() <= HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
            throw new IllegalStateException("Expected large file contents, got size: " + contents.length());
        }

        Path path = pathForId(fileId, true);
        // Note: We allow overwrites for the case where a checkout fails mid-stream, and the next attempt has to
        // start over.
        try (BufferedOutputStream outStream = new BufferedOutputStream(
                Files.newOutputStream(path), 65536)) {
            RandomAccessBytes.copyToStream(contents, outStream);
        }
    }


    void putStream(String fileId, InputStream contents) throws IOException {
        Path path = pathForId(fileId, true);
        try (BufferedOutputStream outStream = new BufferedOutputStream(
                Files.newOutputStream(path), 65536)) {
            long bytesWritten = contents.transferTo(outStream);
            if (bytesWritten <= HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
                throw new IllegalStateException("Expected large file contents, got size: " + bytesWritten);
            }
        }
    }

}
