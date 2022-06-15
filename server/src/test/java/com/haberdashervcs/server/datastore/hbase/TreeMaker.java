package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;


// TODO is this redundant w/ Changeset?
final class TreeMaker {

    private static final HdLogger LOG = HdLoggers.create(TreeMaker.class);

    private static final Splitter PATH_SPLITTER = Splitter.on('/');

    static TreeMaker ofMerged(String org, String repo, String branchName, long commitId, HBaseRawHelper helper) {
        return new TreeMaker(org, repo, branchName, commitId, helper, null);
    }

    static TreeMaker withMergeLock(
            String org, String repo, String branchName, long commitId, HBaseRawHelper helper, MergeLock mergeLock) {
        return new TreeMaker(org, repo, branchName, commitId, helper, mergeLock);
    }


    // TODO One object for org + repo?
    private final String org;
    private final String repo;
    private final String branchName;
    private final long commitId;
    private final HBaseRawHelper helper;
    private final HBaseRowKeyer rowKeyer;
    private final @Nullable MergeLock mergeLock;

    private final HashSet<String> writtenPaths = new HashSet<>();
    private final HashMultimap<String, AddedFileEntry> folderToFiles = HashMultimap.create();

    private TreeMaker(String org, String repo, String branchName, long commitId, HBaseRawHelper helper, MergeLock mergeLock) {
        this.org = org;
        this.repo = repo;
        this.branchName = branchName;
        this.commitId = commitId;
        this.helper = helper;
        this.rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        this.mergeLock = mergeLock;
    }

    private static class AddedFileEntry {
        private String name;
        private String contents;
    }

    TreeMaker addFile(String pathStr, String contents) {
        Preconditions.checkArgument(pathStr.startsWith("/"));
        Path path = Paths.get(pathStr);
        String filename = path.getFileName().toString();
        String folderPath = path.getParent().toString();
        AddedFileEntry entry = new AddedFileEntry();
        entry.name = filename;
        entry.contents = contents;
        folderToFiles.put(folderPath, entry);
        return this;
    }

    TreeMaker write() throws IOException {
        // TODO! make sure all the parent folder entries are set as well
        for (String folderPathStr : folderToFiles.keySet()) {
            addFolder(folderPathStr, folderToFiles.get(folderPathStr));
        }
        return this;
    }

    private void addFolder(String folderPathStr, Set<AddedFileEntry> filesThisFolder) throws IOException {
        List<String> parts = PATH_SPLITTER.splitToList(folderPathStr);
        String sum = "";
        for (String part : parts) {
            sum += part + "/";
            if (writtenPaths.contains(sum)) {
                continue;
            } else {
                actuallyWriteFolder(sum, filesThisFolder);
                writtenPaths.add(sum);
            }
        }
    }

    private void actuallyWriteFolder(String folderPathStr, Set<AddedFileEntry> filesThisFolder) throws IOException {
        LOG.debug("actuallyWriteFolder: %s", folderPathStr);
        Preconditions.checkArgument(folderPathStr.endsWith("/"));
        if (!folderPathStr.equals("/")) {
            folderPathStr = folderPathStr.substring(0, folderPathStr.length() - 1);
        }

        final String newFolderId = UUID.randomUUID().toString();

        // TODO! How do I add SUBFOLDERS??
        ArrayList<FolderListing.Entry> entries = new ArrayList<>();
        for (AddedFileEntry added : filesThisFolder) {
            String fileId = UUID.randomUUID().toString();
            FolderListing.Entry entry = FolderListing.Entry.forFile(added.name, fileId);
            entries.add(entry);

            FolderListing folder;
            if (mergeLock == null) {
                folder = FolderListing.withoutMergeLock(entries, folderPathStr, branchName, this.commitId);
            } else {
                folder = FolderListing.withMergeLock(entries, folderPathStr, branchName, this.commitId, mergeLock.getId());
            }
            helper.putFolderIfNotExists(
                    rowKeyer.forFolderAt(branchName, folder.getPath(), folder.getCommitId()),
                    folder);

            helper.putFile(
                    rowKeyer.forFile(fileId),
                    FileEntry.forFullContents(added.contents.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
