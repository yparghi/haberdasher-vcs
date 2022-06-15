package com.haberdashervcs.server.example;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.TextOrBinaryChecker;
import com.haberdashervcs.common.diff.git.GitDeltaDiffer;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;


/**
 * For running arbitrary tests of server code.
 */
public final class TestWhatever {

    public static void main(String[] args) throws Exception {
        p("Hello TestWhatever!");

        //testTextBinary();
        //testDiffFile();
        //testRounding();
        testVersionRegex();
    }


    private static void testVersionRegex() {
        Pattern VERSION_REGEX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
        p("1: %s", VERSION_REGEX.matcher("1.0.0").matches());
        p("2: %s", VERSION_REGEX.matcher("alpha 6").matches());

        Matcher matcher = VERSION_REGEX.matcher("1.0.12");
        matcher.matches();
        p("3: %s", matcher.group(1));
        p("4: %s", matcher.group(2));
        p("5: %s", matcher.group(3));
    }


    private static void p(String msg, Object... args)  {
        System.out.println(String.format(msg, args));
    }


    private static void testTextBinary() throws Exception {
        List<String> testPaths = ImmutableList.of(
                "/home/Yash/src/haberdasher/dev/testscripts/large_text_file.txt",
                "/home/Yash/src/haberdasher/dev/testscripts/large_binary_file.bin");

        for (String pathStr : testPaths) {
            Path path = Paths.get(pathStr);
            byte[] contents = Files.readAllBytes(path);
            Optional<String> asText = TextOrBinaryChecker.convertToString(contents);
            p("Got result for " + path + ": " + asText.isPresent());
        }
    }


    // $ export BS=1000
    // $ dd if=/dev/urandom of=/tmp/big_diff_original.bin bs=$BS count=500
    // $ cp /tmp/big_diff_original.bin /tmp/big_diff_modified.bin
    // $ dd if=/dev/urandom bs=$BS count=100 >> /tmp/big_diff_modified.bin
    //
    // Times (millis) with raw RandomAccessFile (no buffering):
    // BS=1000 (500K): 3,669
    // BS=10000 (5M): 32,588  -- result size 1,008,038
    //
    // Some seeks from the 500K test:
    // - 599977, 599978, 599979, ...
    // - So, lots of these small jumps.
    //
    // Times after single-buffer impl:
    // BS=1000 (500K): 362
    // BS=10000 (5M): 1276
    private static void testDiffFile() throws Exception {
        final long start = System.currentTimeMillis();
        p("Start time: %d", start);
        String path1 = "/tmp/big_diff_original.bin";
        String path2 = "/tmp/big_diff_modified.bin";
        RandomAccessBytes rab1 = FileRandomAccessBytes.of(new File(path1));
        RandomAccessBytes rab2 = FileRandomAccessBytes.of(new File(path2));
        byte[] result = GitDeltaDiffer.computeGitDiff(rab1, rab2);
        final long end = System.currentTimeMillis();
        p("End time: %d", end);
        p("Elapsed time millis: %d", end - start);
        p("Got result size: %d", result.length);
    }


    private static void testRounding() {
        p("testRounding");

        int result1 = (int) Math.ceil((double)(4 + 5) / 2);
        p("result1: %d", result1);

        int result2 = (int) Math.ceil((double)(4 + 6) / 2);
        p("result2: %d", result2);

        int result3 = (int) Math.ceil((double)(7 + 20) / 2);
        p("result3: %d", result3);
    }

}
