package com.haberdashervcs.client.rebase;

import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;


final class RebasePathComparison {

    enum Change {
        ADDED,
        DELETED,
        MODIFIED,
        /**
         * This means the file was either unmodified or absent at both base and head.
         */
        NO_CHANGE
    }


    private final String path;
    private final Change mainChange;
    private final Change branchChange;
    private final @Nullable String mainBaseFileId;
    private final @Nullable String mainNewFileId;
    private final @Nullable String branchFileId;

    RebasePathComparison(
            String path,
            Change mainChange,
            Change branchChange,
            @Nullable String mainBaseFileId,
            @Nullable String mainNewFileId,
            @Nullable String branchFileId) {
        this.path = path;
        this.mainChange = mainChange;
        this.branchChange = branchChange;
        this.mainBaseFileId = mainBaseFileId;
        this.mainNewFileId = mainNewFileId;
        this.branchFileId = branchFileId;
    }


    public String getPath() {
        return path;
    }

    public Change getMainChange() {
        return mainChange;
    }

    public Change getBranchChange() {
        return branchChange;
    }

    public Optional<String> getMainNewFileId() {
        return Optional.ofNullable(mainNewFileId);
    }

    public Optional<String> getBranchFileId() {
        return Optional.ofNullable(branchFileId);
    }

    public Optional<String> getMainBaseFileId() {
        return Optional.ofNullable(mainBaseFileId);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("mainChange", mainChange)
                .add("branchChange", branchChange)
                .add("mainBaseFileId", mainBaseFileId)
                .add("mainNewFileId", mainNewFileId)
                .add("branchFileId", branchFileId)
                .toString();
    }
}
