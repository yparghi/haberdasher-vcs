package com.haberdashervcs.common.io.rab;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;


/**
 * For an in-memory buffer, keeps track of indexes/offsets into the backing file.
 */
public final class BufferIndexer {


    public static final class BufferRange {
        private final int leftIdx;  // Inclusive
        private final int rightIdx;  // Exclusive

        BufferRange(int leftIdx, int rightIdx) {
            this.leftIdx = leftIdx;
            this.rightIdx = rightIdx;
        }

        public int getLeftIdx() {
            return leftIdx;
        }

        public int getRightIdx() {
            return rightIdx;
        }

        @Override public String toString() {
            return String.format("BufferRange(%d, %d)", leftIdx, rightIdx);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BufferRange that = (BufferRange) o;
            return leftIdx == that.leftIdx && rightIdx == that.rightIdx;
        }

        @Override
        public int hashCode() {
            return Objects.hash(leftIdx, rightIdx);
        }
    }


    private final int bufSize;
    private final int fileSize;

    private BufferRange current = null;

    public BufferIndexer(int bufSize, int fileSize) {
        Preconditions.checkArgument(bufSize <= fileSize);
        this.bufSize = bufSize;
        this.fileSize = fileSize;
    }


    public Optional<BufferRange> shouldRebufferFor(int idx) {
        if (current != null && idx >= current.leftIdx && idx < current.rightIdx) {
            return Optional.empty();

        } else {
            int left = idx - (bufSize / 2);
            if (left < 0) {
                left = 0;
            }
            int right = left + bufSize;
            if (right > fileSize) {
                right = fileSize;
                left = right - bufSize;
            }
            current = new BufferRange(left, right);
            return Optional.of(current);
        }
    }

}
