package com.haberdashervcs.server.frontend.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.DiffHunkList;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.protobuf.ReviewsProto;


final class FileReviewDisplay {

    private static final HdLogger LOG = HdLoggers.create(FileReviewDisplay.class);


    static final class DiffWithCommitId {
        private final long commitId;
        private final ReviewsProto.FileDiffEntry diff;

        DiffWithCommitId(long commitId, ReviewsProto.FileDiffEntry diff) {
            this.commitId = commitId;
            this.diff = diff;
        }

        long getCommitId() {
            return commitId;
        }

        ReviewsProto.FileDiffEntry getDiff() {
            return diff;
        }
    }


    static FileReviewDisplay of(
            String path,
            long baseCommitId,
            long headCommitId,
            List<DiffWithCommitId> diffs,
            List<ReviewsProto.ReviewThread> threads) {
        return new FileReviewDisplay(path, baseCommitId, headCommitId, diffs, threads);
    }


    private final String path;
    private final long baseCommitId;
    private final long headCommitId;
    private final List<DiffWithCommitId> diffs;
    private final List<DiffHunkList.ThreadLineNumberEntry> threads;

    private FileReviewDisplay(
            String path,
            long baseCommitId,
            long headCommitId,
            List<DiffWithCommitId> diffs,
            List<ReviewsProto.ReviewThread> threads) {
        Preconditions.checkArgument(diffs.size() > 0);

        this.path = path;
        this.baseCommitId = baseCommitId;
        this.headCommitId = headCommitId;
        this.diffs = ImmutableList.sortedCopyOf(Comparator.comparingLong(DiffWithCommitId::getCommitId), diffs);
        this.threads = threads.stream()
                .map(thread -> new DiffHunkList.ThreadLineNumberEntry(
                        thread.getId(), thread.getCommitId(), thread.getLineNumber()))
                .collect(Collectors.toUnmodifiableList());
    }


    String getPath() {
        return path;
    }


    static final class FileDiffResult {

        static FileDiffResult deleted(String beforeFileId) {
            Preconditions.checkNotNull(beforeFileId);
            return new FileDiffResult(Type.DELETED, null, null, beforeFileId, null);
        }

        static FileDiffResult ofDiffs(
                List<DiffHunk> hunks,
                List<DiffHunkList.ThreadLineNumberEntry> threads,
                String beforeFileId,
                String afterFileId) {
            Preconditions.checkNotNull(hunks);
            Preconditions.checkNotNull(beforeFileId);
            Preconditions.checkNotNull(afterFileId);
            return new FileDiffResult(Type.DIFF, hunks, threads, beforeFileId, afterFileId);
        }

        static FileDiffResult added(
                List<DiffHunkList.ThreadLineNumberEntry> threads, String afterFileId) {
            Preconditions.checkNotNull(threads);
            Preconditions.checkNotNull(afterFileId);
            return new FileDiffResult(Type.ADDED, null, threads, null, afterFileId);
        }

        static FileDiffResult addedThenDeleted() {
            return new FileDiffResult(Type.ADDED_THEN_DELETED, null, null, null, null);
        }

        static FileDiffResult binary(
                FileDiffResult.Type type,
                long bytesBefore,
                long bytesAfter,
                String beforeFileId,
                String afterFileId) {
            return new FileDiffResult(type, bytesBefore, bytesAfter, beforeFileId, afterFileId);
        }

        static FileDiffResult noChange() {
            return new FileDiffResult(Type.NO_CHANGE, null, null, null, null);
        }


        // The "cumulative" result of changes to this file between base and head commits.
        enum Type {
            ADDED,
            DELETED,
            ADDED_THEN_DELETED,
            DIFF,
            NO_CHANGE
        }


        private final Type type;
        private final @Nullable List<DiffHunk> hunks;
        private final @Nullable List<DiffHunkList.ThreadLineNumberEntry> threads;

        private final boolean isBinaryDiff;
        private final long binaryBytesBefore;
        private final long binaryBytesAfter;

        private final @Nullable String beforeFileId;
        private final @Nullable String afterFileId;

        private FileDiffResult(
                Type type,
                @Nullable List<DiffHunk> hunks,
                @Nullable List<DiffHunkList.ThreadLineNumberEntry> threads,
                @Nullable String beforeFileId,
                @Nullable String afterFileId) {
            this.type = type;
            this.hunks = hunks;
            this.threads = threads;

            this.isBinaryDiff = false;
            this.binaryBytesBefore = -1;
            this.binaryBytesAfter = -1;

            this.beforeFileId = beforeFileId;
            this.afterFileId = afterFileId;
        }

        private FileDiffResult(
                Type type,
                long binaryBytesBefore,
                long binaryBytesAfter,
                @Nullable String beforeFileId,
                @Nullable String afterFileId) {
            this.type = type;
            this.hunks = null;
            this.threads = null;

            this.isBinaryDiff = true;
            this.binaryBytesBefore = binaryBytesBefore;
            this.binaryBytesAfter = binaryBytesAfter;

            this.beforeFileId = beforeFileId;
            this.afterFileId = afterFileId;
        }

        Type getType() {
            return type;
        }

        List<DiffHunk> getHunks() {
            Preconditions.checkState(hunks != null);
            return hunks;
        }

        List<DiffHunkList.ThreadLineNumberEntry> getThreads() {
            Preconditions.checkState(threads != null);
            return threads;
        }

        boolean isBinaryDiff() {
            return isBinaryDiff;
        }

        long getBinaryBytesBefore() {
            Preconditions.checkState(isBinaryDiff);
            return binaryBytesBefore;
        }

        long getBinaryBytesAfter() {
            Preconditions.checkState(isBinaryDiff);
            return binaryBytesAfter;
        }

        @Nullable String getBeforeFileId() {
            return beforeFileId;
        }

        @Nullable String getAfterFileId() {
            return afterFileId;
        }
    }


    FileDiffResult computeDiff() {

        FileStateResult state = computeFileState(baseCommitId);
        FileDiffResult.Type headState = state.getHeadState();

        if (headState == FileDiffResult.Type.NO_CHANGE) {
            return FileDiffResult.noChange();

        } else if (state.binaryBytesBefore != -1 || state.binaryBytesAfter != -1) {
            return FileDiffResult.binary(
                    headState,
                    state.binaryBytesBefore,
                    state.binaryBytesAfter,
                    state.beforeFileId,
                    state.afterFileId);
        }


        if (headState == FileDiffResult.Type.ADDED && state.lastAddCommit == headCommitId) {
            List<DiffHunkList.ThreadLineNumberEntry> threadsAtHeadCommit = threads.stream()
                    .filter(thread -> (thread.commitId == headCommitId))
                    .collect(Collectors.toUnmodifiableList());
            return FileDiffResult.added(threadsAtHeadCommit, state.afterFileId);

        } else if (headState == FileDiffResult.Type.ADDED_THEN_DELETED) {
            return FileDiffResult.addedThenDeleted();

        } else if (headState == FileDiffResult.Type.DELETED) {
            return FileDiffResult.deleted(state.beforeFileId);
        }


        // Cut out threads since the last add commit.
        List<DiffHunkList.ThreadLineNumberEntry> threadsMinusOrphans = threads;
        if (state.lastAddCommit > 0) {
            threadsMinusOrphans = threads.stream()
                    .filter(thread -> (thread.commitId >= state.lastAddCommit))
                    .collect(Collectors.toUnmodifiableList());
        }


        // Compute the thread offsets up to the base commit.
        if (baseCommitId > 0 && state.lastAddCommit < baseCommitId) {
            long startCommit = Math.max(0, state.lastAddCommit);
            List<DiffHunk> upToBase_unused = computeForRange(startCommit, baseCommitId, threadsMinusOrphans);
        }


        if (headState == FileDiffResult.Type.ADDED) {
            // Compute thread offsets since the add.
            List<DiffHunk> sinceAdd_unused = computeForRange(state.lastAddCommit, headCommitId, threadsMinusOrphans);
            return FileDiffResult.added(threadsMinusOrphans, state.afterFileId);

        } else {
            List<DiffHunk> baseToHead = computeForRange(baseCommitId, headCommitId, threadsMinusOrphans);
            return FileDiffResult.ofDiffs(baseToHead, threads, state.beforeFileId, state.afterFileId);
        }
    }


    private List<DiffHunk> computeForRange(
            long fromCommitId, long toCommitId, List<DiffHunkList.ThreadLineNumberEntry> threads) {
        ArrayList<DiffHunk> hunksSoFar = new ArrayList<>();

        for (DiffWithCommitId diff : diffs) {
            if (diff.getCommitId() <= fromCommitId) {
                continue;
            } else if (diff.getCommitId() > toCommitId) {
                break;
            }
            Verify.verify(diff.getDiff().getType() == ReviewsProto.FileDiffEntry.Type.DIFF);
            Verify.verify(diff.getDiff().getDiffHunks().hasBinaryDiff() == false);

            DiffHunkList hunkListThisCommit = new DiffHunkList(diff.getCommitId(), hunksSoFar, threads);
            List<ReviewsProto.TextDiffHunk> hunksFromCommit = diff.getDiff().getDiffHunks().getTextDiffHunksList();
            for (ReviewsProto.TextDiffHunk hunk : hunksFromCommit) {
                int mLineNum = hunk.getModifiedFromLine();
                int linesToDelete = (hunk.getOriginalToLine() - hunk.getOriginalFromLine());
                int linesToInsert = (hunk.getModifiedToLine() - hunk.getModifiedFromLine());
                //LOG.info("TEMP: change(%d, %d, %d)", mLineNum, linesToDelete, linesToInsert);
                hunkListThisCommit.change(mLineNum, linesToDelete, linesToInsert);
            }
            hunkListThisCommit.finish();
        }

        return hunksSoFar;
    }


    private static class FileStateResult {
        long baseCommitId = -1;
        boolean diffFoundInRange = false;
        long lastAddCommit = -1;
        long lastDeleteCommit = -1;
        ReviewsProto.FileDiffEntry.Type firstAddDeleteOpAfterBase = null;
        String beforeFileId = null;
        String afterFileId = null;

        long binaryBytesBefore = -1;
        long binaryBytesAfter = -1;

        FileDiffResult.Type getHeadState() {
            Preconditions.checkState(baseCommitId >= 0);

            if (!diffFoundInRange) {
                return FileDiffResult.Type.NO_CHANGE;

            } else if (lastDeleteCommit > lastAddCommit) {
                return (firstAddDeleteOpAfterBase == ReviewsProto.FileDiffEntry.Type.DELETE)
                        ? FileDiffResult.Type.DELETED
                        : FileDiffResult.Type.ADDED_THEN_DELETED;

            } else if (lastAddCommit > baseCommitId) {
                return FileDiffResult.Type.ADDED;

            } else {
                return FileDiffResult.Type.DIFF;
            }
        }
    }

    private FileStateResult computeFileState(long baseCommitId) {
        FileStateResult out = new FileStateResult();
        out.baseCommitId = baseCommitId;

        for (DiffWithCommitId diff : diffs) {
            if (diff.getCommitId() > headCommitId) {
                break;
            } else if (diff.getCommitId() > baseCommitId) {
                out.diffFoundInRange = true;
            }

            ReviewsProto.FileDiffEntry.Type diffType = diff.getDiff().getType();
            if (diffType == ReviewsProto.FileDiffEntry.Type.ADD) {
                out.lastAddCommit = diff.getCommitId();
                out.beforeFileId = null;
                out.afterFileId = diff.getDiff().getThisId();
            } else if (diffType == ReviewsProto.FileDiffEntry.Type.DELETE) {
                out.lastDeleteCommit = diff.getCommitId();
                if (out.beforeFileId == null && diff.getCommitId() > baseCommitId) {
                    out.beforeFileId = diff.getDiff().getPreviousId();
                }
                out.afterFileId = null;
            }

            if (diff.getCommitId() > baseCommitId
                    && out.firstAddDeleteOpAfterBase == null
                    && (diffType == ReviewsProto.FileDiffEntry.Type.ADD
                            || diffType == ReviewsProto.FileDiffEntry.Type.DELETE)) {
                out.firstAddDeleteOpAfterBase = diff.getDiff().getType();
            }

            if (diff.getDiff().getType() != ReviewsProto.FileDiffEntry.Type.DIFF) {
                continue;
            }

            if (out.beforeFileId == null && diff.getCommitId() > baseCommitId) {
                out.beforeFileId = diff.getDiff().getPreviousId();
            }
            out.afterFileId = diff.getDiff().getThisId();

            // Binary <-> text
            if (diff.getDiff().getDiffHunks().hasBinaryDiff()) {
                ReviewsProto.BinaryDiff bDiff = diff.getDiff().getDiffHunks().getBinaryDiff();
                if (bDiff.getNumBytesOriginal() == -1) {
                    out.lastAddCommit = diff.getCommitId();
                    out.beforeFileId = null;
                    out.binaryBytesBefore = bDiff.getNumBytesModified();
                    out.binaryBytesAfter = bDiff.getNumBytesModified();
                } else if (bDiff.getNumBytesModified() == -1) {
                    out.lastAddCommit = diff.getCommitId();
                    out.beforeFileId = null;
                    out.binaryBytesBefore = -1;
                    out.binaryBytesAfter = -1;
                } else {
                    out.binaryBytesAfter = bDiff.getNumBytesModified();
                    if (out.binaryBytesBefore == -1 && diff.getCommitId() > baseCommitId) {
                        out.binaryBytesBefore = bDiff.getNumBytesOriginal();
                    }
                }

            } else {
                out.binaryBytesBefore = -1;
                out.binaryBytesAfter = -1;
            }
        }

        return out;
    }

}
