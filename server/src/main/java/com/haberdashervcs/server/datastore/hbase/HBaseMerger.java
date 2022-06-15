package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Verify;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.protobuf.CommitsProto;


final class HBaseMerger {

    private static final HdLogger LOG = HdLoggers.create(HBaseMerger.class);


    // TODO: Pass in the helper.
    HBaseMerger() {
    }


    // The idea:
    // - Gather *every* folder history for this branch
    // - For each path in the histories, add the new folder & a lock, using `checkAndPut` with the value pulled
    //     - Bail on this whole thing if there's already an *active* lock on one of the histories.
    // - Make it official: if you get through that whole list, mark the branch as merged.
    // - Clean-up: Go through the histories again (with their updated values). Use checkAndPut to remove the locks.
    MergeResult merge(
            String org, String repo, String branchName, long headCommitId, HBaseRawHelper helper)
            throws IOException {

        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        Optional<BranchEntry> oBranch = helper.getBranch(rowKeyer.forBranch(branchName));
        if (oBranch.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Branch %s wasn't found on the server. Has it been pushed?", branchName));
        }
        BranchEntry branchEntry = oBranch.get();

        if (branchEntry.getHeadCommitId() != headCommitId) {
            throw new IllegalStateException(String.format(
                    "The server branch head (%d) doesn't match the local branch head (%d). Have you pushed the branch?",
                    branchEntry.getHeadCommitId(), headCommitId));
        }

        final long nowTs = System.currentTimeMillis();
        MergeStates mergeStates = MergeStates.fromPastSeconds(nowTs, TimeUnit.HOURS.toSeconds(1), helper, rowKeyer);

        final List<HBaseRawHelper.FolderListingWithOriginalBytes> branchHeadHistories =
                helper.getMostRecentFoldersOnBranch(org, repo, branchName, branchEntry.getHeadCommitId());

        long mainIntegrationBaseCommit = branchEntry.getBaseCommitId();
        List<CommitEntry> commitsFromBranch = helper.getCommitsDescendingFrom(
                org, repo, branchEntry.getName(), branchEntry.getHeadCommitId(), -1);
        for (CommitEntry commit : commitsFromBranch) {
            if (commit.getIntegration().isPresent()) {
                Verify.verify(commit.getIntegration().get().getBranch().equals("main"));
                mainIntegrationBaseCommit = commit.getIntegration().get().getCommitId();
                break;
            }
        }


        // Pessimistically look for newer changes on main before trying to acquire locks.
        for (HBaseRawHelper.FolderListingWithOriginalBytes headOnBranch : branchHeadHistories) {

            List<FolderListing> newerListingsOnMain = helper.getListingsSinceCommitIgnoringMergeLocks(
                    org,
                    repo,
                    "main",
                    mainIntegrationBaseCommit,
                    headOnBranch.listing.getPath());

            for (FolderListing newerListing : newerListingsOnMain) {
                // We assume absence means the lock was completed.
                // TODO! But maybe that assumption is wrong, this all needs to be rewritten.
                Optional<String> mergeId = newerListing.getMergeLockId();
                if (mergeId.isEmpty()
                        || mergeStates.forMergeLockId(mergeId.get()).getState() != MergeLock.State.FAILED) {
                    return MergeResult.of(
                            MergeResult.ResultType.FAILED,
                            String.format(
                                    "A folder has changed on both main and the branch (%s). The branch should be rebased before trying to merge again.",
                                    newerListing.getPath()),
                            -1);
                }
            }
        }





        // TODO: Consider this race condition:
        // - I'm trying to merge this branch whose base is main:10.
        // - The loop above finds no commits on main since main:10, so the merge proceeds.
        // - Between the code above and the code below, another merge sneaks in and writes a listing for main:11.
        //     - And the listing at main:11 happens to conflict with this branch!
        // - So below, I take a number (12) without realizing 11 has snuck in with a conflict.
        //     - And my write of the listing at main:12 clobbers the write at main:11 !
        //
        //
        // How do I take care of this?:
        // - I have to lock a whole path/folder globally on main, not just this or that listing...
        // - So, some lookup: path -> pathLock.
        // - Before merging, grab all path locks REQUIRING they are not newer than the base commit:
        //     - First look them all up pessimistically, looking for newer merges, so I can fail fast here.
        //     - Then grab them all again, one at a time, and use a put-if-not-exists or put-if-unchanged to
        //       lock them for this merge.
        // (- Maybe we can reuse MergeLock for this somehow.)
        // - Write all listings at the new commit, as I'm currently doing below.
        //
        // - On clean-up, complete/release the path locks.
        //     - TODO: Make sure this doesn't block a path permanently if something goes wrong!...





        // Now, lock the folders.
        MergeLock newLock = MergeLock.of(
                UUID.randomUUID().toString(),
                branchName,
                MergeLock.State.IN_PROGRESS,
                nowTs);
        helper.putMerge(rowKeyer, newLock);

        final HeadCommitNumberTaker numberTaker = HeadCommitNumberTaker.forDb(helper, rowKeyer);
        // This also updates the BranchEntry for main.
        final long wouldBeNewCommitIdOnMain = numberTaker.getNewHeadCommitId();

        for (
                int i = 0; i < branchHeadHistories.size(); ++i) {
            HBaseRawHelper.FolderListingWithOriginalBytes headOnBranch = branchHeadHistories.get(i);

            FolderListing newListing = FolderListing.withMergeLock(
                    headOnBranch.listing.getEntries(),
                    headOnBranch.listing.getPath(),
                    "main",
                    wouldBeNewCommitIdOnMain,
                    newLock.getId());

            helper.putFolderIfNotExists(
                    rowKeyer.forFolderAt("main", newListing.getPath(), newListing.getCommitId()),
                    newListing);
        }


        // Mark the merge as complete to officially make it part of main's history.
        MergeLock completed = MergeLock.of(
                newLock.getId(), newLock.getBranchName(), MergeLock.State.COMPLETED, newLock.getTimestampMillis());
        helper.putMerge(rowKeyer, completed);


        // Write a commit entry for main.
        List<CommitEntry.CommitChangedPath> changedFilesAcrossBranch = new ArrayList<>();
        for (CommitEntry branchCommit : commitsFromBranch) {
            changedFilesAcrossBranch.addAll(branchCommit.getChangedPaths());
        }
        // We're cheating a little by using the BranchIntegration on a main commit to store the merged-in branch.
        CommitsProto.BranchIntegration mergedInBranch = CommitsProto.BranchIntegration.newBuilder()
                .setBranch(branchEntry.getName())
                .setCommitId(branchEntry.getHeadCommitId())
                .build();

        String commitMessage = String.format(
                "Merge of branch %s:%d", branchEntry.getName(), branchEntry.getHeadCommitId());
        CommitEntry newCommitOnMain = CommitEntry.of(
                "main", wouldBeNewCommitIdOnMain, "Haberdasher Merge", commitMessage, changedFilesAcrossBranch)
                .withIntegration(mergedInBranch);
        byte[] commitKey = rowKeyer.forCommit(newCommitOnMain);
        helper.putCommit(commitKey, newCommitOnMain);
        LOG.info(
                "Wrote merge commit: %s",
                newCommitOnMain.getDebugString());


        // TODO: Clean-up: Go through the histories again (with their updated values). Use checkAndMutate to remove the
        //     locks.
        return MergeResult.of(
                MergeResult.ResultType.SUCCESSFUL,
                "Successfully merged into main.",
                wouldBeNewCommitIdOnMain);
    }

}
