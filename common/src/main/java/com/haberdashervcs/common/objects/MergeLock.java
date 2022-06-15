package com.haberdashervcs.common.objects;

import java.util.concurrent.TimeUnit;

public final class MergeLock {

    public static MergeLock of(String id, String branchName, State state, long timestampMillis) {
        return new MergeLock(id, branchName, state, timestampMillis);
    }


    public enum State {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }


    private final String id;
    private final String branchName;
    private final State state;
    private final long timestampMillis;

    private MergeLock(String id, String branchName, State state, long timestampMillis) {
        this.id = id;
        this.branchName = branchName;
        this.state = state;
        this.timestampMillis = timestampMillis;
    }

    public String getId() {
        return id;
    }

    public String getBranchName() {
        return branchName;
    }

    public State getState() {
        return state;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    // TODO Should an external/configurable class determine if it's active?
    public boolean isInProgress(final long nowTs) {
        if (state != State.IN_PROGRESS) {
            return false;
        }

        // TODO! This assert also needs to happen when commmiting the merge, right?
        if (nowTs - timestampMillis > TimeUnit.MINUTES.toMillis(1)) {
            return false;
        }

        return true;
    }
}
