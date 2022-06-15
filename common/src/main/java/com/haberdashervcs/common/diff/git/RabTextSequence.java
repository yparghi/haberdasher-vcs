// Based on org.eclipse.jgit.diff.RawText

/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */


package com.haberdashervcs.common.diff.git;

import java.nio.charset.StandardCharsets;

import com.haberdashervcs.common.diff.TextLinesSource;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.util.IntList;


public final class RabTextSequence
        extends Sequence
        // TODO! toss this
        implements TextLinesSource {

    /**
     * The file content for this sequence.
     */
    private final RandomAccessBytes content;

    /**
     * Map of line number to starting position within {@link #content}.
     */
    private final IntList lines;

    public RabTextSequence(RandomAccessBytes content) {
        this.content = content;
        // TODO: Consider evaluating the line map lazily, like only N lines at a time as called for, since for example
        // diffing may stop after the first M lines of a file, so we wouldn't need to compute the whole line map. Or,
        // would this be obviated by precomputing diffs on push?
        this.lines = lineMap(content);
    }


    /** @return total number of items in the sequence. */
    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        // The line map is always 2 entries larger than the number of lines in
        // the file. Index 0 is padded out/unused. The last index is the total
        // length of the buffer, and acts as a sentinel.
        //
        return lines.size() - 2;
    }


    RandomAccessBytes getContent() {
        return content;
    }


    IntList getLines() {
        return lines;
    }


    // Based on JGit RawParseUtils.lineMap()
    private static IntList lineMap(RandomAccessBytes content) {
        final int end = content.length();
        int ptr = 0;

        IntList map = new IntList(end / 36);
        map.fillTo(1, Integer.MIN_VALUE);

        for (; ptr < end; ptr = nextLF(content, ptr, end)) {
            map.add(ptr);
        }
        map.add(end);
        return map;
    }


    // Based on RawParseUtils.nextLF()
    private static int nextLF(RandomAccessBytes content, int ptr, int end) {
        while (ptr < end) {
            if (content.at(ptr++) == '\n') {
                return ptr;
            }
        }
        return ptr;
    }


    @Override
    public String getLine(int lineNumber) {
        int lineStart = lines.get(lineNumber);
        int lineEnd = lines.get(lineNumber + 1);
        byte[] bytes = new byte[lineEnd - lineStart];
        for (int i = lineStart; i < lineEnd; ++i) {
            bytes[i - lineStart] = content.at(i);
        }
        // TODO: Support different encodings, how?
        return new String(bytes, StandardCharsets.UTF_8);
    }


    public int getLineStartingByte(int lineNumber) {
        return lines.get(lineNumber);
    }

}
