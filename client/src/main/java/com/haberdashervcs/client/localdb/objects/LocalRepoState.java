package com.haberdashervcs.client.localdb.objects;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


public final class LocalRepoState {

    public static LocalRepoState normal() {
        return new LocalRepoState(State.NORMAL, null);
    }

    public static LocalRepoState forRebaseInProgress(String rebaseCommitBeingIntegrated) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(rebaseCommitBeingIntegrated));
        return new LocalRepoState(State.REBASE_IN_PROGRESS, rebaseCommitBeingIntegrated);
    }


    public enum State {
        NORMAL,
        REBASE_IN_PROGRESS
    }


    private final State state;
    @Nullable private final String rebaseCommitBeingIntegrated;

    private LocalRepoState(State state, @Nullable String rebaseCommitBeingIntegrated) {
        this.state = state;
        this.rebaseCommitBeingIntegrated = rebaseCommitBeingIntegrated;
    }

    public State getState() {
        return state;
    }

    public String getRebaseCommitBeingIntegrated() {
        Preconditions.checkState(state == State.REBASE_IN_PROGRESS && rebaseCommitBeingIntegrated != null);
        return rebaseCommitBeingIntegrated;
    }

}
