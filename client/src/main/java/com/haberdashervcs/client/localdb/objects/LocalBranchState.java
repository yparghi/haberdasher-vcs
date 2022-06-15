package com.haberdashervcs.client.localdb.objects;

import com.google.common.base.MoreObjects;


public final class LocalBranchState {

    public static LocalBranchState of(
            String branchName,
            long baseCommitId,
            long headCommitId,
            long currentlySyncedCommitId) {
        return new LocalBranchState(
                branchName, baseCommitId, headCommitId, currentlySyncedCommitId);
    }


    private final String branchName;
    private final long baseCommitId;
    private final long headCommitId;
    private final long currentlySyncedCommitId;

    private LocalBranchState(
            String branchName,
            long baseCommitId,
            long headCommitId,
            long currentlySyncedCommitId) {
        this.branchName = branchName;
        this.baseCommitId = baseCommitId;
        this.headCommitId = headCommitId;
        this.currentlySyncedCommitId = currentlySyncedCommitId;
    }

    public String getBranchName() {
        return branchName;
    }

    public long getBaseCommitId() {
        return baseCommitId;
    }

    public long getHeadCommitId() {
        return headCommitId;
    }

    public long getCurrentlySyncedCommitId() {
        return currentlySyncedCommitId;
    }

    public String getDebugString() {
        return MoreObjects.toStringHelper(this)
                .add("branchName", branchName)
                .add("baseCommitId", baseCommitId)
                .add("headCommitId", headCommitId)
                .add("currentlySyncedCommitId", currentlySyncedCommitId)
                .toString();
    }
}
