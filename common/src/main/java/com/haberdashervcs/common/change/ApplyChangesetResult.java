package com.haberdashervcs.common.change;

import com.google.common.base.Preconditions;


public final class ApplyChangesetResult {

    public enum Status {
        OK,
        FAILED
    }

    public static ApplyChangesetResult successful(String commitId) {
        return new ApplyChangesetResult(Status.OK, commitId);
    }

    public static ApplyChangesetResult failed() {
        return new ApplyChangesetResult(Status.FAILED, null);
    }


    private final Status status;
    private final String commitId;

    private ApplyChangesetResult(Status status, String commitId) {
        this.status = status;
        this.commitId = commitId;
    }

    public Status getStatus() {
        return status;
    }

    public String getCommitId() {
        Preconditions.checkState(status == Status.OK);
        Preconditions.checkNotNull(commitId);
        return commitId;
    }
}
