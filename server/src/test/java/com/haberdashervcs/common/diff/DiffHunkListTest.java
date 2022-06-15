package com.haberdashervcs.common.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class DiffHunkListTest {

    @Test
    public void hunkOps() throws Exception {
        DiffHunk h1 = new DiffHunk(2, 4, 2, 3);
        h1.insertAt(2, 5);
        assertEquals(2, h1.modifiedStart);
        assertEquals(8, h1.modifiedEnd);

        DiffHunk h2 = new DiffHunk(2, 4, 2, 3);
        int deleted = h2.deleteFrom(2, 10);
        assertEquals(1, deleted);
        assertEquals(2, h2.modifiedStart);
        assertEquals(2, h2.modifiedEnd);
    }


    @Test
    public void deleteWithinHunk() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 7)));
        // Commit numbers are arbitrary.
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(2, 3, 0);

        assertEquals(1, base.size());
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(4, base.get(0).modifiedEnd);
    }


    @Test
    public void deleteIntoNewHunk() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 4)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(2, 7, 0);

        assertEquals(2, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(4, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(2, base.get(0).modifiedEnd);

        assertEquals(4, base.get(1).originalStart);
        assertEquals(9, base.get(1).originalEnd);
        assertEquals(2, base.get(1).modifiedStart);
        assertEquals(2, base.get(1).modifiedEnd);


        hunkList.finish();
        assertEquals(1, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(9, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(2, base.get(0).modifiedEnd);
    }


    @Test
    public void deleteIntoNextHunk() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 4),
                new DiffHunk(6, 8, 6, 8)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(1, 20, 0);

        assertEquals(5, base.size());

        assertEquals(1, base.get(0).originalStart);
        assertEquals(2, base.get(0).originalEnd);
        assertEquals(1, base.get(0).modifiedStart);
        assertEquals(1, base.get(0).modifiedEnd);

        assertEquals(2, base.get(1).originalStart);
        assertEquals(4, base.get(1).originalEnd);
        assertEquals(1, base.get(1).modifiedStart);
        assertEquals(1, base.get(1).modifiedEnd);

        assertEquals(4, base.get(2).originalStart);
        assertEquals(6, base.get(2).originalEnd);
        assertEquals(1, base.get(2).modifiedStart);
        assertEquals(1, base.get(2).modifiedEnd);

        assertEquals(6, base.get(3).originalStart);
        assertEquals(8, base.get(3).originalEnd);
        assertEquals(1, base.get(3).modifiedStart);
        assertEquals(1, base.get(3).modifiedEnd);

        assertEquals(8, base.get(4).originalStart);
        assertEquals(21, base.get(4).originalEnd);
        assertEquals(1, base.get(4).modifiedStart);
        assertEquals(1, base.get(4).modifiedEnd);


        hunkList.finish();
        assertEquals(1, base.size());

        assertEquals(1, base.get(0).originalStart);
        assertEquals(21, base.get(0).originalEnd);
        assertEquals(1, base.get(0).modifiedStart);
        assertEquals(1, base.get(0).modifiedEnd);
    }


    @Test
    public void insertBetweenHunks() throws Exception {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 10),
                new DiffHunk(6, 8, 14, 16)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        // I'm inserting at mLine 11. That's oLine 3.
        hunkList.change(11, 0, 10);

        assertEquals(3, base.size());

        assertEquals(3, base.get(1).originalStart);
        assertEquals(3, base.get(1).originalEnd);
        assertEquals(11, base.get(1).modifiedStart);
        assertEquals(21, base.get(1).modifiedEnd);
    }


    @Test
    public void deleteAtEnd() throws Exception {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 6)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        // Diff hunks:
        // 1) 2-2 -> 2-4
        // 2) 10-15 -> 10-10
        hunkList.change(2, 0, 2);
        hunkList.change(10, 5, 0);

        assertEquals(2, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(4, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(8, base.get(0).modifiedEnd);

        // Say there are versions v0, v1, and v2.
        // In v1, lines 2-4 in v0 are expanded to lines 2-6. Then we expand them again in v2 to lines 2-8.
        // So deletion at line 10 in v2 is deletion at 2 lines past the original 2-4 range in v0.
        assertEquals(6, base.get(1).originalStart);
        assertEquals(11, base.get(1).originalEnd);
        assertEquals(10, base.get(1).modifiedStart);
        assertEquals(10, base.get(1).modifiedEnd);
    }


    // From testing code review refactoring, with a real change of insert + change + delete.
    @Test
    public void offsetsFromNothing() throws Exception {
        ArrayList<DiffHunk> diffs = new ArrayList<>();
        DiffHunkList hunkList = new DiffHunkList(1, diffs, Collections.emptyList());

        // Diff hunks: insert at base line 4, change base line 6, delete base lines 8-9.
        // 1) 4-4 -> 4-5
        // 2) 6-7 -> 7-8
        // 3) 8-10 -> 9-9
        hunkList.change(4, 0, 1);
        hunkList.change(7, 1, 1);
        hunkList.change(9, 2, 0);

        assertEquals(3, diffs.size());

        assertEquals(4, diffs.get(0).originalStart);
        assertEquals(4, diffs.get(0).originalEnd);
        assertEquals(4, diffs.get(0).modifiedStart);
        assertEquals(5, diffs.get(0).modifiedEnd);

        assertEquals(6, diffs.get(1).originalStart);
        assertEquals(7, diffs.get(1).originalEnd);
        assertEquals(7, diffs.get(1).modifiedStart);
        assertEquals(8, diffs.get(1).modifiedEnd);

        assertEquals(8, diffs.get(2).originalStart);
        assertEquals(10, diffs.get(2).originalEnd);
        assertEquals(9, diffs.get(2).modifiedStart);
        assertEquals(9, diffs.get(2).modifiedEnd);
    }


    @Test
    public void insertAtEnd() throws Exception {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 4)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(2, 2, 0);
        hunkList.change(10, 0, 10);

        assertEquals(2, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(4, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(2, base.get(0).modifiedEnd);

        assertEquals(10, base.get(1).originalStart);
        assertEquals(10, base.get(1).originalEnd);
        assertEquals(8, base.get(1).modifiedStart);
        assertEquals(18, base.get(1).modifiedEnd);
    }


    @Test
    public void combinedOp() throws Exception {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 4),
                new DiffHunk(6, 8, 6, 8)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(5, 5, 10);

        assertEquals(4, base.size());

        assertEquals(5, base.get(1).originalStart);
        assertEquals(6, base.get(1).originalEnd);
        assertEquals(5, base.get(1).modifiedStart);
        assertEquals(5, base.get(1).modifiedEnd);

        assertEquals(6, base.get(2).originalStart);
        assertEquals(8, base.get(2).originalEnd);
        assertEquals(5, base.get(2).modifiedStart);
        assertEquals(5, base.get(2).modifiedEnd);

        assertEquals(8, base.get(3).originalStart);
        assertEquals(10, base.get(3).originalEnd);
        assertEquals(5, base.get(3).modifiedStart);
        assertEquals(15, base.get(3).modifiedEnd);


        hunkList.finish();
        assertEquals(2, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(4, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(4, base.get(0).modifiedEnd);

        assertEquals(5, base.get(1).originalStart);
        assertEquals(10, base.get(1).originalEnd);
        assertEquals(5, base.get(1).modifiedStart);
        assertEquals(15, base.get(1).modifiedEnd);
    }


    @Test
    public void offsetForLaterHunks() throws Exception {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(2, 4, 2, 4),
                new DiffHunk(6, 8, 6, 8),
                new DiffHunk(10, 12, 10, 12)));
        DiffHunkList hunkList = new DiffHunkList(1, base, Collections.emptyList());

        hunkList.change(3, 1, 0);
        hunkList.change(6, 1, 0);
        hunkList.finish();

        assertEquals(3, base.size());

        assertEquals(2, base.get(0).originalStart);
        assertEquals(4, base.get(0).originalEnd);
        assertEquals(2, base.get(0).modifiedStart);
        assertEquals(3, base.get(0).modifiedEnd);

        // This hunk is directly modified, and needs an offset.
        assertEquals(6, base.get(1).originalStart);
        assertEquals(8, base.get(1).originalEnd);
        assertEquals(5, base.get(1).modifiedStart);
        assertEquals(6, base.get(1).modifiedEnd);

        // This hunk is not modified, but needs an offset.
        assertEquals(10, base.get(2).originalStart);
        assertEquals(12, base.get(2).originalEnd);
        assertEquals(8, base.get(2).modifiedStart);
        assertEquals(10, base.get(2).modifiedEnd);
    }


    @Test
    public void threadHunkDeletion() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(10, 15, 10, 15)));

        List<DiffHunkList.ThreadLineNumberEntry> threads = new ArrayList<>();
        threads.add(new DiffHunkList.ThreadLineNumberEntry("thread-1", 1, 12));

        DiffHunkList hunkList = new DiffHunkList(2, base, threads);
        // Thread gets replaced -- the 5 lines around it are replaced with 10 new ones.
        hunkList.change(10, 5, 10);

        assertEquals(10, threads.get(0).lineNum);
    }


    @Test
    public void threadHunkInsertion() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(10, 15, 10, 15)));

        List<DiffHunkList.ThreadLineNumberEntry> threads = new ArrayList<>();
        threads.add(new DiffHunkList.ThreadLineNumberEntry("thread-1", 1, 12));

        DiffHunkList hunkList = new DiffHunkList(2, base, threads);
        hunkList.change(10, 0, 5);

        assertEquals(17, threads.get(0).lineNum);
    }


    @Test
    public void threadOffsetAtEnd() {
        ArrayList<DiffHunk> base = new ArrayList<>(Arrays.asList(
                new DiffHunk(10, 15, 10, 15)));

        List<DiffHunkList.ThreadLineNumberEntry> threads = new ArrayList<>();
        threads.add(new DiffHunkList.ThreadLineNumberEntry("thread-1", 1, 20));

        DiffHunkList hunkList = new DiffHunkList(2, base, threads);
        hunkList.change(10, 0, 50);
        hunkList.finish();

        assertEquals(70, threads.get(0).lineNum);
    }

}
