package com.haberdashervcs.common.diff.git;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.SequenceComparator;


public final class HistogramDiffer {

    private static final HdLogger LOG = HdLoggers.create(HistogramDiffer.class);


    public HistogramDiffer() {
    }


    public List<DiffHunk> computeLineDiffs(RandomAccessBytes original, RandomAccessBytes modified) {
        RabTextSequence originalSeq = new RabTextSequence(original);
        RabTextSequence modifiedSeq = new RabTextSequence(modified);
        SequenceComparator<RabTextSequence> comp = new RabTextComparator();
        HistogramDiff differ = new HistogramDiff();
        // TODO: Limit the number of edits to some maximum, since it's only for display.
        EditList edits = differ.diff(comp, originalSeq, modifiedSeq);
        if (edits.size() == 0) {
            return ImmutableList.of();
        }

        ArrayList<DiffHunk> out = new ArrayList<>();
        // Git diffs are 0-indexed, while DiffHunk is 1-indexed, corresponding to RabTextSequence line numbers.
        for (Edit edit : edits) {
            DiffHunk hunk = new DiffHunk(
                    edit.getBeginA() + 1,
                    edit.getEndA() + 1,
                    edit.getBeginB() + 1,
                    edit.getEndB() + 1);
            out.add(hunk);
        }
        return out;
    }

}
