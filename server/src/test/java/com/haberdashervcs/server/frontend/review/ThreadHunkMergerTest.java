package com.haberdashervcs.server.frontend.review;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.DiffHunkList;
import org.junit.Test;

import static org.junit.Assert.*;


public class ThreadHunkMergerTest {

    @Test
    public void threadByItself() {
        List<DiffHunk> hunks = ImmutableList.of(
                new DiffHunk(2, 4, 2, 4));
        List<DiffHunkList.ThreadLineNumberEntry> threads = ImmutableList.of(
                new DiffHunkList.ThreadLineNumberEntry("id-a", 1, 10));

        ThreadHunkMerger merger = new ThreadHunkMerger(hunks, threads);
        List<DiffHunk> merged = merger.merge();

        assertEquals(2, merged.size());

        assertEquals(2, merged.get(0).modifiedStart);
        assertEquals(4, merged.get(0).modifiedEnd);
        assertEquals(null, merged.get(0).forThreads);

        assertEquals(10, merged.get(1).modifiedStart);
        assertEquals(10, merged.get(1).modifiedEnd);
        assertEquals(1, merged.get(1).forThreads.size());
        assertEquals(10, merged.get(1).forThreads.get(0).lineNum);
    }


    @Test
    public void threadsWithinHunk() {
        List<DiffHunk> hunks = ImmutableList.of(
                new DiffHunk(2, 4, 2, 6));
        List<DiffHunkList.ThreadLineNumberEntry> threads = ImmutableList.of(
                new DiffHunkList.ThreadLineNumberEntry("id-a", 1, 3),
                new DiffHunkList.ThreadLineNumberEntry("id-b", 1, 3),
                new DiffHunkList.ThreadLineNumberEntry("id-c", 1, 4));

        ThreadHunkMerger merger = new ThreadHunkMerger(hunks, threads);
        List<DiffHunk> merged = merger.merge();

        assertEquals(1, merged.size());

        assertEquals(2, merged.get(0).modifiedStart);
        assertEquals(6, merged.get(0).modifiedEnd);
        assertEquals(3, merged.get(0).forThreads.size());
        assertEquals(3, merged.get(0).forThreads.get(0).lineNum);
        assertEquals(3, merged.get(0).forThreads.get(1).lineNum);
        assertEquals(4, merged.get(0).forThreads.get(2).lineNum);
    }


    @Test
    public void multipleThreadsOnOneLine() {
        List<DiffHunk> hunks = ImmutableList.of(
                new DiffHunk(2, 4, 2, 4));
        List<DiffHunkList.ThreadLineNumberEntry> threads = ImmutableList.of(
                new DiffHunkList.ThreadLineNumberEntry("id-a", 1, 10),
                new DiffHunkList.ThreadLineNumberEntry("id-b", 1, 10));

        ThreadHunkMerger merger = new ThreadHunkMerger(hunks, threads);
        List<DiffHunk> merged = merger.merge();

        assertEquals(2, merged.size());

        assertEquals(2, merged.get(0).modifiedStart);
        assertEquals(4, merged.get(0).modifiedEnd);
        assertEquals(null, merged.get(0).forThreads);

        assertEquals(10, merged.get(1).modifiedStart);
        assertEquals(10, merged.get(1).modifiedEnd);
        assertEquals(2, merged.get(1).forThreads.size());
        assertEquals("id-a", merged.get(1).forThreads.get(0).threadId);
        assertEquals("id-b", merged.get(1).forThreads.get(1).threadId);
    }

}
