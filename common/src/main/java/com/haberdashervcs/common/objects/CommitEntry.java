package com.haberdashervcs.common.objects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.protobuf.CommitsProto;


public final class CommitEntry {


    public static final class CommitChangedPath {

        public enum ChangeType {
            ADD,
            DELETE,
            DIFF
        }

        public static CommitChangedPath diff(String path, String previousId, String thisId) {
            Preconditions.checkNotNull(previousId);
            Preconditions.checkNotNull(thisId);
            return new CommitChangedPath(path, ChangeType.DIFF, previousId, thisId);
        }

        public static CommitChangedPath added(String path, String newFileId) {
            Preconditions.checkNotNull(newFileId);
            return new CommitChangedPath(path, ChangeType.ADD, null, newFileId);
        }

        public static CommitChangedPath deleted(String path, String deletedFileId) {
            Preconditions.checkNotNull(deletedFileId);
            return new CommitChangedPath(path, ChangeType.DELETE, deletedFileId, null);
        }


        private final String path;
        private final ChangeType changeType;
        private final @Nullable String previousId;
        private final @Nullable String thisId;

        private CommitChangedPath(String path, ChangeType changeType, String previousId, String thisId) {
            this.path = path;
            this.changeType = changeType;
            this.previousId = previousId;
            this.thisId = thisId;
        }

        public String getPath() {
            return path;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public Optional<String> getPreviousId() {
            return Optional.ofNullable(previousId);
        }

        public Optional<String> getThisId() {
            return Optional.ofNullable(thisId);
        }
    }


    public static CommitEntry of(
            String branchName,
            long commitId,
            String author,
            String message,
            Collection<CommitChangedPath> changedPaths) {
        return new CommitEntry(branchName, commitId, author, message, changedPaths, null);
    }


    private final String branchName;
    private final long commitId;
    private final String author;
    private final String message;
    // TODO: What if the list of changed paths is really long? Will that cause storage problems?
    private final List<CommitChangedPath> changedPaths;
    private final @Nullable CommitsProto.BranchIntegration integration;

    private CommitEntry(
            String branchName,
            long commitId,
            String author,
            String message,
            Collection<CommitChangedPath> changedPaths,
            @Nullable CommitsProto.BranchIntegration integration) {
        this.branchName = branchName;
        this.commitId = commitId;
        this.author = author;
        this.message = message;
        this.changedPaths = ImmutableList.copyOf(changedPaths);
        this.integration = integration;
    }

    public String getBranchName() {
        return branchName;
    }

    public long getCommitId() {
        return commitId;
    }

    public String getAuthorUserId() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    public List<CommitChangedPath> getChangedPaths() {
        return changedPaths;
    }

    public Optional<CommitsProto.BranchIntegration> getIntegration() {
        return Optional.ofNullable(integration);
    }


    public CommitEntry withAuthor(String author) {
        return new CommitEntry(
                branchName, commitId, author, message, changedPaths, integration);
    }


    // This is a hack/convenience to avoid adding a real builder interface.
    public CommitEntry withIntegration(CommitsProto.BranchIntegration integration) {
        Preconditions.checkState(this.integration == null);
        Preconditions.checkNotNull(integration);
        return new CommitEntry(
                branchName, commitId, author, message, changedPaths, integration);
    }


    public String getDebugString() {
        return MoreObjects.toStringHelper(this)
                .add("branchName", branchName)
                .add("commitId", commitId)
                .add("author", author)
                .add("message", message)
                .add("changedPaths", changedPaths)
                .add("integration", integration)
                .toString();
    }

}
