package com.haberdashervcs.server.frontend.review;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.DiffHunkList;


/**
 * Inserts changeless DiffHunk's (of Type.SAME) into a List<DiffHunk> for review threads that aren't within a hunk.
 */
final class ThreadHunkMerger {

    private final List<DiffHunk> hunks;
    private final List<DiffHunkList.ThreadLineNumberEntry> threads;
    private int hunkIdx = 0;
    private int threadIdx = 0;

    ThreadHunkMerger(List<DiffHunk> hunks, List<DiffHunkList.ThreadLineNumberEntry> threads) {
        this.hunks = hunks;
        this.threads = threads;
    }


    List<DiffHunk> merge() {
        List<DiffHunk> out = new ArrayList<>();

        while (hunkIdx < hunks.size() || threadIdx < threads.size()) {

            if (hunkIdx == hunks.size()) {
                addHunkForThreadsHere(out);

            } else if (threadIdx == threads.size()) {
                out.add(hunks.get(hunkIdx));
                ++hunkIdx;

            } else {
                DiffHunk nextHunk = hunks.get(hunkIdx);
                DiffHunkList.ThreadLineNumberEntry nextThread = threads.get(threadIdx);

                if (nextThread.lineNum < nextHunk.modifiedStart) {
                    addHunkForThreadsHere(out);

                } else {
                    DiffHunk thisHunk = hunks.get(hunkIdx);
                    addThreadsWithinHunk(thisHunk);
                    out.add(thisHunk);
                    ++hunkIdx;
                }
            }
        }

        return out;
    }


    private void addThreadsWithinHunk(DiffHunk thisHunk) {
        Preconditions.checkArgument(thisHunk.forThreads == null);

        while (threadIdx < threads.size()) {
            DiffHunkList.ThreadLineNumberEntry thisThread = threads.get(threadIdx);
            if (thisThread.lineNum >= thisHunk.modifiedStart && thisThread.lineNum < thisHunk.modifiedEnd) {
                if (thisHunk.forThreads == null) {
                    thisHunk.forThreads = new ArrayList<>();
                }
                thisHunk.forThreads.add(thisThread);
                ++threadIdx;
            } else {
                break;
            }
        }
    }


    private void addHunkForThreadsHere(List<DiffHunk> out) {
        DiffHunkList.ThreadLineNumberEntry nextThread = threads.get(threadIdx);
        int lineNum = nextThread.lineNum;
        DiffHunk thisThreadHunk = new DiffHunk(lineNum, lineNum, lineNum, lineNum);
        thisThreadHunk.forThreads = new ArrayList<>();
        // There may be multiple threads at this line.
        while (true) {
            thisThreadHunk.forThreads.add(nextThread);
            ++threadIdx;
            if (threadIdx == threads.size()) {
                break;
            } else if ((nextThread = threads.get(threadIdx)).lineNum != lineNum) {
                break;
            }
        }
        out.add(thisThreadHunk);
    }

}
