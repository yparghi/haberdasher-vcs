package com.haberdashervcs.client.checkout;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.crawl.LocalChangeHandler;
import com.haberdashervcs.client.crawl.LocalComparisonToCommit;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;


public final class SyncToCommitChangeHandler implements LocalChangeHandler {

    private static final HdLogger LOG = HdLoggers.create(SyncToCommitChangeHandler.class);


    private final RepoConfig config;
    private final LocalDb db;

    public SyncToCommitChangeHandler(RepoConfig config, LocalDb db) {
        this.config = config;
        this.db = db;
    }


    @Override
    public void handleComparisons(
            HdFolderPath path, List<LocalComparisonToCommit> comparisons, LocalChangeCrawler.CrawlEntry crawlEntry)
            throws IOException {
        for (LocalComparisonToCommit comparison : comparisons) {
            if (comparison.getPathInLocalRepo() != null) {
                handleLocalPathExists(comparison);
            } else {
                handleNewFromCommit(path, comparison);
            }
        }
    }


    private void deleteFile(Path path) throws IOException {
        Files.delete(path);
    }


    private void handleLocalPathExists(LocalComparisonToCommit comparison) throws IOException {
        final Path localFile = comparison.getPathInLocalRepo();
        final FolderListing.Entry entryInCommit = comparison.getEntryInCommit();

        if (localFile.toFile().isDirectory() && entryInCommit == null) {
            deleteRecursive(localFile);

        } else if (localFile.toFile().isFile() && entryInCommit == null) {
            deleteFile(localFile);

        } else if (localFile.toFile().isDirectory() && entryInCommit != null) {
            if (entryInCommit.getType() == FolderListing.Entry.Type.FILE) {
                deleteRecursive(localFile);
                FileEntryWithPatchedContents fileEntryFromCommit = db.getFile(entryInCommit.getId());
                writeFileContents(fileEntryFromCommit, localFile);
            }

        } else if (localFile.toFile().isFile() && entryInCommit != null) {
            if (entryInCommit.getType() == FolderListing.Entry.Type.FILE) {
                FileEntryWithPatchedContents fileEntryFromCommit = db.getFile(entryInCommit.getId());
                String localHash = HdHasher.hashLocalFile(localFile);
                if (!localHash.equals(entryInCommit.getId())) {
                    writeFileContents(fileEntryFromCommit, localFile);
                }

            } else {
                deleteFile(localFile);
                Files.createDirectories(localFile);
            }
        }
    }

    // Thanks to: https://stackoverflow.com/a/27917071
    private void deleteRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                deleteFile(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                deleteFile(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    private void handleNewFromCommit(HdFolderPath parentPath, LocalComparisonToCommit comparison) throws IOException {
        Path localToWrite = parentPath
                .joinWithSubfolder(comparison.getEntryInCommit().getName())
                .toLocalPathFromRoot(config.getRoot());

        if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FILE) {
            if (localToWrite.getParent() != null) {  // A top-level file has parent == null
                Files.createDirectories(localToWrite.getParent());
            }
            FileEntryWithPatchedContents commitFile = db.getFile(comparison.getEntryInCommit().getId());
            writeFileContents(commitFile, localToWrite);
        } else {
            Files.createDirectories(localToWrite);
        }
    }


    private void writeFileContents(FileEntryWithPatchedContents entry, Path path) throws IOException {
        OutputStream outStream = new BufferedOutputStream(Files.newOutputStream(path), 65536);
        try (outStream) {
            RandomAccessBytes.copyToStream(entry.getContents(), outStream);
        }
    }
}
