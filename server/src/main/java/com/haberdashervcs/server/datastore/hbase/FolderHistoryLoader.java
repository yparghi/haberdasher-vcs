package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.Optional;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;


final class FolderHistoryLoader {

    private static final HdLogger LOG = HdLoggers.create(FolderHistoryLoader.class);


    static FolderHistoryLoader forBranch(
            HBaseRowKeyer rowKeyer, String branch, HBaseRawHelper helper, MergeStates mergestates) {
        return new FolderHistoryLoader(rowKeyer, branch, helper, mergestates);
    }


    private final HBaseRowKeyer rowKeyer;
    private final String branchName;
    private final HBaseRawHelper helper;
    private final MergeStates mergeStates;

    private FolderHistoryLoader(
            HBaseRowKeyer rowKeyer, String branchName, HBaseRawHelper helper, MergeStates mergeStates) {
        this.rowKeyer = rowKeyer;
        this.branchName = branchName;
        this.helper = helper;
        this.mergeStates = mergeStates;
    }

    Optional<FolderListing> getFolderAtCommit(long commitId, String path) {
        try {
            LOG.debug("getFolderAtCommit: %s", commitId);
            return helper.getMergedFolderAtCommit(
                    rowKeyer.getOrg(), rowKeyer.getRepo(), branchName, commitId, path, mergeStates);

        } catch (IOException ioEx) {
            throw new RuntimeException(ioEx);
        }
    }
}
