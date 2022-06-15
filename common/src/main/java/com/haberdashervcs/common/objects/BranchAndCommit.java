package com.haberdashervcs.common.objects;

import com.google.common.base.MoreObjects;


// TODO: Toss this class, and use more specific/native branch state objects everywhere?
public final class BranchAndCommit {

    public static BranchAndCommit of(String branchName, long commitId) {
        return new BranchAndCommit(branchName, commitId);
    }

    private final String branchName;
    private final long commitId;

    private BranchAndCommit(String currentBranch, long currentCommit) {
        this.branchName = currentBranch;
        this.commitId = currentCommit;
    }

    public String getBranchName() {
        return branchName;
    }

    public long getCommitId() {
        return commitId;
    }

    @Override
    public String toString() {
        return String.format("%s:%d", branchName, commitId);
    }
}
