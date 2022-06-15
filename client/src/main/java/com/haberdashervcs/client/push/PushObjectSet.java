package com.haberdashervcs.client.push;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.haberdashervcs.client.localdb.FileEntryWithRawContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDbRowKeyer;
import com.haberdashervcs.common.io.HdObjectOutputStream;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;
import com.haberdashervcs.common.protobuf.ServerProto;


public final class PushObjectSet {

    private static final HdLogger LOG = HdLoggers.create(PushObjectSet.class);


    private final LocalBranchState localBranch;
    private final long serverHeadCommitId;
    private final LocalDb db;
    private final Set<String> maybeNewFileIds;
    private final List<FolderListing> newFolders;
    private final List<CommitEntry> newCommits;

    PushObjectSet(LocalBranchState localBranch, long serverHeadCommitId, LocalDb db) {
        this(
                localBranch,
                serverHeadCommitId,
                db,
                new HashSet<>(),
                new ArrayList<>(),
                new ArrayList<>());
    }

    private PushObjectSet(
            LocalBranchState localBranch,
            long serverHeadCommitId,
            LocalDb db,
            Set<String> maybeNewFileIds,
            List<FolderListing> newFolders,
            List<CommitEntry> newCommits) {
        this.localBranch = localBranch;
        this.serverHeadCommitId = serverHeadCommitId;
        this.db = db;
        this.maybeNewFileIds = maybeNewFileIds;
        this.newFolders = newFolders;
        this.newCommits = newCommits;
    }

    public Set<String> getMaybeNewFileIds() {
        return maybeNewFileIds;
    }

    public List<FolderListing> getNewFolders() {
        return newFolders;
    }

    public List<CommitEntry> getNewCommits() {
        return newCommits;
    }

    public String getDebugString() {
        return MoreObjects.toStringHelper(this)
                .add("newCommits", newCommits)
                .add("newFolders", newFolders)
                .add("maybeNewFileIds", maybeNewFileIds)
                .toString();
    }


    void search() {
        List<CommitEntry> newCommitsInDb = db.getCommitsSince(localBranch.getBranchName(), serverHeadCommitId);
        LOG.info("Found %d new %s.", newCommitsInDb.size(), (newCommitsInDb.size() == 1) ? "commit" : "commits");

        for (CommitEntry commit : newCommitsInDb) {
            newCommits.add(commit);
        }


        HashSet<HdFolderPath> seenPaths = new HashSet<>();
        LinkedList<HdFolderPath> pathsToCrawl = new LinkedList<>();
        for (String checkedOutPath : db.getGlobalCheckedOutPaths().toList()) {
            pathsToCrawl.add(HdFolderPath.fromFolderListingFormat(checkedOutPath));
        }

        while (!pathsToCrawl.isEmpty()) {
            HdFolderPath hdPath = pathsToCrawl.pop();
            if (seenPaths.contains(hdPath)) {
                throw new AssertionError("Path crawled twice: " + hdPath);
            }
            seenPaths.add(hdPath);
            LOG.debug("Push: Looking for folders at path: %s", hdPath);

            // We use a set here because we're looking at multiple FolderListings (across different commits) for the
            //     same path.
            HashSet<HdFolderPath> newPathsThisIteration = new HashSet<>();

            // Because subfolders may have pushable changes while a parent folder doesn't, we have
            // to crawl even FolderListings that haven't changed since the last pushed commit.
            Optional<FolderListing> baseFolderAtCommit = db.findFolderAt(
                    localBranch.getBranchName(),
                    hdPath.forFolderListing(),
                    serverHeadCommitId);
            if (!baseFolderAtCommit.isPresent()) {
                baseFolderAtCommit = db.findFolderAt(
                        "main",
                        hdPath.forFolderListing(),
                        localBranch.getBaseCommitId());
            }

            if (baseFolderAtCommit.isPresent()) {
                for (FolderListing.Entry entry : baseFolderAtCommit.get().getEntries()) {
                    if (entry.getType() == FolderListing.Entry.Type.FOLDER) {
                        HdFolderPath subpath = hdPath.joinWithSubfolder(entry.getName());
                        if (!seenPaths.contains(subpath)) {
                            newPathsThisIteration.add(subpath);
                        }
                    }
                }
            } else {
                // The folder is new since the server head commit.
            }


            List<FolderListing> listingsOnBranchThisPath = db.getListingsSinceCommit(
                    localBranch.getBranchName(),
                    hdPath.forFolderListing(),
                    serverHeadCommitId);
            LOG.debug(
                    "Found %d branch listings on path %s since commit",
                    listingsOnBranchThisPath.size(), hdPath);

            for (FolderListing listing : listingsOnBranchThisPath) {
                newFolders.add(listing);

                for (FolderListing.Entry entry : listing.getEntries()) {
                    if (entry.getType() == FolderListing.Entry.Type.FOLDER) {
                        HdFolderPath entryPath = hdPath.joinWithSubfolder(entry.getName());
                        if (!seenPaths.contains(entryPath)) {
                            newPathsThisIteration.add(entryPath);
                        }

                    } else if (entry.getType() == FolderListing.Entry.Type.FILE) {
                        LOG.debug("Search found file: %s", entry.getId());
                        addFile(entry.getId());
                    }
                }
            }

            pathsToCrawl.addAll(newPathsThisIteration);
        }
    }


    private void addFile(String fileId) {
        List<String> fileIdsWithDiffBases = db.getAllDiffEntryIds(fileId);
        maybeNewFileIds.addAll(fileIdsWithDiffBases);
    }


    public PushObjectSet filterToResponse(ServerProto.PushQueryResponse pushQueryResponse) {
        Set<String> filteredFileIds = new HashSet<>(maybeNewFileIds);
        for (String serverHasFileId : pushQueryResponse.getFileIdsServerAlreadyHasList()) {
            filteredFileIds.remove(serverHasFileId);
        }
        return new PushObjectSet(
                localBranch, serverHeadCommitId, db, filteredFileIds, newFolders, newCommits);
    }


    public void writeToStream(HdObjectOutputStream objectsOut) throws IOException  {
        final SqliteLocalDbRowKeyer rowKeyer = SqliteLocalDbRowKeyer.getInstance();

        for (CommitEntry commit : newCommits) {
            LOG.info("Pushing commit %s:%d", commit.getBranchName(), commit.getCommitId());
            objectsOut.writeCommit("TODO no commit ids", commit);
        }

        for (FolderListing folder : newFolders) {
            LOG.info("Pushing folder %s @ %s:%d", folder.getPath(), folder.getBranch(), folder.getCommitId());
            objectsOut.writeFolder("TODO  no folder ids", folder);
        }

        for (String fileId : maybeNewFileIds) {
            FileEntryWithRawContents rawFile = db.getFileWithRawContents(rowKeyer.forFile(fileId)).get();
            LOG.info("Pushing file %s", rawFile.getId());
            RandomAccessBytes rawContents = rawFile.getContents();

            final FileEntry toWrite;
            if (rawFile.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE) {
                if (rawFile.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                    toWrite = FileEntry.forDiffGit(
                            rawFile.getId(),
                            ByteArrayRandomAccessBytes.empty(),
                            rawFile.getBaseEntryId().get(),
                            FileEntry.StorageType.LARGE_FILE_STORE);
                } else {
                    toWrite = FileEntry.forFullContents(
                            rawFile.getId(),
                            ByteArrayRandomAccessBytes.empty(),
                            FileEntry.StorageType.LARGE_FILE_STORE);
                }
            } else {
                toWrite = rawFile.getEntry();
            }

            if (toWrite.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE) {
                try (InputStream inStream = RandomAccessBytes.asInputStream(rawContents)) {
                    objectsOut.writeLargeFileContents(toWrite, inStream, rawContents.length());
                }
            } else {
                objectsOut.writeFile(toWrite.getId(), toWrite);
            }
        }
    }

}
