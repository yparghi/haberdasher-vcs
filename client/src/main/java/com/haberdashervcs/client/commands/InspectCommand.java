package com.haberdashervcs.client.commands;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDbRowKeyer;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;


final class InspectCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(InspectCommand.class);


    private final RepoConfig repoConfig;
    private final LocalDb db;
    private final List<String> otherArgs;

    InspectCommand(RepoConfig repoConfig, LocalDb db, List<String> otherArgs) {
        this.repoConfig = repoConfig;
        this.db = db;
        this.otherArgs = otherArgs;
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() < 1) {
            throw new IllegalArgumentException("Usage: hd inspect <file / folder> ...");
        }

        db.startTransaction();

        if (otherArgs.get(0).equals("folder")) {
            handleFolder();
        } else if (otherArgs.get(0).equals("file")) {
            handleFile();
        } else {
            throw new IllegalArgumentException("Unknown operation: " + otherArgs.get(0));
        }

        db.cancelTransaction();
    }


    private void handleFile() throws Exception {
        if (otherArgs.size() != 2) {
            throw new IllegalArgumentException("Usage: hd inspect file <file id>");
        }

        String fileId = otherArgs.get(1);
        FileEntryWithPatchedContents file = db.getFile(SqliteLocalDbRowKeyer.getInstance().forFile(fileId));

        Path contentsOut = Files.createTempFile("hd-inspect-", "");
        OutputStream outStream = new BufferedOutputStream(
                Files.newOutputStream(contentsOut, StandardOpenOption.CREATE),
                65536);
        try (outStream) {
            RandomAccessBytes.copyToStream(file.getContents(), outStream);
        }

        // TODO: A flag for actually copying out the file.
        LOG.info("Contents written to: " + contentsOut.toAbsolutePath().toString());
    }


    private void handleFolder() throws Exception {
        if (otherArgs.size() != 4) {
            throw new IllegalArgumentException("Usage: hd inspect folder <branch> <path> <commit>");
        }

        String branchName = otherArgs.get(1);
        String path = otherArgs.get(2);
        long commitId = Long.valueOf(otherArgs.get(3));

        Optional<FolderListing> result = db.findFolderAt(branchName, path, commitId);
        if (result.isEmpty()) {
            LOG.info("No folder found.");
        } else {
            LOG.info("Found folder: " + result.get().getDebugString());
        }
    }
}
