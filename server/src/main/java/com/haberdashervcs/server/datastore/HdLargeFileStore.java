package com.haberdashervcs.server.datastore;

import java.io.IOException;
import java.io.InputStream;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;


public interface HdLargeFileStore {

    class FileWithSize {
        private final InputStream contents;
        private final long sizeInBytes;

        public FileWithSize(InputStream contents, long sizeInBytes) {
            this.contents = contents;
            this.sizeInBytes = sizeInBytes;
        }

        public InputStream getContents() {
            return contents;
        }

        public long getSizeInBytes() {
            return sizeInBytes;
        }
    }


    FileWithSize getFileById(String org, String repo, String fileId) throws IOException;
    RandomAccessBytes getFileRab(String org, String repo, String fileId) throws IOException;

    /**
     * Returns the number of bytes written.
     */
    long saveFile(String org, String repo, String fileId, InputStream contents) throws IOException;

    void start() throws Exception;
}
