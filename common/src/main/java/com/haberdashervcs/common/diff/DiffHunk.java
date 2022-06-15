package com.haberdashervcs.common.diff;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.diff.git.RabTextSequence;


public final class DiffHunk {

    // TODO: Make everything not public.
    public int originalStart;
    public int originalEnd;
    public int modifiedStart;
    public int modifiedEnd;

    public @Nullable List<DiffHunkList.ThreadLineNumberEntry> forThreads = null;

    public DiffHunk(int originalStart, int originalEnd, int modifiedStart, int modifiedEnd) {
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
        this.modifiedStart = modifiedStart;
        this.modifiedEnd = modifiedEnd;
    }


    void insertAt(int lineNum, int numLines) {
        // The right bound is inclusive because it's okay to append to a hunk.
        Preconditions.checkArgument(modifiedStart <= lineNum && lineNum <= modifiedEnd);
        modifiedEnd += numLines;
    }

    int deleteFrom(int lineNum, int numLines) {
        Preconditions.checkArgument(modifiedStart <= lineNum && lineNum < modifiedEnd);
        int toDelete = Math.min(numLines, modifiedEnd - lineNum);
        modifiedEnd -= toDelete;
        return toDelete;
    }

    void shiftModifiedRange(int offset) {
        this.modifiedStart += offset;
        this.modifiedEnd += offset;
    }


    public List<LineDiff> asUdiff(RabTextSequence original, RabTextSequence modified) {
        List<LineDiff> out = new ArrayList<>();

        for (int i = originalStart; i < originalEnd; ++i) {
            out.add(new LineDiff(LineDiff.Type.MINUS, i, -1, original.getLine(i)));
        }

        for (int j = modifiedStart; j < modifiedEnd; ++j) {
            out.add(new LineDiff(LineDiff.Type.PLUS, -1, j, modified.getLine(j)));
        }

        return out;
    }

}
