package com.haberdashervcs.common.objects;


import com.google.common.base.MoreObjects;

public final class BranchEntry {

    public static BranchEntry of(String name, long baseCommitId, long headCommitId) {
        return new BranchEntry(name, baseCommitId, headCommitId);
    }


    private final String name;
    private final long baseCommitId;
    private final long headCommitId;

    private BranchEntry(String name, long baseCommitId, long headCommitId) {
        this.name = name;
        this.baseCommitId = baseCommitId;
        this.headCommitId = headCommitId;
    }

    public String getName() {
        return name;
    }

    public long getBaseCommitId() {
        return baseCommitId;
    }

    public long getHeadCommitId() {
        return headCommitId;
    }

    public String getDebugString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("baseCommitId", baseCommitId)
                .add("headCommitId", headCommitId)
                .toString();
    }
}
