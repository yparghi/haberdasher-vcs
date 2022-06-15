package com.haberdashervcs.common.diff.git;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import junit.framework.TestCase;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.util.IntList;
import org.junit.Assert;


public class HistogramDifferTest extends TestCase {

    private static final Joiner LINE_JOINER = Joiner.on('\n');


    // Hack for visibility in testing.
    private static class RawTextWithFields extends RawText {
        private RawTextWithFields(byte[] bytes) {
            super(bytes);
        }

        private IntList getLines() {
            return this.lines;
        }
    }


    // Tests that RabTextSequence matches RawText function outputs, so that we know it will work the same with JGit
    // diffing/merging algorithms.
    public void testMatchesGitRawText() throws Exception {
        String textStr = ""
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n"
                + "dates\n"
                + "text\n"
                + "text\n"
                + "text\n"
                + "text\n"
                + "text\n"
                + "text\n"
                ;
        byte[] textBytes = textStr.getBytes(StandardCharsets.UTF_8);

        RawTextWithFields rawText = new RawTextWithFields(textBytes);
        RawTextComparator rawCmp = RawTextComparator.DEFAULT;

        RandomAccessBytes rab = ByteArrayRandomAccessBytes.of(textBytes);
        RabTextSequence rabText = new RabTextSequence(rab);
        RabTextComparator rabCmp = new RabTextComparator();


        assertEquals(rawText.getLines().get(0), rabText.getLines().get(0));
        assertEquals(rawText.getLines().get(1), rabText.getLines().get(1));
        assertEquals(rawText.getLines().get(2), rabText.getLines().get(2));
        assertEquals(rawText.getLines().get(3), rabText.getLines().get(3));

        assertEquals(rawText.getRawContent().length, rabText.getContent().length());
        for (int i = 0; i < rawText.getRawContent().length; ++i) {
            assertEquals(rawText.getRawContent()[i], rabText.getContent().at(i));
        }

        assertEquals(rawText.size(), rabText.size());
        for (int i = 0; i < rawText.size(); ++i) {
            assertTrue(rabCmp.equals(rabText, i, rabText, i));
            assertEquals(rawCmp.hash(rawText, i), rabCmp.hash(rabText, i));
        }


        String modifiedStr = ""
                + "new line at start\n"
                + "apples\n"
                + "bananas\n"
                + "cantaloupes\n"
                + "dates\n"
                + "text\n"
                + "text\n"
                + "extra b word here\n"
                + "text\n"
                + "text\n"
                + "text\n"
                + "text\n"
                ;
        byte[] modifiedBytes = modifiedStr.getBytes(StandardCharsets.UTF_8);
        RawTextWithFields mRawText = new RawTextWithFields(modifiedBytes);
        RandomAccessBytes mRab = ByteArrayRandomAccessBytes.of(modifiedBytes);
        RabTextSequence mRabText = new RabTextSequence(mRab);

        assertEquals(mRawText.size(), mRabText.size());
        Edit wholeEdit = new Edit(0, rawText.size(), 0, mRawText.size());

        Edit rawReduce = rawCmp.reduceCommonStartEnd(rawText, mRawText, wholeEdit);
        Edit rabReduce = rabCmp.reduceCommonStartEnd(rabText, mRabText, wholeEdit);
        assertEquals(rawReduce, rabReduce);


        // Finally, check they produce the same diffs.
        HistogramDiff gitDiffer = new HistogramDiff();
        EditList gitDiff = gitDiffer.diff(rawCmp, rawText, mRawText);

        HistogramDiffer hdDiffer = new HistogramDiffer();
        List<DiffHunk> hdDiff = hdDiffer.computeLineDiffs(rab, mRab);

        assertEquals(gitDiff.size(), hdDiff.size());
        for (int i = 0; i < gitDiff.size(); ++i) {
            Edit gitHunk = gitDiff.get(i);
            DiffHunk hdHunk = hdDiff.get(i);
            assertEquals(gitHunk.getBeginA() + 1, hdHunk.originalStart);
            assertEquals(gitHunk.getEndA() + 1, hdHunk.originalEnd);
            assertEquals(gitHunk.getBeginB() + 1, hdHunk.modifiedStart);
            assertEquals(gitHunk.getEndB() + 1, hdHunk.modifiedEnd);
        }
    }


    private String joinLinesToText(String... lines) {
        return LINE_JOINER.join(lines) + "\n";
    }


    public void testAppendLines() throws Exception {
        HistogramDiffer differ = new HistogramDiffer();
        String original = joinLinesToText(
                "apple",
                "banana");
        String modified = joinLinesToText(
                "apple",
                "banana",
                "cantaloupe",
                "daiquiri");

        List<DiffHunk> result = differ.computeLineDiffs(asRab(original), asRab(modified));
        assertHunksEqual(
                ImmutableList.of(new int[]{ 3, 3, 3, 5 }),
                result);
    }


    private void assertHunksEqual(ImmutableList<int[]> expectedIndexes, List<DiffHunk> result) {
        assertEquals(expectedIndexes.size(), result.size());

        for (int i = 0; i < expectedIndexes.size(); ++i) {
            int[] expected = expectedIndexes.get(i);
            DiffHunk hunk = result.get(i);
            if (expected.length != 4) {
                throw new IllegalArgumentException();
            }
            Assert.assertArrayEquals(
                    expected,
                    new int[]{
                            hunk.originalStart,
                            hunk.originalEnd,
                            hunk.modifiedStart,
                            hunk.modifiedEnd
                    });
        }
    }


    private RandomAccessBytes asRab(String original) {
        return ByteArrayRandomAccessBytes.of(original.getBytes(StandardCharsets.UTF_8));
    }


    // This test came from commit testing in commit_basic_changes.sh.
    public void testAppendToSingleLineFile() throws Exception {
        HistogramDiffer differ = new HistogramDiffer();
        String original = "replacing folder with file\n";
        String modified = joinLinesToText(
                "replacing folder with file",
                "",
                "blerg",
                "",
                "blerg",
                "",
                "blerg");

        List<DiffHunk> result = differ.computeLineDiffs(asRab(original), asRab(modified));
        assertHunksEqual(
                ImmutableList.of(new int[]{ 2, 2, 2, 8 }),
                result);
    }


    public void testAddLinesInDifferentPlaces() throws Exception {
        HistogramDiffer differ = new HistogramDiffer();
        String original = joinLinesToText(
                "apple",
                "banana",
                "cantaloupe",
                "denver",
                "elephant");
        String modified = joinLinesToText(
                "apple",
                "banana",
                "xxx",
                "cantaloupe",
                "denver",
                "yyy",
                "elephant");

        List<DiffHunk> result = differ.computeLineDiffs(asRab(original), asRab(modified));
        assertHunksEqual(
                ImmutableList.of(
                        new int[]{ 3, 3, 3, 4 },
                        new int[]{5, 5, 6, 7}),
                result);
    }


    // From actual testing in April 2022, when diff hunk line numbers were all off by one.
    public void testLineNumbers() {
        HistogramDiffer differ = new HistogramDiffer();
        String original = joinLinesToText(
                "line 1",
                "line 2",
                "line 3",
                "line 4",
                "line 5",
                "line 6",
                "line 7",
                "line 8",
                "line 9",
                "line 10",
                "line 11",
                "line 12");
        String modified = joinLinesToText(
                "line 1",
                "line 2",
                "line 3",
                "added line, original 8-9 is deleted below",
                "line 4",
                "line 5",
                "line 6xxx",
                "line 7",
                "line 10",
                "line 11",
                "line 12");

        List<DiffHunk> result = differ.computeLineDiffs(asRab(original), asRab(modified));
        assertEquals(3, result.size());



        assertHunksEqual(
                ImmutableList.of(
                        new int[]{ 4, 4, 4, 5 },
                        new int[]{ 6, 7, 7, 8 },
                        new int[]{ 8, 10, 9, 9 }
                ), result);
    }

}
