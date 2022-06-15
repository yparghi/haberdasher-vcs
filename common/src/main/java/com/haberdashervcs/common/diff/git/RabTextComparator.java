// Based on org.eclipse.jgit.diff.RawTextComparator

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

import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.SequenceComparator;
import org.eclipse.jgit.util.IntList;


final class RabTextComparator extends SequenceComparator<RabTextSequence> {

    RabTextComparator() {}


    @Override
    public int hash(RabTextSequence seq, int lno) {
        final int begin = seq.getLines().get(lno + 1);
        final int end = seq.getLines().get(lno + 2);
        return hashRegion(seq.getContent(), begin, end);
    }


    /** {@inheritDoc} */
    @Override
    public Edit reduceCommonStartEnd(RabTextSequence a, RabTextSequence b, Edit e) {
        // This is a faster exact match based form that tries to improve
        // performance for the common case of the header and trailer of
        // a text file not changing at all. After this fast path we use
        // the slower path based on the super class' using equals() to
        // allow for whitespace ignore modes to still work.

        if (e.getBeginA() == e.getEndA() || e.getBeginB() == e.getEndB()) {
            return e;
        }

        RandomAccessBytes aRaw = a.getContent();
        RandomAccessBytes bRaw = b.getContent();

        int aPtr = a.getLines().get(e.getBeginA() + 1);
        int bPtr = a.getLines().get(e.getBeginB() + 1);

        int aEnd = a.getLines().get(e.getEndA() + 1);
        int bEnd = b.getLines().get(e.getEndB() + 1);

        // This can never happen, but the JIT doesn't know that. If we
        // define this assertion before the tight while loops below it
        // should be able to skip the array bound checks on access.
        //
        if (aPtr < 0 || bPtr < 0 || aEnd > aRaw.length() || bEnd > bRaw.length())
            throw new ArrayIndexOutOfBoundsException();

        while (aPtr < aEnd && bPtr < bEnd && aRaw.at(aPtr) == bRaw.at(bPtr)) {
            aPtr++;
            bPtr++;
        }

        while (aPtr < aEnd && bPtr < bEnd && aRaw.at(aEnd - 1) == bRaw.at(bEnd - 1)) {
            aEnd--;
            bEnd--;
        }

        int newBeginA, newEndA, newBeginB, newEndB;
        newBeginA = findForwardLine(a.getLines(), e.getBeginA(), aPtr);
        newBeginB = findForwardLine(b.getLines(), e.getBeginB(), bPtr);

        newEndA = findReverseLine(a.getLines(), e.getEndA(), aEnd);

        final boolean partialA = aEnd < a.getLines().get(newEndA + 1);
        if (partialA)
            bEnd += a.getLines().get(newEndA + 1) - aEnd;

        newEndB = findReverseLine(b.getLines(), e.getEndB(), bEnd);

        if (!partialA && bEnd < b.getLines().get(newEndB + 1))
            newEndA++;

        Edit newEdit = new Edit(newBeginA, newEndA, newBeginB, newEndB);
        return super.reduceCommonStartEnd(a, b, newEdit);
    }

    private static int findForwardLine(IntList lines, int idx, int ptr) {
        final int end = lines.size() - 2;
        while (idx < end && lines.get(idx + 2) < ptr)
            idx++;
        return idx;
    }

    private static int findReverseLine(IntList lines, int idx, int ptr) {
        while (0 < idx && ptr <= lines.get(idx))
            idx--;
        return idx;
    }


    @Override
    public boolean equals(RabTextSequence a, int ai, RabTextSequence b, int bi) {
        ai++;
        bi++;

        int as = a.getLines().get(ai);
        int bs = b.getLines().get(bi);
        final int ae = a.getLines().get(ai + 1);
        final int be = b.getLines().get(bi + 1);

        if (ae - as != be - bs)
            return false;

        while (as < ae) {
            if (a.getContent().at(as++) != b.getContent().at(bs++))
                return false;
        }
        return true;
    }


    // Based on RawTextComparator.DEFAULT
    private int hashRegion(RandomAccessBytes raw, int ptr, int end) {
        int hash = 5381;
        for (; ptr < end; ptr++)
            hash = ((hash << 5) + hash) + (raw.at(ptr) & 0xff);
        return hash;
    }
}
