package com.haberdashervcs.client.rebase;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;


// TODO: I think we can use this in 'diff' to compare arbitrary commits.
final class RebaseCrawler {

    static final class FileChange {
        final RebasePathComparison.Change change;
        final String path;
        final Optional<String> baseId;
        final Optional<String> changedId;

        private FileChange(RebasePathComparison.Change change, String path, Optional<String> baseId, Optional<String> changedId) {
            this.change = change;
            this.path = path;
            this.baseId = baseId;
            this.changedId = changedId;
        }

        RebasePathComparison.Change getChange() {
            return change;
        }

        String getPath() {
            return path;
        }

        Optional<String> getBaseId() {
            return baseId;
        }

        Optional<String> getChangedId() {
            return changedId;
        }
    }


    private static final class CrawlEntry {
        private final String path;
        private final Optional<FolderListing> left;
        private final Optional<FolderListing> right;

        private CrawlEntry(String path, Optional<FolderListing> left, Optional<FolderListing> right) {
            this.path = path;
            this.left = left;
            this.right = right;
        }
    }


    private final LocalDb db;

    private final String leftBranch;
    private final long leftHeadCommitId;
    private final long leftBaseCommitId;

    private final String rightBranch;
    private final long rightHeadCommitId;
    private final long rightBaseCommitId;

    private final LinkedList<CrawlEntry> crawlEntries;
    private final List<FileChange> result;

    RebaseCrawler(
            LocalDb db,
            String leftBranch,
            long leftHeadCommitId,
            long leftBaseCommitId,
            String rightBranch,
            long rightHeadCommitId,
            long rightBaseCommitId) {
        this.db = db;
        this.leftBranch = leftBranch;
        this.leftHeadCommitId = leftHeadCommitId;
        this.leftBaseCommitId = leftBaseCommitId;
        this.rightBranch = rightBranch;
        this.rightHeadCommitId = rightHeadCommitId;
        this.rightBaseCommitId = rightBaseCommitId;

        this.crawlEntries = new LinkedList<>();
        this.result = new ArrayList<>();
    }


    // TODO: Consider if/how we need to track folder additions or deletions.
    List<FileChange> crawl() {
        Preconditions.checkState(crawlEntries.isEmpty());
        Preconditions.checkState(result.isEmpty());

        CheckoutPathSet checkedOutPaths = db.getGlobalCheckedOutPaths();
        for (String headPath : checkedOutPaths.toList()) {
            crawlEntries.add(0, new CrawlEntry(headPath, findLeft(headPath), findRight(headPath)));
        }


        while (!crawlEntries.isEmpty()) {
            CrawlEntry thisEntry = crawlEntries.pop();
            if (thisEntry.right.isEmpty()) {
                allChanged(thisEntry.left.get(), RebasePathComparison.Change.DELETED);
            } else if (thisEntry.left.isEmpty()) {
                allChanged(thisEntry.right.get(), RebasePathComparison.Change.ADDED);
            } else {
                compare(thisEntry.left.get(), thisEntry.right.get());
            }
        }

        return result;
    }


    private Optional<FolderListing> findLeft(String path) {
        return findFolder(leftBranch, leftHeadCommitId, leftBaseCommitId, path);
    }

    private Optional<FolderListing> findRight(String path) {
        return findFolder(rightBranch, rightHeadCommitId, rightBaseCommitId, path);
    }

    private Optional<FolderListing> findFolder(String branch, long headCommitId, long baseCommitId, String path) {
        Optional<FolderListing> branchListing = db.findFolderAt(branch, path, headCommitId);
        if (branchListing.isEmpty() && !branch.equals("main")) {
            branchListing = db.findFolderAt("main", path, baseCommitId);
        }
        return branchListing;
    }


    private void allChanged(
            FolderListing listing, RebasePathComparison.Change change) {
        Preconditions.checkArgument(change == RebasePathComparison.Change.ADDED
                || change == RebasePathComparison.Change.DELETED);

        for (FolderListing.Entry entry : listing.getEntries()) {
            if (entry.getType() == FolderListing.Entry.Type.FILE) {
                result.add(new FileChange(
                        change,
                        filePath(listing, entry),
                        (change == RebasePathComparison.Change.ADDED) ? Optional.empty() : Optional.of(entry.getId()),
                        (change == RebasePathComparison.Change.ADDED) ? Optional.of(entry.getId()) : Optional.empty()));
            } else {
                String subfolder = subfolderPath(listing, entry);
                if (change == RebasePathComparison.Change.ADDED) {
                    crawlEntries.add(0, new CrawlEntry(subfolder, Optional.empty(), findRight(subfolder)));
                } else {
                    crawlEntries.add(0, new CrawlEntry(subfolder, findLeft(subfolder), Optional.empty()));
                }
            }
        }
    }


    private String subfolderPath(FolderListing parent, FolderListing.Entry subEntry) {
        Preconditions.checkArgument(subEntry.getType() == FolderListing.Entry.Type.FOLDER);
        return HdFolderPath.fromFolderListingFormat(parent.getPath())
                .joinWithSubfolder(subEntry.getName())
                .forFolderListing();
    }

    private String filePath(FolderListing parent, FolderListing.Entry subEntry) {
        Preconditions.checkArgument(subEntry.getType() == FolderListing.Entry.Type.FILE);
        return HdFolderPath.fromFolderListingFormat(parent.getPath())
                .filePathForName(subEntry.getName());
    }


    private void compare(FolderListing left, FolderListing right) {
        Preconditions.checkArgument(left.getPath().equals(right.getPath()));

        // Deletions
        for (FolderListing.Entry leftEntry : left.getEntries()) {
            Optional<FolderListing.Entry> rightEntry = right.getEntryForName(leftEntry.getName());
            if (rightEntry.isPresent()) {
                continue;
            }

            if (leftEntry.getType() == FolderListing.Entry.Type.FOLDER) {
                String subfolder = subfolderPath(left, leftEntry);
                crawlEntries.add(0, new CrawlEntry(subfolder, findLeft(subfolder), Optional.empty()));
            } else {
                result.add(new FileChange(
                        RebasePathComparison.Change.DELETED,
                        filePath(left, leftEntry),
                        Optional.of(leftEntry.getId()),
                        Optional.empty()));
            }
        }


        // Additions, modifications, file/folder replacements
        for (FolderListing.Entry rightEntry : right.getEntries()) {
            Optional<FolderListing.Entry> leftEntry = left.getEntryForName(rightEntry.getName());

            if (leftEntry.isEmpty() && rightEntry.getType() == FolderListing.Entry.Type.FOLDER) {
                String subfolder = subfolderPath(right, rightEntry);
                crawlEntries.add(0, new CrawlEntry(subfolder, Optional.empty(), findRight(subfolder)));

            } else if (leftEntry.isEmpty() && rightEntry.getType() == FolderListing.Entry.Type.FILE) {
                result.add(new FileChange(
                        RebasePathComparison.Change.ADDED,
                        filePath(right, rightEntry),
                        Optional.empty(),
                        Optional.of(rightEntry.getId())));

            } else if (leftEntry.get().getType() == FolderListing.Entry.Type.FOLDER
                    && rightEntry.getType() == FolderListing.Entry.Type.FOLDER) {
                String subfolder = subfolderPath(right, rightEntry);
                crawlEntries.add(0, new CrawlEntry(subfolder, findLeft(subfolder), findRight(subfolder)));

            } else if (leftEntry.get().getType() == FolderListing.Entry.Type.FOLDER
                    && rightEntry.getType() == FolderListing.Entry.Type.FILE) {
                result.add(new FileChange(
                        RebasePathComparison.Change.ADDED,
                        filePath(right, rightEntry),
                        Optional.empty(),
                        Optional.of(rightEntry.getId())));
                String subfolder = subfolderPath(left, leftEntry.get());
                crawlEntries.add(0, new CrawlEntry(subfolder, findLeft(subfolder), Optional.empty()));

            } else if (leftEntry.get().getType() == FolderListing.Entry.Type.FILE
                    && rightEntry.getType() == FolderListing.Entry.Type.FOLDER) {
                result.add(new FileChange(
                        RebasePathComparison.Change.DELETED,
                        filePath(left, leftEntry.get()),
                        Optional.of(leftEntry.get().getId()),
                        Optional.empty()));
                String subfolder = subfolderPath(right, rightEntry);
                crawlEntries.add(0, new CrawlEntry(subfolder, Optional.empty(), findRight(subfolder)));

            } else if (leftEntry.get().getType() == FolderListing.Entry.Type.FILE
                    && rightEntry.getType() == FolderListing.Entry.Type.FILE) {
                if (!leftEntry.get().getId().equals(rightEntry.getId())) {
                    result.add(new FileChange(
                            RebasePathComparison.Change.MODIFIED,
                            filePath(right, rightEntry),
                            Optional.of(leftEntry.get().getId()),
                            Optional.of(rightEntry.getId())));
                }
            }
        }
    }

}
