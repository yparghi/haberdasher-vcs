package com.haberdashervcs.common.diff.git;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class PatchedViewRandomAccessBytes implements RandomAccessBytes {

    private static final HdLogger LOG = HdLoggers.create(PatchedViewRandomAccessBytes.class);


    // The result is: base + patches[0] + patches[1] + ...
    public static PatchedViewRandomAccessBytes build(RandomAccessBytes base, List<RandomAccessBytes> patches) {
        Preconditions.checkArgument(!patches.isEmpty());

        PatchedViewRandomAccessBytes out = new PatchedViewRandomAccessBytes(base, null);
        for (RandomAccessBytes patch : patches) {
            out = new PatchedViewRandomAccessBytes(patch, out);
        }
        return out;
    }


    private final RandomAccessBytes thisData;
    @Nullable private final PatchedViewRandomAccessBytes underlying;  // null iff this is the base

    private List<BinaryDelta.PatchMapping> patchMappings;
    private int length = -1;

    private BinaryDelta.PatchMapping cachedPm = null;

    private PatchedViewRandomAccessBytes(RandomAccessBytes thisData, @Nullable PatchedViewRandomAccessBytes underlying) {
        this.thisData = Preconditions.checkNotNull(thisData);
        this.underlying = underlying;

        if (!isBase()) {
            computePatchMappings();
        } else {
            patchMappings = null;
            length = thisData.length();
        }
    }


    private void computePatchMappings() {
        Preconditions.checkState(!isBase());
        patchMappings = BinaryDelta.computePatchMappings(thisData);

        // Sort by index in the result, so that the mappings go continuously from result start to end.
        Collections.sort(patchMappings, new Comparator<BinaryDelta.PatchMapping>() {
            @Override public int compare(BinaryDelta.PatchMapping o1, BinaryDelta.PatchMapping o2) {
                return o1.outStart - o2.outStart;
            }
        });

        // Confirm they're continuous.
        BinaryDelta.PatchMapping previous = null;
        for (BinaryDelta.PatchMapping mapping : patchMappings) {
            if (previous == null) {
                if (mapping.outStart != 0) {
                    throw new IllegalStateException("Patch mapping doesn't start at 0");
                }
            } else {
                int previousEnd = previous.outStart + previous.rangeSize;
                if (mapping.outStart != previousEnd) {
                    throw new IllegalStateException(String.format(
                            "Patch mapping gap: %d -> %d", previousEnd, mapping.outStart));
                }
            }
            previous = mapping;
        }

        //LOG.info("TEMP: Built patch mappings: " + patchMappings.size());
        BinaryDelta.PatchMapping last = patchMappings.get(patchMappings.size() - 1);
        length = last.outStart + last.rangeSize;
    }


    private boolean isBase() {
        return (underlying == null);
    }


    // TODO!!! Toss at() entirely, in favor of readInto() ?
    @Override
    public byte at(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Bad index: " + index);
        }

        if (isBase()) {
            return thisData.at(index);
        }

        BinaryDelta.PatchMapping pm = binarySearch(index);
        int idxInOutRange = index - pm.outStart;
        if (pm.baseStart >= 0) {
            return underlying.at(pm.baseStart + idxInOutRange);
        } else {
            return thisData.at(pm.deltaStart + idxInOutRange);
        }
    }


    // TEMP! thoughts on faster reading (without a binsearch for each byte):
    // - patchmappings are sequential...
    // - There should be some caching of contiguous byte[] blocks in each pvrab...
    // - But how does a sub-pvrab fill in a top-down byte[] request?...
    // - each pvrab caches its last used *base* (thisData) mapping / byte[]...
    //
    // TODO!!! Toss at() entirely, in favor of readInto() ?
    // TODO!!! TESTS!
    @Override
    public int readInto(int fromIdx, int numBytes, byte[] dest) {
        if (fromIdx < 0 || fromIdx >= length) {
            throw new IndexOutOfBoundsException("Bad fromIdx: " + fromIdx);
        }

        if (isBase()) {
            return thisData.readInto(fromIdx, numBytes, dest);
        }

        // - either the pm for fromIdx is base or under...
        // - if base, read a block of bytes from thisData using readInto()
        // - if under, just delegate the call to under? and return it directly?
        if (cachedPm == null) {
            cachedPm = binarySearch(fromIdx);
        } else if (fromIdx < cachedPm.outStart) {
            cachedPm = binarySearch(fromIdx);
        } else if (fromIdx >= (cachedPm.outStart + cachedPm.rangeSize)) {
            cachedPm = binarySearch(fromIdx);
        }

        int idxInPmRange = fromIdx - cachedPm.outStart;
        numBytes = Math.min(numBytes, cachedPm.rangeSize - idxInPmRange);
        if (cachedPm.baseStart >= 0) {
            int startIdx = cachedPm.baseStart + idxInPmRange;
            int bytesRead = underlying.readInto(startIdx, numBytes, dest);
            return bytesRead;
        } else {
            int startIdx = cachedPm.deltaStart + idxInPmRange;
            int bytesRead = thisData.readInto(startIdx, numBytes, dest);
            return bytesRead;
        }
    }


    // Cribbed from: https://en.wikipedia.org/wiki/Binary_search_algorithm
    private BinaryDelta.PatchMapping binarySearch(final int targetIdx) {
        int leftIdx = 0;
        int rightIdx = patchMappings.size() - 1;

        while (leftIdx != rightIdx) {
            int midIdx = (int) Math.ceil((double) (leftIdx + rightIdx) / 2);
            BinaryDelta.PatchMapping pm = patchMappings.get(midIdx);
            if (targetIdx < pm.outStart) {
                rightIdx = midIdx - 1;
            } else {
                leftIdx = midIdx;
            }
        }

        BinaryDelta.PatchMapping pm = patchMappings.get(leftIdx);
        if (targetIdx >= pm.outStart && targetIdx < (pm.outStart + pm.rangeSize)) {
            return pm;
        } else {
            throw new IndexOutOfBoundsException("Unmapped index: " + targetIdx);
        }
    }


    @Override
    public int length() {
        return length;
    }

}
