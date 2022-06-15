package com.haberdashervcs.common.change;


public final class RebaseSpec {

    public static RebaseSpec toNewBaseCommit(String newBaseCommitId) {
        return new RebaseSpec(newBaseCommitId);
    }

    private final String newBaseCommitId;

    private RebaseSpec(String newBaseCommitId) {
        this.newBaseCommitId = newBaseCommitId;
    }

    public String getNewBaseCommitId() {
        return newBaseCommitId;
    }
}
