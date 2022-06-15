package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;

import com.haberdashervcs.common.objects.BranchEntry;


/**
 * Reserves a new commit id on main for merging in a branch.
 */
// TODO: Find a way to do this with less contention.
final class HeadCommitNumberTaker {

    static HeadCommitNumberTaker forDb(HBaseRawHelper helper, HBaseRowKeyer rowKeyer) {
        return new HeadCommitNumberTaker(helper, rowKeyer);
    }


    private final HBaseRawHelper helper;
    private final HBaseRowKeyer rowKeyer;

    private HeadCommitNumberTaker(HBaseRawHelper helper, HBaseRowKeyer rowKeyer) {
        this.helper = helper;
        this.rowKeyer = rowKeyer;
    }

    // Some scattered notes:
    // - You have different merges happening at different times, and they all want to "book" a commit ID in main...
    // - So how do I "take a number" from main?
    //     - Just use checkAndMutate over and over? Like there's a "head/latest" entry in the merge locks table
    //       (separate CF?), so you pull that, add +1 to its number, and write it with checkAndMutate. If that
    //       fails, just retry with the re-pulled entry?
    //     - What about merge failure and/or clean-up?:
    //         - Don't worry about it? Just let the merge/commit counter increment even if that merge goes on to
    //           fail?
    //
    // TODO: Is there a way this logic can be used in conjunction with a logging table, that stores the commit history
    //     of a branch? (If I ever go that route, it would mean trying to "reserve" an entry from the logging table
    //     rather than the branch entry.)
    long getNewHeadCommitId() throws IOException {
        int numTries = 5;
        while (numTries > 0) {
            --numTries;
            HBaseRawHelper.BranchWithOriginalBytes main =
                    helper.getBranchWithOriginalBytes(rowKeyer.forBranch("main")).get();
            BranchEntry withIncrementedHeadCommit = BranchEntry.of(
                    main.branch.getName(),
                    main.branch.getBaseCommitId(),
                    main.branch.getHeadCommitId() + 1);
            boolean result = helper.updateBranchIfUnchanged(
                    rowKeyer.forBranch("main"), withIncrementedHeadCommit, main.originalBytes);
            if (result) {
                return withIncrementedHeadCommit.getHeadCommitId();
            } else {
                continue;
            }
        }

        throw new IOException("Failed to take a number after " + numTries + " tries! This may be due to many concurrent/contending merges.");
    }
}
