package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import javax.annotation.Nullable;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.server.datastore.HdLargeFileStore;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileAlreadyExistsException;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;


public final class HdfsLargeFileStore implements HdLargeFileStore {

    private static final HdLogger LOG = HdLoggers.create(HdfsLargeFileStore.class);


    public static HdLargeFileStore forConfiguration(Configuration conf) {
        return new HdfsLargeFileStore(conf);
    }


    private final Configuration conf;
    private @Nullable FileSystem fileSystem = null;

    private HdfsLargeFileStore(Configuration conf) {
        this.conf = conf;
    }

    @Override
    public FileWithSize getFileById(String org, String repo, String fileId) throws IOException {
        LOG.info("Getting large file: %s / %s / %s", org, repo, fileId);
        Path path = new Path(pathForFile(org, repo, fileId));
        FileStatus status = fileSystem.getFileStatus(path);
        long size = status.getLen();
        LOG.info("TEMP! Got LFS file %s / size %d", fileId, size);
        FSDataInputStream stream = fileSystem.open(path);
        return new FileWithSize(stream, size);
    }


    @Override
    public RandomAccessBytes getFileRab(String org, String repo, String fileId) throws IOException {
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public long saveFile(String org, String repo, String fileId, InputStream contents) throws IOException {
        LOG.info("Saving large file: %s / %s / %s", org, repo, fileId);
        Path path = new Path(pathForFile(org, repo, fileId));
        LOG.info("Large file hdfs path: %s", path);
        // TODO: Is this the right way? Should I force callers to make sure a file doesn't already exist?
        try {
            FSDataOutputStream outStream = fileSystem.create(path, false /* overwrite */);
            long totalBytes = 0;
            long bytesTransferred = contents.transferTo(outStream);
            totalBytes += bytesTransferred;
            LOG.info("Saved large file size %d", bytesTransferred);
            // Weird Hdfs file errors can happen (e.g. inconsistent reported file sizes, from testing) if we don't
            //     flush/close the write stream.
            outStream.flush();
            outStream.close();
            return totalBytes;
        } catch (FileAlreadyExistsException ex) {
            LOG.warn("File already exists: %s / %s / %s", org, repo, fileId);
            FileWithSize fileWithSize = getFileById(org, repo, fileId);
            return fileWithSize.getSizeInBytes();
        }
    }

    private String pathForFile(String org, String repo, String fileId) {
        // TODO: Store illegal characters somewhere central -- I need to make sure org and repo names can't contain '|'
        //     and ':' and maybe others.
        // TODO/TEMP! Use getDefaultFS ?
        return String.format("hdfs://localhost:9006/%s|%s/%s", org, repo, fileId);
    }

    @Override
    public void start() throws Exception {
        // TODO: Remove this workaround by addressing the root cause.
        //
        // We do this "nudging" of Hadoop FileSystem implementations because of an issue in mvn assembly that clobbers
        // multiple service implementations and effectively "hides" FileSystem implementation classes in the jar.
        // See: https://stackoverflow.com/questions/17265002/hadoop-no-filesystem-for-scheme-file
        conf.set("fs.hdfs.impl", LocalFileSystem.class.getName());
        conf.set("fs.hdfs.impl", DistributedFileSystem.class.getName());

        // TODO! Configurable -- how? Use the Configuration object? Pass it in directly in the ctor?
        fileSystem = FileSystem.get(new URI("hdfs://localhost:9006"), conf);
    }
}
