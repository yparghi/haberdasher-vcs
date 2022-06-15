package com.haberdashervcs.client.commit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.crawl.LocalChangeHandler;
import com.haberdashervcs.client.crawl.LocalComparisonToCommit;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchAndCommit;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;


// TODO: Tests with jimfs?
final class CommitChangeHandler implements LocalChangeHandler {

    private static final HdLogger LOG = HdLoggers.create(CommitChangeHandler.class);


    private final LocalDb db;
    private final BranchAndCommit currentBranch;
    private final List<CommitEntry.CommitChangedPath> changedPaths;

    CommitChangeHandler(LocalDb db, BranchAndCommit currentBranch) {
        this.db = db;
        this.currentBranch = currentBranch;
        this.changedPaths = new ArrayList<>();
    }


    @Override
    public void handleComparisons(
            HdFolderPath parentFolderPath,
            List<LocalComparisonToCommit> comparisons,
            LocalChangeCrawler.CrawlEntry crawlEntry)
            throws IOException {
        LOG.debug("Handling comparison in %s", parentFolderPath);
        ArrayList<FolderListing.Entry> entriesThisFolder = new ArrayList<>();
        boolean newFolderShouldBeWritten = false;

        for (LocalComparisonToCommit comparison : comparisons) {

            if (comparison.getEntryInCommit() == null
                    && comparison.getPathInLocalRepo().toFile().isFile()) {
                String filePath = parentFolderPath.filePathForName(comparison.getName());
                LOG.debug("New file: %s", filePath);
                newFolderShouldBeWritten = true;
                String hash = db.putNewFile(comparison.getPathInLocalRepo());
                changedPaths.add(CommitEntry.CommitChangedPath.added(filePath, hash));
                entriesThisFolder.add(FolderListing.Entry.forFile(comparison.getName(), hash));


            } else if (comparison.getEntryInCommit() == null
                    && comparison.getPathInLocalRepo().toFile().isDirectory()) {
                LOG.debug("New folder entry: %s", parentFolderPath.forFolderListing() + comparison.getName() + "/");
                newFolderShouldBeWritten = true;
                entriesThisFolder.add(FolderListing.Entry.forSubFolder(comparison.getName()));


            } else if (comparison.getEntryInCommit() != null
                    && comparison.getPathInLocalRepo() == null) {
                String hdPath = parentFolderPath.forFolderListing() + comparison.getName();
                LOG.debug("Deleted file or folder: %s", hdPath);
                newFolderShouldBeWritten = true;
                if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FILE) {
                    changedPaths.add(
                            CommitEntry.CommitChangedPath.deleted(hdPath, comparison.getEntryInCommit().getId()));
                }


            } else if (comparison.getEntryInCommit() != null
                    && comparison.getPathInLocalRepo() != null
                    && comparison.getPathInLocalRepo().toFile().isFile()) {
                String repoFilePath = parentFolderPath.filePathForName(comparison.getName());

                if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FOLDER) {
                    LOG.debug("Replacing folder with file: %s", repoFilePath);
                    newFolderShouldBeWritten = true;
                    String hash = db.putNewFile(comparison.getPathInLocalRepo());
                    entriesThisFolder.add(FolderListing.Entry.forFile(comparison.getName(), hash));
                    changedPaths.add(CommitEntry.CommitChangedPath.added(repoFilePath, hash));


                } else {  // Existing file
                    FileEntryWithPatchedContents commitContents = db.getFile(comparison.getEntryInCommit().getId());
                    final String hash = HdHasher.hashLocalFile(comparison.getPathInLocalRepo());
                    if (!hash.equals(commitContents.getId())) {
                        LOG.debug("Adding diff for file %s: hashes %s / %s",
                                repoFilePath,
                                commitContents.getId(),
                                hash);
                        newFolderShouldBeWritten = true;
                        LOG.info("File: %s (%s...)", repoFilePath, hash.substring(0, 6));
                        db.putFileHandlingDiffEntries(
                                hash, comparison.getPathInLocalRepo(), commitContents.getEntry());
                        changedPaths.add(
                                CommitEntry.CommitChangedPath.diff(repoFilePath, commitContents.getId(), hash));

                    } else {
                        LOG.debug("Unchanged file: %s", repoFilePath);
                    }

                    entriesThisFolder.add(
                            FolderListing.Entry.forFile(comparison.getName(), hash));
                }


            } else if (comparison.getEntryInCommit() != null
                    && comparison.getPathInLocalRepo() != null
                    && comparison.getPathInLocalRepo().toFile().isDirectory()) {

                if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FILE) {
                    String filePath = parentFolderPath.filePathForName(comparison.getName());
                    LOG.debug("File replaced with folder: %s", filePath);
                    newFolderShouldBeWritten = true;
                    changedPaths.add(
                            CommitEntry.CommitChangedPath.deleted(filePath, comparison.getEntryInCommit().getId()));
                } else {
                    LOG.debug("Existing folder: %s", parentFolderPath.forFolderListing() + comparison.getName());
                }
                entriesThisFolder.add(FolderListing.Entry.forSubFolder(comparison.getName()));
            }
        }


        if (newFolderShouldBeWritten) {
            LOG.info("Changed folder: %s", parentFolderPath.forFolderListing());
            FolderListing newFolder = FolderListing.withoutMergeLock(
                    entriesThisFolder,
                    parentFolderPath.forFolderListing(),
                    currentBranch.getBranchName(),
                    currentBranch.getCommitId() + 1);
            db.putFolder(newFolder);
        }
    }


    List<CommitEntry.CommitChangedPath> getChangedPaths() {
        return changedPaths;
    }

}
