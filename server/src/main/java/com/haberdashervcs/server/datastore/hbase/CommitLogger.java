package com.haberdashervcs.server.datastore.hbase;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.HdFolderPath;


// TODO! tests
//
// NOTE: How would you efficiently get the history for a folder and subfolders? Do we need some secondary commit
//     history storage for "searching" like this?
final class CommitLogger {

    private static final HdLogger LOG = HdLoggers.create(CommitLogger.class);


    static CommitLogger forBranch(String branchName, HBaseRawHelper helper, HBaseRowKeyer rowKeyer) {
        return new CommitLogger(branchName, helper, rowKeyer);
    }


    private final String branchName;
    private final HBaseRawHelper helper;
    private final HBaseRowKeyer rowKeyer;

    private CommitLogger(String branchName, HBaseRawHelper helper, HBaseRowKeyer rowKeyer) {
        this.branchName = branchName;
        this.helper = helper;
        this.rowKeyer = rowKeyer;
    }

    // TODO!
    // - What about merge commits? Should they aggregate changed files?
    List<CommitEntry> getLog(HdFolderPath path, long atCommitId) {
        // The idea:
        // - Use pagination. Get N commits from helper -- I'll need to write a new method for this.
        // - Filter the commits to see if they match the path (or a subpath of path).
        // - If I'm not at 20 commits (or something arbitrary), pull another N from the helper.
        // - When do I stop? When I reach 20, or when I've pulled 1,000 or something. (This is a temporary approach.)
        int commitsMatched = 0;
        int commitsFetched = 0;
        long currentCommit = atCommitId;
        List<CommitEntry> out = new ArrayList<>();
        while (commitsMatched < 20 && commitsFetched < 1000) {
            if (currentCommit <= 0) {
                break;
            }

            final int batchSize = 50;
            List<CommitEntry> thisBatch = helper.getCommitsDescendingFrom(
                    rowKeyer.getOrg(), rowKeyer.getRepo(), branchName, currentCommit, batchSize);
            for (CommitEntry commit : thisBatch) {
                if (matches(commit, path)) {
                    out.add(commit);
                    ++commitsMatched;
                }
            }

            currentCommit -= batchSize;
            commitsFetched += batchSize;
        }

        return ImmutableList.copyOf(out);
    }


    private boolean matches(CommitEntry commit, HdFolderPath path) {
        // We special-case "/" because we want it to return even empty commits, like integration commits.
        if (path.forFolderListing().equals("/")) {
            return true;
        }

        for (CommitEntry.CommitChangedPath changedPath : commit.getChangedPaths()) {
            if (changedPath.getPath().startsWith(path.forFolderListing())) {
                return true;
            }
        }
        return false;
    }
}
