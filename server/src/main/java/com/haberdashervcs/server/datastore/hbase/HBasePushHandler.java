package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.io.HdObjectId;
import com.haberdashervcs.common.io.HdObjectInputStream;
import com.haberdashervcs.common.io.LargeObjectInputStream;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.common.protobuf.ServerProto;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdLargeFileStore;


final class HBasePushHandler {

    private static final HdLogger LOG = HdLoggers.create(HBasePushHandler.class);


    private final HBaseRawHelper helper;
    private final HdLargeFileStore largeFileStore;
    private final OrgSubscription orgSub;

    private HBaseRowKeyer rowKeyer = null;
    private long highestSeenCommitId = -1;
    private HBaseRawHelper.BranchWithOriginalBytes dbBranchWithBytes = null;
    private String fileIdFromLargeContents = null;
    private int objectsWritten = 0;
    private long fileBytesWrittenThisPush = 0;
    private long repoSizeBytesBeforePush = -1;

    private Map<Long, CommitEntry> commitsById = new HashMap<>();

    HBasePushHandler(
            HBaseRawHelper helper,
            HdLargeFileStore largeFileStore,
            OrgSubscription orgSub) {
        this.helper = helper;
        this.largeFileStore = largeFileStore;
        this.orgSub = orgSub;
    }


    ServerProto.PushQueryResponse handleQuery(
            ServerProto.PushQuery pushQuery)
            throws IOException {
        final String branchName = pushQuery.getBranch();
        rowKeyer = HBaseRowKeyer.forRepo(pushQuery.getOrg(), pushQuery.getRepo());

        Optional<HBaseRawHelper.BranchWithOriginalBytes> oBranch =
                helper.getBranchWithOriginalBytes(rowKeyer.forBranch(branchName));
        if (oBranch.isPresent() && oBranch.get().branch.getBaseCommitId() != pushQuery.getBaseCommitId()) {
            ServerProto.PushQueryResponse response = ServerProto.PushQueryResponse.newBuilder()
                    .setResponseType(ServerProto.PushQueryResponse.ResponseType.ERROR)
                    .setMessage(String.format(
                            "Mismatched base commit ids: server's is %d and client's is %d",
                            oBranch.get().branch.getBaseCommitId(), pushQuery.getBaseCommitId()))
                    .build();
            return response;
        }

        final List<String> fileIdsClientWantsToPush = pushQuery.getFileIdsClientWantsToPushList();
        final List<String> fileIdsServerAlreadyHas = new ArrayList<>();
        for (String clientFileId : fileIdsClientWantsToPush) {
            byte[] fileRowKey = rowKeyer.forFile(clientFileId);
            // TODO: Look into making this more efficient, like with batch lookup.
            Optional<FileEntry> fileMaybe = helper.getFileMaybe(fileRowKey);
            if (fileMaybe.isPresent()) {
                fileIdsServerAlreadyHas.add(clientFileId);
            }
        }

        ServerProto.PushQueryResponse response = ServerProto.PushQueryResponse.newBuilder()
                .setResponseType(ServerProto.PushQueryResponse.ResponseType.OK)
                .setMessage("Push looks ok")
                .addAllFileIdsServerAlreadyHas(fileIdsServerAlreadyHas)
                .build();
        return response;
    }


    void writeObjects(
            String userId,
            HdObjectInputStream objectsIn)
            throws IOException {
        Preconditions.checkState(highestSeenCommitId == -1);
        Preconditions.checkState(dbBranchWithBytes == null);
        Preconditions.checkState(objectsWritten == 0);
        Preconditions.checkState(fileBytesWrittenThisPush == 0);
        Preconditions.checkState(repoSizeBytesBeforePush == -1);


        Optional<HdObjectId> next = objectsIn.next();
        if (!next.isPresent() || next.get().getType() != HdObjectId.ObjectType.PUSH_SPEC) {
            LOG.error("Expected a push spec, got %s", next);
            throw new IOException("Expected a push spec first");
        }

        final ServerProto.PushSpec pushSpec = objectsIn.getPushSpec();
        if (pushSpec.getBranch().equals("main")) {
            throw new UnsupportedOperationException("You can't push directly to main.");
        }


        // TODO: Consider branch overwriting, because of an accidental name collision. Should we
        //     use randomized alphanumeric branch id's for the actual name on the server?
        final String branchName = pushSpec.getBranch();
        rowKeyer = HBaseRowKeyer.forRepo(pushSpec.getOrg(), pushSpec.getRepo());

        repoSizeBytesBeforePush = helper.getRepoSize(rowKeyer);
        ensureRepoSizeIsAllowed(repoSizeBytesBeforePush);

        Optional<HBaseRawHelper.BranchWithOriginalBytes> oBranch =
                helper.getBranchWithOriginalBytes(rowKeyer.forBranch(branchName));
        if (oBranch.isPresent()) {
            dbBranchWithBytes = oBranch.get();
            if (dbBranchWithBytes.branch.getBaseCommitId() != pushSpec.getBaseCommitId()) {
                throw new IllegalStateException(String.format(
                        "Mismatched base commit ids: server is %d and client is %d",
                        dbBranchWithBytes.branch.getBaseCommitId(), pushSpec.getBaseCommitId()));
            }
        } else {
            BranchEntry newBranch = BranchEntry.of(branchName, pushSpec.getBaseCommitId(), 0);
            createBranch(newBranch);
        }

        LOG.info(
                "Repo (%s, %s) size before push is %d",
                rowKeyer.getOrg(), rowKeyer.getRepo(), repoSizeBytesBeforePush);

        processObjectStream(pushSpec, userId, objectsIn);

        finish(pushSpec);
    }


    private void createBranch(BranchEntry newBranch) throws IOException {
        LOG.info("Creating new branch: %s", newBranch.getDebugString());
        byte[] rowKey = rowKeyer.forBranch(newBranch.getName());
        helper.createBranch(rowKey, newBranch);
        dbBranchWithBytes = helper.getBranchWithOriginalBytes(rowKey).get();

        // We still want the branch creation to work. It's the primary op here.
        // TODO: If the review creation fails, do it lazily elsewhere. Or find a way to make this all atomic?
        try {
            ReviewsProto.ReviewContents reviewProto = ReviewsProto.ReviewContents.newBuilder()
                    .setOrg(rowKeyer.getOrg())
                    .setRepo(rowKeyer.getRepo())
                    .setThisBranch(newBranch.getName())
                    .setOtherBranch("main")
                    .setOtherBranchCommitId(newBranch.getBaseCommitId())
                    .build();
            helper.createBranchReview(reviewProto);
        } catch (Exception ex) {
            LOG.exception(ex, "Failed to create branch review for %s", newBranch.getName());
        }
    }


    private void processObjectStream(
            ServerProto.PushSpec pushSpec,
            String pushingUserId,
            HdObjectInputStream objectsIn)
            throws IOException {

        Optional<HdObjectId> next;
        while ((next = objectsIn.next()).isPresent()) {

            if (next.get().getType() == HdObjectId.ObjectType.FILE) {
                FileEntry fileEntry = objectsIn.getFile();
                writeFile(fileEntry);

            } else if (next.get().getType() == HdObjectId.ObjectType.FOLDER) {
                FolderListing folder = objectsIn.getFolder();
                writeFolder(pushSpec.getBranch(), folder);

            } else if (next.get().getType() == HdObjectId.ObjectType.COMMIT) {
                CommitEntry commit = objectsIn.getCommit();
                // Integration commits can have no changed folders -- they still count as a nonempty change.
                if (commit.getIntegration().isPresent() && commit.getCommitId() > highestSeenCommitId) {
                    highestSeenCommitId = commit.getCommitId();
                }
                writeCommit(pushingUserId, commit);

            } else if (next.get().getType() == HdObjectId.ObjectType.LARGE_FILE_CONTENTS) {
                writeLargeFileContents(objectsIn.getLargeFileContents());

            } else {
                throw new IOException("Unexpected object type: " + next.get().getType());
            }
        }
    }


    private void writeFile(FileEntry file) throws IOException {
        if (fileIdFromLargeContents != null) {
            if (!file.getId().equals(fileIdFromLargeContents)) {
                throw new IllegalStateException(String.format(
                        "Expected large file id %s but got file id %s",
                        file.getId(), fileIdFromLargeContents));

            } else if (file.getStorageType() != FileEntry.StorageType.LARGE_FILE_STORE) {
                throw new IllegalStateException("Expected large file, but got: " + file.getDebugString());
            }

            fileIdFromLargeContents = null;

        } else if (file.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE) {
            throw new IllegalStateException("Unexpected large file: " + file.getDebugString());
        }

        ++objectsWritten;
        LOG.info("Push: Got file: %s", file.getDebugString());
        helper.putFile(rowKeyer.forFile(file.getId()), file);
        incrementRepoSizeForFile(file);
    }


    private void incrementRepoSizeForFile(FileEntry entry) {
        if (entry.getStorageType() == FileEntry.StorageType.DATASTORE) {
            // TODO: Make this more efficient, so that it doesn't copy the contents array. Write file sizes in the
            // stream? And assert on write somehow?
            int entryContentsSize = entry.getEntryContents().length();
            if (entryContentsSize > HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
                throw new IllegalStateException(String.format(
                        "Unexpectedly large file entry contents (%d bytes) with storage type DATASTORE: %s",
                        entryContentsSize,
                        entry.getDebugString()));
            }
            fileBytesWrittenThisPush += entryContentsSize;
        }
        ensureRepoSizeIsAllowed(repoSizeBytesBeforePush + fileBytesWrittenThisPush);
    }


    private void writeFolder(String branchName, FolderListing folder) throws IOException {
        ++objectsWritten;
        LOG.info("Push: Got folder: %s", folder.getDebugString());
        if (folder.getCommitId() <= dbBranchWithBytes.branch.getHeadCommitId()) {
            throw new IllegalStateException(String.format(
                    "Got new folder with commit id %d for branch at %d",
                    folder.getCommitId(),
                    dbBranchWithBytes.branch.getHeadCommitId()));
        } else if (folder.getCommitId() > highestSeenCommitId) {
            highestSeenCommitId = folder.getCommitId();
        }

        helper.putFolderAllowingOverwrite(
                rowKeyer.forFolderAt(
                        branchName, folder.getPath(), folder.getCommitId()),
                folder);
    }


    private void writeCommit(String pushingUserId, CommitEntry pushedLocalCommit) throws IOException {
        ++objectsWritten;
        LOG.info("Push: Got commit: %s", pushedLocalCommit.getDebugString());
        if (pushedLocalCommit.getCommitId() <= dbBranchWithBytes.branch.getHeadCommitId()) {
            throw new IllegalStateException(String.format(
                    "Got new commit with commit id %d for branch at %d",
                    pushedLocalCommit.getCommitId(),
                    dbBranchWithBytes.branch.getHeadCommitId()));
        }


        // Set the author as the user ID. The client sets the author as a stub like "(local)".
        // TODO: Get rid of this hack on both client and server.
        CommitEntry serverCommit = pushedLocalCommit.withAuthor(pushingUserId);
        byte[] rowKey = rowKeyer.forCommit(serverCommit);
        helper.putCommit(rowKey, serverCommit);

        Verify.verify(!commitsById.containsKey(pushedLocalCommit.getCommitId()));
        commitsById.put(pushedLocalCommit.getCommitId(), serverCommit);
    }


    private void writeLargeFileContents(LargeObjectInputStream contents)
            throws IOException {
        Preconditions.checkState(fileIdFromLargeContents == null);
        fileIdFromLargeContents = contents.getObjectId();
        try (contents) {
            long size = largeFileStore.saveFile(
                    rowKeyer.getOrg(), rowKeyer.getRepo(), fileIdFromLargeContents, contents);
            incrementRepoSizeForLargeContents(size);
        }
    }


    private void incrementRepoSizeForLargeContents(long size) {
        fileBytesWrittenThisPush += size;
        ensureRepoSizeIsAllowed(repoSizeBytesBeforePush + fileBytesWrittenThisPush);
    }


    private void finish(ServerProto.PushSpec pushSpec) throws IOException {
        if (objectsWritten == 0) {
            LOG.info("No objects written -- done.");
            return;
        }

        if (pushSpec.getNewHeadCommitId() != highestSeenCommitId) {
            throw new AssertionError(String.format(
                    "The new head commit (%d) didn't match the newest folder commit (%d)",
                    pushSpec.getNewHeadCommitId(), highestSeenCommitId));
        }

        final BranchEntry dbBranch = dbBranchWithBytes.branch;
        LOG.debug("Push: Current branch state is %s", dbBranch.getDebugString());
        if (dbBranch.getBaseCommitId() != pushSpec.getBaseCommitId()) {
            throw new AssertionError(String.format(
                    "Server branch base commit (%d) doesn't match pushed branch's base commit (%d)",
                    dbBranch.getBaseCommitId(), pushSpec.getBaseCommitId()));
        }


        try {
            long repoSizeAfterPush = helper.incrementRepoSize(rowKeyer, fileBytesWrittenThisPush);
            ensureRepoSizeIsAllowed(repoSizeAfterPush);
            LOG.info(
                    "Repo (%s, %s) size after push is %d",
                    rowKeyer.getOrg(), rowKeyer.getRepo(), repoSizeAfterPush);
        } catch (Exception ex) {
            LOG.exception(ex, "Error incrementing repo size for (%s, %s)", rowKeyer.getOrg(), rowKeyer.getRepo());
        }


        // For transactionality: if this fails, or if the whole push fails, then commit/diff entries will be
        // overwritten in the next attempt.
        //
        // IDEA: Consider computing diffs in the client.
        computeDiffs(dbBranch.getName(), dbBranch.getBaseCommitId());


        // This is the final operation that seals the push "transaction" as completed, in one change.
        final BranchEntry updatedEntry = BranchEntry.of(
                dbBranch.getName(), dbBranch.getBaseCommitId(), pushSpec.getNewHeadCommitId());
        boolean branchSuccessfullyUpdated = helper.updateBranchIfUnchanged(
                rowKeyer.forBranch(dbBranch.getName()),
                updatedEntry,
                dbBranchWithBytes.originalBytes);
        if (!branchSuccessfullyUpdated) {
            throw new IOException("Branch failed to update!");
        }
    }


    private void computeDiffs(String branchName, long mainBaseCommitId) throws IOException {
       RepoBrowser.ReviewWithOriginalBytes existingReview = helper.getReview(
                rowKeyer.getOrg(),
                rowKeyer.getRepo(),
                branchName,
                "main",
                mainBaseCommitId)
                .get();
        ReviewsProto.ReviewContents.Builder updatedReview = ReviewsProto.ReviewContents.newBuilder(
                existingReview.getReview());

        HBaseCommitDiffGenerator commitDiffer = HBaseCommitDiffGenerator.of(
                branchName, helper, rowKeyer, largeFileStore);
        List<ReviewsProto.CommitDiff> newCommitDiffs = commitDiffer.diff(commitsById.values());
        updatedReview.addAllCommitDiffs(newCommitDiffs);
        helper.updateBranchReview(updatedReview.build(), existingReview);
    }


    private void ensureRepoSizeIsAllowed(long numBytes) {
        long max = orgSub.getRepoMaxSizeBytes();
        if (numBytes > max) {
            throw new IllegalStateException(String.format(
                    "Sorry, this repo has reached its maximum size (%d bytes). You can upgrade your plan to increase this limit.",
                    max));
        }
    }

}
