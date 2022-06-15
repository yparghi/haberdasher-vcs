package com.haberdashervcs.common.diff.git;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class GitDeltaDifferTest {

    private static final HdLogger LOG = HdLoggers.create(GitDeltaDifferTest.class);


    @Test
    public void deltaForFiles() throws Exception {
        URL testFileUrl = GitDeltaDifferTest.class.getResource("/large_text_file.txt");
        Path path = Paths.get(testFileUrl.toURI());
        FileRandomAccessBytes fileRab = FileRandomAccessBytes.of(path.toFile());

        assertEquals(1104737, fileRab.length());

        byte[] fileBytes = RandomAccessBytes.toByteArray(fileRab);
        String asString = new String(fileBytes, StandardCharsets.UTF_8);

        String modified = asString + "\nSome new text added for Haberdasher\n";
        RandomAccessBytes modifiedRab = ByteArrayRandomAccessBytes.of(
                modified.getBytes(StandardCharsets.UTF_8));

        byte[] diff = GitDeltaDiffer.computeGitDiff(fileRab, modifiedRab);
        LOG.info("Got diff: %d", diff.length);

        RandomAccessBytes diffRab = ByteArrayRandomAccessBytes.of(diff);
        PatchedViewRandomAccessBytes patched = PatchedViewRandomAccessBytes.build(
                fileRab, ImmutableList.of(diffRab));

        String patchedStr = new String(
                RandomAccessBytes.toByteArray(patched),
                StandardCharsets.UTF_8);
        assertEquals(modified, patchedStr);

        // A long diff, that will be stored in LFS.
        // NOTE: If we fix it so that diffs that are bigger than the base get stored as full, then this won't be true
        // anymore.
        URL testFile2 = GitDeltaDifferTest.class.getResource("/large_text_file_2.txt");
        Path path2 = Paths.get(testFile2.toURI());
        FileRandomAccessBytes fileRab2 = FileRandomAccessBytes.of(path2.toFile());
        byte[] diff2 = GitDeltaDiffer.computeGitDiff(modifiedRab, fileRab2);
        LOG.info("Got diff2: %d", diff2.length);
        RandomAccessBytes diffRab2 = ByteArrayRandomAccessBytes.of(diff2);
        PatchedViewRandomAccessBytes patched2 = PatchedViewRandomAccessBytes.build(
                fileRab, ImmutableList.of(diffRab, diffRab2));

        String patched2Str = new String(
                RandomAccessBytes.toByteArray(patched2),
                StandardCharsets.UTF_8);

        String modified2Str = new String(
                RandomAccessBytes.toByteArray(fileRab2), StandardCharsets.UTF_8);
        assertEquals(modified2Str, patched2Str);
    }


    @Test
    public void deltaStreaming() throws Exception {
        final String longer = "0123456789abcdef";

        testPatches(
                "1) Add one letter",
                "abcd",
                "abcde");

        testPatches(
                "2) Remove one letter",
                "abcd",
                "abc");

        testPatches(
                "3) Insert in the middle",
                Strings.repeat(longer, 2),
                Strings.repeat(longer, 2)
                        + "xxxxxx"
                        + Strings.repeat(longer, 2)
                        + "yyyyyy"
                        + Strings.repeat(longer, 2));

        testPatches(
                "4) Longer string, with reads from base",
                Strings.repeat(longer, 10),
                Strings.repeat(longer, 12));

        testPatches(
                "5) Base, modified, back to base",
                "abcdef",
                "abcdefghi",
                "abcdef");

        String[] steps = new String[] {
                Strings.repeat(longer, 2),
                Strings.repeat(longer, 2) + "xxx",
                Strings.repeat(longer, 2) + "xxx" + longer
        };
        for (int i = 0; i < steps.length; ++i) {
            testPatches(
                    String.format("6-%d) Multiple layers", i),
                    longer,
                    Arrays.copyOfRange(steps, 0, i+1));
        }
    }


    private void testPatches(
            String caseName, String base, String... modifiedContents)
            throws IOException {
        Preconditions.checkArgument(modifiedContents.length >= 1);
        LOG.info("testPatchStream: Case %s", caseName);

        RandomAccessBytes baseRab = ByteArrayRandomAccessBytes.of(base.getBytes(StandardCharsets.UTF_8));
        RandomAccessBytes previousBase = baseRab;
        List<RandomAccessBytes> patches = new ArrayList<>();
        for (String modified : modifiedContents) {
            RandomAccessBytes modifiedRab = ByteArrayRandomAccessBytes.of(modified.getBytes(StandardCharsets.UTF_8));
            byte[] diffBytes = GitDeltaDiffer.computeGitDiff(previousBase, modifiedRab);
            patches.add(ByteArrayRandomAccessBytes.of(diffBytes));
            previousBase = modifiedRab;
        }

        PatchedViewRandomAccessBytes patched = PatchedViewRandomAccessBytes.build(baseRab, patches);
        byte[] result = RandomAccessBytes.toByteArray(patched);

        assertArrayEquals(
                modifiedContents[modifiedContents.length - 1].getBytes(StandardCharsets.UTF_8),
                result);
    }

}
