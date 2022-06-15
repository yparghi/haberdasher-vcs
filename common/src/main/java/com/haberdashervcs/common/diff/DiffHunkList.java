package com.haberdashervcs.common.diff;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class DiffHunkList {

    private static final HdLogger LOG = HdLoggers.create(DiffHunkList.class);


    public final static class ThreadLineNumberEntry {

        // TODO: Make everything not public.

        public final String threadId;
        public final long commitId;
        public int lineNum;
        // Used for keeping track during computation.
        private boolean offsetApplied;

        public ThreadLineNumberEntry(String threadId, long commitId, int lineNum) {
            this.threadId = threadId;
            this.commitId = commitId;
            this.lineNum = lineNum;
            this.offsetApplied = false;
        }
    }


    private final long mCommitId;
    // TODO! linked list, or whatever would make this faster
    private final ArrayList<DiffHunk> hunks;
    private final List<ThreadLineNumberEntry> threads;

    private int idx;
    private int offset;
    private boolean finished;

    // TODO! What about thread line #'s at the base commit, which never move?
    public DiffHunkList(long mCommitId, ArrayList<DiffHunk> hunks, List<ThreadLineNumberEntry> threads) {
        this.mCommitId = mCommitId;
        this.hunks = hunks;
        this.threads = threads;
        this.idx = 0;
        this.offset = 0;
        this.finished = false;

        for (ThreadLineNumberEntry thread : threads) {
            thread.offsetApplied = false;
        }
    }


    // TODO: Should I change this to take a DiffHunk, with its already calculated before/after o- and m-lines?
    public void change(final int mLineNum, final int linesToDelete, final int linesToInsert) {
        Preconditions.checkArgument(linesToDelete >= 0 && linesToInsert >= 0);
        Preconditions.checkArgument(linesToDelete > 0 || linesToInsert > 0);

        if (linesToDelete > 0) {
            deleteLines(mLineNum, linesToDelete);
            if (linesToInsert > 0) {
                DiffHunk toInsert = hunks.get(idx);
                toInsert.insertAt(mLineNum, linesToInsert);
            }

        } else {
            insertAt(mLineNum, linesToInsert);
        }

        this.offset += (linesToInsert - linesToDelete);
    }


    private void insertAt(final int mLineNum, final int numLines) {

        if (hunks.size() == 0) {
            DiffHunk wholeInsertion = new DiffHunk(
                    mLineNum,
                    mLineNum,
                    mLineNum,
                    mLineNum + numLines);
            hunks.add(idx, wholeInsertion);
            return;
        }

        for (; idx < hunks.size(); incrementIdx()) {
            DiffHunk thisHunk = hunks.get(idx);
            if (thisHunk.modifiedStart <= mLineNum && mLineNum < thisHunk.modifiedEnd) {
                handleThreadsForInsert(mLineNum, numLines, thisHunk.modifiedEnd);
                thisHunk.insertAt(mLineNum, numLines);
                return;

            } else if (thisHunk.modifiedStart > mLineNum && idx == 0) {
                DiffHunk newInsert = new DiffHunk(
                        mLineNum,
                        mLineNum,
                        mLineNum,
                        mLineNum + numLines);
                hunks.add(0, newInsert);
                return;

            } else if (thisHunk.modifiedStart > mLineNum && idx > 0) {
                int linesBack = thisHunk.modifiedStart - mLineNum;
                DiffHunk newInsert = new DiffHunk(
                        thisHunk.originalStart - linesBack,
                        thisHunk.originalStart - linesBack,
                        thisHunk.modifiedStart - linesBack,
                        thisHunk.modifiedStart - linesBack + numLines);
                hunks.add(idx, newInsert);
                return;
            }
        }


        // Insert at the end.
        DiffHunk prev = hunks.get(idx - 1);
        int linesAhead = (mLineNum - prev.modifiedEnd) + offset;
        DiffHunk newInsert = new DiffHunk(
                prev.originalEnd + linesAhead,
                prev.originalEnd + linesAhead,
                mLineNum + offset,
                mLineNum + offset + numLines);
        hunks.add(idx, newInsert);
    }


    private void handleThreadsForInsert(int mLineNum, int numLines, int hunkEndLine) {
        for (ThreadLineNumberEntry thread : threads) {
            if (thread.commitId >= this.mCommitId) {
                continue;
            }

            if (thread.lineNum >= mLineNum && thread.lineNum < hunkEndLine) {
                thread.lineNum += numLines;
                thread.offsetApplied = true;
            }
        }
    }


    private void deleteLines(final int mLineNum, final int numLines) {

        handleThreadsForDelete(mLineNum, numLines);

        int linesDeletedSoFar = 0;

        if (hunks.size() == 0) {
            DiffHunk wholeDeletion = new DiffHunk(
                    mLineNum,
                    mLineNum + numLines,
                    mLineNum,
                    mLineNum);
            hunks.add(idx, wholeDeletion);
            return;
        }

        // Find the first hunk.
        for (; idx < hunks.size(); incrementIdx()) {
            DiffHunk thisHunk = hunks.get(idx);

            if (thisHunk.modifiedStart <= mLineNum && mLineNum < thisHunk.modifiedEnd) {
                int linesDeleted = thisHunk.deleteFrom(mLineNum, numLines);
                linesDeletedSoFar += linesDeleted;
                break;

            } else if (thisHunk.modifiedStart > mLineNum) {
                int linesBack = thisHunk.modifiedStart - mLineNum;
                if (linesBack > numLines) {
                    DiffHunk wholeDeletion = new DiffHunk(
                            thisHunk.originalStart - linesBack,
                            thisHunk.originalStart - linesBack + numLines,
                            mLineNum,
                            mLineNum);
                    hunks.add(idx, wholeDeletion);
                    return;

                } else {
                    DiffHunk newStartingHunk = new DiffHunk(
                            thisHunk.originalStart - linesBack,
                            thisHunk.originalStart,
                            mLineNum,
                            mLineNum);
                    hunks.add(idx, newStartingHunk);
                    linesDeletedSoFar += linesBack;
                    break;
                }
            }
        }


        if (idx == hunks.size()) {
            DiffHunk prev = hunks.get(idx - 1);
            int linesToDelete = (numLines - linesDeletedSoFar);
            // TEMP BUG!: Doesn't incorporate offset this round...
            // Do diffhunks need a tempvar like int offsetThisRound, that gets reset to 0 in finish() ?......
            //int linesAhead = (mLineNum - prev.modifiedEnd);
            int linesAhead = (mLineNum - prev.modifiedEnd) + offset;
            int baseStart = prev.originalEnd + linesAhead;

            DiffHunk wholeDeletion = new DiffHunk(
                    baseStart,
                    baseStart + linesToDelete,
                    mLineNum,
                    mLineNum);
            hunks.add(idx, wholeDeletion);
            return;
        }


        // hunks[idx] is now the first, already handled hunk.

        DiffHunk prev = hunks.get(idx);
        int currentBaseLine = prev.originalEnd;
        while (linesDeletedSoFar < numLines) {
            incrementIdx();

            if (idx == hunks.size()) {
                DiffHunk remainingDeletion = new DiffHunk(
                        currentBaseLine,
                        currentBaseLine + (numLines - linesDeletedSoFar),
                        mLineNum,
                        mLineNum);
                hunks.add(idx, remainingDeletion);
                return;
            }


            DiffHunk thisHunk = hunks.get(idx);
            if (thisHunk.originalStart > currentBaseLine) {
                int linesBack = thisHunk.originalStart - currentBaseLine;
                if (linesBack > (numLines - linesDeletedSoFar)) {
                    DiffHunk remainingDeletion = new DiffHunk(
                            currentBaseLine,
                            currentBaseLine + numLines - linesDeletedSoFar,
                            mLineNum,
                            mLineNum);
                    hunks.add(idx, remainingDeletion);
                    return;

                } else {
                    DiffHunk deletionUpTo = new DiffHunk(
                            currentBaseLine,
                            currentBaseLine + linesBack,
                            mLineNum,
                            mLineNum);
                    hunks.add(idx, deletionUpTo);
                    currentBaseLine += linesBack;
                    linesDeletedSoFar += linesBack;
                }


            } else {
                thisHunk.modifiedStart -= linesDeletedSoFar;
                thisHunk.modifiedEnd -= linesDeletedSoFar;
                int linesDeletedFromThisHunk = thisHunk.deleteFrom(mLineNum, numLines - linesDeletedSoFar);
                currentBaseLine += linesDeletedFromThisHunk;
                linesDeletedSoFar += linesDeletedFromThisHunk;
            }
        }
    }


    private void handleThreadsForDelete(int mLineNum, int numLines) {
        for (ThreadLineNumberEntry thread : threads) {
            if (thread.commitId >= this.mCommitId) {
                continue;
            }

            if (thread.lineNum >= mLineNum && thread.lineNum < mLineNum + numLines) {
                // Move the thread to the start of the modified line range.
                thread.lineNum = mLineNum;
                thread.offsetApplied = true;
            }
        }
    }


    // The invariant for all this is: after a change op, the cursor points to the last modified hunk (not after it).
    private void incrementIdx() {
        ++idx;
        if (idx < hunks.size()) {
            DiffHunk thisHunk = hunks.get(idx);
            thisHunk.shiftModifiedRange(offset);
            applyThreadOffsets(thisHunk.modifiedStart);
        }
    }


    private void applyThreadOffsets(int nextHunkStart) {
        for (ThreadLineNumberEntry thread : threads) {
            if (thread.commitId >= this.mCommitId) {
                continue;
            }

            if (!thread.offsetApplied && thread.lineNum < nextHunkStart) {
                thread.lineNum += offset;
                thread.offsetApplied = true;
            }
        }
    }


    // Applies remaining offsets, and merges continuous hunks. (in that order? i.e. merging starts a new pass?)
    public void finish() {

        // Apply offsets to remaining hunks.
        while (idx < hunks.size()) {
            incrementIdx();
        }

        // Apply offsets to remaining threads.
        for (ThreadLineNumberEntry thread : threads) {
            if (thread.commitId >= this.mCommitId) {
                continue;
            }

            if (!thread.offsetApplied) {
                thread.lineNum += offset;
                thread.offsetApplied = true;
            }
        }


        // Merge contiguous hunks.
        //
        // TODO! We really need a linked list for this.
        DiffHunk beingMerged = hunks.get(0);
        idx = 1;
        while (idx < hunks.size()) {
            DiffHunk thisHunk = hunks.get(idx);

            if (thisHunk.originalStart == beingMerged.originalEnd) {
                beingMerged.originalEnd = thisHunk.originalEnd;
                beingMerged.modifiedEnd = thisHunk.modifiedEnd;
                hunks.remove(idx);

            } else {
                beingMerged = thisHunk;
                ++idx;
            }
        }

        finished = true;
    }


    List<DiffHunk> getHunks() {
        Preconditions.checkState(finished);
        return hunks;
    }


    List<ThreadLineNumberEntry> getThreads() {
        Preconditions.checkState(finished);
        return threads;
    }

    // Notes on offsets, 3/20:
    // - You delete + insert at M...
    // - All the hunks after the affected range are unchanged, even though they're all +4 now (let's say)...
    // - Keep a cursor...
    // - Delete ops only go forward -- is this doable from current code?: yes, 'i' only goes forward there
    // - Then insert at the end. doable?:
    //     - I think so if we delete & insert together in one op, and create an empty delete hunk for 0 lines deleted...
    //         ? Or, just use i - 1 ?
    //     - then you insert into the last hunk
    // - Putting it all together, you adjust the offset from the whole - & + operation, and carry it forward
    //     * the CONSTANT is mLineNum! (I think?)


    // Notes 3/19:
    // - SIMPLIFY! One change/hunk happens at a cursor position N...
    // - So you find the hunk at N  **OR CREATE IT**...
    //     - findHunkAt(n) will create an empty hunk there, if there's none already?
    // - and apply inserts/deletes on that hunk repeatedly?
    //
    // - you can never "run out" of insertions in a hunk...
    // - but you CAN run out of deletions, and have to start a new hunk, or move to the next one -- right?
    //     - I think you create a pure deletion hunk that "spans" from this hunk to the next, if there's any gap
    //       in between.
    //
    // - and to avoid applying offsets till a final ending pass, you only move the cursor forward?
    //     - so INDEXING is about rigorously maintaining the modIdx you're using as you go forward, then applying
    //       offsets in a final pass?

}
