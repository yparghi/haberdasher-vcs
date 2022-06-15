package com.haberdashervcs.common.diff.git;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import org.eclipse.jgit.merge.MergeAlgorithm;
import org.eclipse.jgit.merge.MergeResult;


public final class GitTextMerger {

    private static final HdLogger LOG = HdLoggers.create(GitTextMerger.class);


    public static GitTextMerger of(
            List<String> sequenceNames,
            RandomAccessBytes mainBaseContents,
            RandomAccessBytes mainNewContents,
            RandomAccessBytes branchContents) {
        return new GitTextMerger(sequenceNames, mainBaseContents, mainNewContents, branchContents);
    }


    private final List<String> sequenceNames;
    private final RandomAccessBytes mainBaseContents;
    private final RandomAccessBytes mainNewContents;
    private final RandomAccessBytes branchContents;

    private GitTextMerger(
            List<String> sequenceNames,
            RandomAccessBytes mainBaseContents,
            RandomAccessBytes mainNewContents,
            RandomAccessBytes branchContents) {
        Preconditions.checkArgument(sequenceNames.size() == 3);
        this.sequenceNames = sequenceNames;
        this.mainBaseContents = mainBaseContents;
        this.mainNewContents = mainNewContents;
        this.branchContents = branchContents;
    }


    // TODO: For testing, break out into one method that returns the merge chunks, and another method (in another
    // class?) that writes them to a file?
    public void writeToLocalPath(Path localPath) throws IOException {
        RabTextSequence mainBaseText = new RabTextSequence(mainBaseContents);
        RabTextSequence mainNewText = new RabTextSequence(mainNewContents);
        RabTextSequence branchText = new RabTextSequence(branchContents);
        RabTextComparator comp = new RabTextComparator();

        MergeAlgorithm merger = new MergeAlgorithm();
        MergeResult<RabTextSequence> result = merger.merge(comp, mainBaseText, mainNewText, branchText);

        OutputStream out = Files.newOutputStream(localPath);
        // 0 == base, 1 == branch, 2 == mainNew
        RabMergeFormatterPass formatter = new RabMergeFormatterPass(out, result, sequenceNames, StandardCharsets.UTF_8);
        formatter.formatMerge();
    }

}
