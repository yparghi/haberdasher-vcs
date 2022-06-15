package com.haberdashervcs.server.frontend.review;

import java.util.ArrayList;
import java.util.List;

import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.DiffHunkList;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


// TODO: Helper method(s) for FileDiffEntry creation.
public class FileReviewDisplayTest {

    @Test
    public void singleDiff() throws Exception {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(5, 10, 5, 10))
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.ReviewThread thread1 = ReviewsProto.ReviewThread.newBuilder()
                .setId("thread1-id")
                .setCommitId(1)
                .setFilePath(path)
                .setState(ReviewsProto.ReviewThread.State.ACTIVE)
                .setLineNumberType(ReviewsProto.ReviewThread.LineNumberType.MODIFIED) // TODO: Do we need this still?
                .setLineNumber(8)
                .addComments(
                        ReviewsProto.ReviewComment.newBuilder().setUserId("user1").setText("comment text").build())
                .build();
        threads.add(thread1);

        FileReviewDisplay frd = FileReviewDisplay.of(path, 0, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();

        List<DiffHunk> resultHunks = result.getHunks();
        List<DiffHunkList.ThreadLineNumberEntry> resultThreads = result.getThreads();

        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result.getType());
        assertEquals("id-0", result.getBeforeFileId());
        assertEquals("id-1", result.getAfterFileId());

        assertEquals(1, resultHunks.size());
        assertEquals(1, resultThreads.size());
        assertEquals(5, resultHunks.get(0).originalStart);
        assertEquals(8, resultThreads.get(0).lineNum);
    }


    private ReviewsProto.TextDiffHunk hunk(int oFrom, int oTo, int mFrom, int mTo) {
        return ReviewsProto.TextDiffHunk.newBuilder()
                .setOriginalFromLine(oFrom)
                .setOriginalToLine(oTo)
                .setModifiedFromLine(mFrom)
                .setModifiedToLine(mTo)
                .build();
    }


    @Test
    public void twoDiffs() throws Exception {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(5, 10, 5, 10))
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        // Add 5 lines at line # 5.
        ReviewsProto.FileDiffHunks hunks2 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(5, 5, 5, 10))
                .build();
        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-1")
                .setThisId("id-2")
                .setDiffHunks(hunks2)
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        threads.add(thread(path, "thread1-id", 1, 8));

        FileReviewDisplay frd = FileReviewDisplay.of(path, 0, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();
        List<DiffHunk> resultHunks = result.getHunks();
        List<DiffHunkList.ThreadLineNumberEntry> resultThreads = result.getThreads();

        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result.getType());
        assertEquals("id-0", result.getBeforeFileId());
        assertEquals("id-2", result.getAfterFileId());

        assertEquals(1, resultHunks.size());
        assertEquals(5, resultHunks.get(0).originalStart);
        assertEquals(10, resultHunks.get(0).originalEnd);
        assertEquals(5, resultHunks.get(0).modifiedStart);
        assertEquals(15, resultHunks.get(0).modifiedEnd);

        assertEquals(1, resultThreads.size());
        assertEquals(13, resultThreads.get(0).lineNum);
    }


    @Test
    public void threadOffsetBetweenAndAfterHunks() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(5, 5, 5, 10))
                .addTextDiffHunks(hunk(15, 20, 15, 20))
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        threads.add(thread(path, "thread0-between-id", 0, 12));
        threads.add(thread(path, "thread0-after-id", 0, 30));

        FileReviewDisplay frd = FileReviewDisplay.of(path, 0, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();
        List<DiffHunk> resultHunks = result.getHunks();
        List<DiffHunkList.ThreadLineNumberEntry> resultThreads = result.getThreads();

        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result.getType());
        assertEquals("id-0", result.getBeforeFileId());
        assertEquals("id-1", result.getAfterFileId());

        assertEquals(2, resultHunks.size());

        assertEquals(5, resultHunks.get(0).originalStart);
        assertEquals(5, resultHunks.get(0).originalEnd);
        assertEquals(5, resultHunks.get(0).modifiedStart);
        assertEquals(10, resultHunks.get(0).modifiedEnd);

        assertEquals(15, resultHunks.get(1).originalStart);
        assertEquals(20, resultHunks.get(1).originalEnd);
        assertEquals(15, resultHunks.get(1).modifiedStart);
        assertEquals(20, resultHunks.get(1).modifiedEnd);


        assertEquals(2, resultThreads.size());

        assertEquals(17, resultThreads.get(0).lineNum);
        assertEquals(35, resultThreads.get(1).lineNum);
    }


    private ReviewsProto.ReviewThread thread(String path, String threadId, long commitId, int lineNumber) {
        return ReviewsProto.ReviewThread.newBuilder()
                .setId(threadId)
                .setCommitId(commitId)
                .setFilePath(path)
                .setState(ReviewsProto.ReviewThread.State.ACTIVE)
                .setLineNumberType(ReviewsProto.ReviewThread.LineNumberType.MODIFIED)
                .setLineNumber(lineNumber)
                .addComments(
                        ReviewsProto.ReviewComment.newBuilder().setUserId("user1").setText("comment text").build())
                .build();
    }


    @Test
    public void addedThenDiff() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);


        ReviewsProto.FileDiffHunks hunks2 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(2, 4, 2, 4))
                .build();
        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-1")
                .setThisId("id-2")
                .setDiffHunks(hunks2)
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);


        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, result1.getType());
        assertEquals(null, result1.getBeforeFileId());
        assertEquals("id-2", result1.getAfterFileId());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 1, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result2.getType());
        assertEquals("id-1", result2.getBeforeFileId());
        assertEquals("id-2", result2.getAfterFileId());
    }


    @Test
    public void addedThenNothing() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);


        FileReviewDisplay frd = FileReviewDisplay.of(path, 0, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type = result.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type);
        assertEquals(null, result.getBeforeFileId());
        assertEquals("id-1", result.getAfterFileId());
    }


    @Test
    public void addedThenDeleted() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type1 = result1.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type1);
        assertEquals(null, result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 1, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type2 = result2.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DELETED, type2);
        assertEquals("id-1", result2.getBeforeFileId());
        assertEquals(null, result2.getAfterFileId());

        FileReviewDisplay frd3 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result3 = frd3.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type3 = result3.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED_THEN_DELETED, type3);
        assertEquals(null, result3.getBeforeFileId());
        assertEquals(null, result3.getAfterFileId());
    }


    @Test
    public void diffThenDeletedThenAdded() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(2, 4, 2, 4))
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        ReviewsProto.FileDiffEntry diff3 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-3")
                .build();
        FileReviewDisplay.DiffWithCommitId dc3 = new FileReviewDisplay.DiffWithCommitId(3, diff3);
        diffs.add(dc3);


        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type1 = result1.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, type1);
        assertEquals("id-0", result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type2 = result2.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DELETED, type2);
        assertEquals("id-0", result2.getBeforeFileId());
        assertEquals(null, result2.getAfterFileId());

        FileReviewDisplay frd3 = FileReviewDisplay.of(path, 0, 3, diffs, threads);
        FileReviewDisplay.FileDiffResult result3 = frd3.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type3 = result3.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type3);
        assertEquals(null, result3.getBeforeFileId());
        assertEquals("id-3", result3.getAfterFileId());

        FileReviewDisplay frd4 = FileReviewDisplay.of(path, 1, 3, diffs, threads);
        FileReviewDisplay.FileDiffResult result4 = frd4.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type4 = result4.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type4);
        assertEquals(null, result4.getBeforeFileId());
        assertEquals("id-3", result4.getAfterFileId());
    }


    @Test
    public void deletedThenAddedThenDeletedAgain() {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-0")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-2")
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        ReviewsProto.FileDiffEntry diff3 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-2")
                .build();
        FileReviewDisplay.DiffWithCommitId dc3 = new FileReviewDisplay.DiffWithCommitId(3, diff3);
        diffs.add(dc3);


        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type1 = result1.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DELETED, type1);
        assertEquals("id-0", result1.getBeforeFileId());
        assertEquals(null, result1.getAfterFileId());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type2 = result2.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type2);
        assertEquals(null, result2.getBeforeFileId());
        assertEquals("id-2", result2.getAfterFileId());

        FileReviewDisplay frd3 = FileReviewDisplay.of(path, 0, 3, diffs, threads);
        FileReviewDisplay.FileDiffResult result3 = frd3.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type3 = result3.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DELETED, type3);
        assertEquals("id-2", result3.getBeforeFileId());
        assertEquals(null, result3.getAfterFileId());

        FileReviewDisplay frd4 = FileReviewDisplay.of(path, 1, 3, diffs, threads);
        FileReviewDisplay.FileDiffResult result4 = frd4.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type4 = result4.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED_THEN_DELETED, type4);
        assertEquals(null, result4.getBeforeFileId());
        assertEquals(null, result4.getAfterFileId());
    }


    @Test
    public void binaryAddAndDelete() {
        String path = "/some/file.bin";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type1 = result1.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type1);
        assertEquals(null, result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type2 = result2.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED_THEN_DELETED, type2);
        assertEquals(null, result2.getBeforeFileId());
        assertEquals(null, result2.getAfterFileId());

        FileReviewDisplay frd3 = FileReviewDisplay.of(path, 1, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result3 = frd3.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type3 = result3.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DELETED, type3);
        assertEquals("id-1", result3.getBeforeFileId());
        assertEquals(null, result3.getAfterFileId());
    }


    @Test
    public void binaryDiff() {
        String path = "/some/file.bin";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .setBinaryDiff(ReviewsProto.BinaryDiff.newBuilder()
                        .setNumBytesOriginal(10)
                        .setNumBytesModified(20)
                        .build())
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffHunks hunks2 = ReviewsProto.FileDiffHunks.newBuilder()
                .setBinaryDiff(ReviewsProto.BinaryDiff.newBuilder()
                        .setNumBytesOriginal(20)
                        .setNumBytesModified(40)
                        .build())
                .build();
        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-1")
                .setThisId("id-2")
                .setDiffHunks(hunks2)
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result1.getType());
        assertTrue(result1.isBinaryDiff());
        assertEquals("id-0", result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());
        assertEquals(10, result1.getBinaryBytesBefore());
        assertEquals(20, result1.getBinaryBytesAfter());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result2.getType());
        assertTrue(result2.isBinaryDiff());
        assertEquals("id-0", result2.getBeforeFileId());
        assertEquals("id-2", result2.getAfterFileId());
        assertEquals(10, result2.getBinaryBytesBefore());
        assertEquals(40, result2.getBinaryBytesAfter());

        FileReviewDisplay frd3 = FileReviewDisplay.of(path, 1, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result3 = frd3.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result3.getType());
        assertTrue(result3.isBinaryDiff());
        assertEquals("id-1", result3.getBeforeFileId());
        assertEquals("id-2", result3.getAfterFileId());
        assertEquals(20, result3.getBinaryBytesBefore());
        assertEquals(40, result3.getBinaryBytesAfter());
    }


    @Test
    public void binaryToText() {
        String path = "/some/file.bin";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .setBinaryDiff(ReviewsProto.BinaryDiff.newBuilder()
                        .setNumBytesOriginal(10)
                        .setNumBytesModified(20)
                        .build())
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffHunks hunks2 = ReviewsProto.FileDiffHunks.newBuilder()
                .setBinaryDiff(ReviewsProto.BinaryDiff.newBuilder()
                        .setNumBytesOriginal(20)
                        .setNumBytesModified(-1)
                        .build())
                .build();
        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-1")
                .setThisId("id-2")
                .setDiffHunks(hunks2)
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result1.getType());
        assertEquals("id-0", result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());
        assertTrue(result1.isBinaryDiff());
        assertEquals(10, result1.getBinaryBytesBefore());
        assertEquals(20, result1.getBinaryBytesAfter());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        FileReviewDisplay.FileDiffResult.Type type2 = result2.getType();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, type2);
        assertEquals(null, result2.getBeforeFileId());
        assertEquals("id-2", result2.getAfterFileId());
        assertFalse(result2.isBinaryDiff());
    }


    @Test
    public void textToBinary() {
        String path = "/some/file.bin_or_text";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffHunks hunks1 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(2, 4, 2, 4))
                .build();
        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .setDiffHunks(hunks1)
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffHunks hunks2 = ReviewsProto.FileDiffHunks.newBuilder()
                .setBinaryDiff(ReviewsProto.BinaryDiff.newBuilder()
                        .setNumBytesOriginal(-1)
                        .setNumBytesModified(20)
                        .build())
                .build();
        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setPreviousId("id-1")
                .setThisId("id-2")
                .setDiffHunks(hunks2)
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        FileReviewDisplay frd1 = FileReviewDisplay.of(path, 0, 1, diffs, threads);
        FileReviewDisplay.FileDiffResult result1 = frd1.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.DIFF, result1.getType());
        assertEquals("id-0", result1.getBeforeFileId());
        assertEquals("id-1", result1.getAfterFileId());
        assertFalse(result1.isBinaryDiff());

        FileReviewDisplay frd2 = FileReviewDisplay.of(path, 0, 2, diffs, threads);
        FileReviewDisplay.FileDiffResult result2 = frd2.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, result2.getType());
        assertEquals("id-2", result2.getAfterFileId());
    }


    @Test
    public void threadOffsetMultipleChanges() throws Exception {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        threads.add(thread(path, "thread0-to-be-orphaned", 0, 12));
        threads.add(thread(path, "thread2-moves-around", 2, 30));

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DELETE)
                .setPreviousId("id-0")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        ReviewsProto.FileDiffEntry diff2 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setThisId("id-2")
                .build();
        FileReviewDisplay.DiffWithCommitId dc2 = new FileReviewDisplay.DiffWithCommitId(2, diff2);
        diffs.add(dc2);

        // Insert 10 lines before.
        ReviewsProto.FileDiffHunks hunks3 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(10, 10, 10, 20))
                .build();
        ReviewsProto.FileDiffEntry diff3 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setThisId("id-3")
                .setDiffHunks(hunks3)
                .build();
        FileReviewDisplay.DiffWithCommitId dc3 = new FileReviewDisplay.DiffWithCommitId(3, diff3);
        diffs.add(dc3);

        // Delete 5 lines before.
        ReviewsProto.FileDiffHunks hunks4 = ReviewsProto.FileDiffHunks.newBuilder()
                .addTextDiffHunks(hunk(20, 25, 20, 20))
                .build();
        ReviewsProto.FileDiffEntry diff4 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.DIFF)
                .setThisId("id-4")
                .setDiffHunks(hunks4)
                .build();
        FileReviewDisplay.DiffWithCommitId dc4 = new FileReviewDisplay.DiffWithCommitId(4, diff4);
        diffs.add(dc4);


        FileReviewDisplay frd = FileReviewDisplay.of(path, 0, 4, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();
        List<DiffHunkList.ThreadLineNumberEntry> resultThreads = result.getThreads();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.ADDED, result.getType());
        assertEquals(null, result.getBeforeFileId());
        assertEquals("id-4", result.getAfterFileId());
        assertEquals(1, resultThreads.size());
        assertEquals(35, resultThreads.get(0).lineNum);
    }


    @Test
    public void noChangeInRange() throws Exception {
        String path = "/some/file.txt";
        List<FileReviewDisplay.DiffWithCommitId> diffs = new ArrayList<>();
        List<ReviewsProto.ReviewThread> threads = new ArrayList<>();

        ReviewsProto.FileDiffEntry diff1 = ReviewsProto.FileDiffEntry.newBuilder()
                .setPath(path)
                .setType(ReviewsProto.FileDiffEntry.Type.ADD)
                .setPreviousId("id-0")
                .setThisId("id-1")
                .build();
        FileReviewDisplay.DiffWithCommitId dc1 = new FileReviewDisplay.DiffWithCommitId(1, diff1);
        diffs.add(dc1);

        FileReviewDisplay frd = FileReviewDisplay.of(path, 1, 10, diffs, threads);
        FileReviewDisplay.FileDiffResult result = frd.computeDiff();
        assertEquals(FileReviewDisplay.FileDiffResult.Type.NO_CHANGE, result.getType());
        assertEquals(null, result.getBeforeFileId());
        assertEquals(null, result.getAfterFileId());
    }

}
