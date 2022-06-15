package com.haberdashervcs.common.diff.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


/**
 * Computes deltas between two files, for storage as bytes in the database. This kind of diffing is distinct from the
 * textual kind used in the web UI for code review or browsing.
 */
public final class GitDeltaDiffer {

    private static final HdLogger LOG = HdLoggers.create(GitDeltaDiffer.class);


    // TODO: Use the delta size limit parameter to bail if the diff is very large, so we can save a full copy instead?
    // And/or return a RAB instead of a byte[] ?
    public static byte[] computeGitDiff(RandomAccessBytes original, RandomAccessBytes modified) throws IOException {
        DeltaIndex originalIndex = new DeltaIndex(original);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        originalIndex.encode(result, modified);
        return result.toByteArray();
    }

}
