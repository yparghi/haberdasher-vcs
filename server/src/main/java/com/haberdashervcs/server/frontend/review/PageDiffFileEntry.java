package com.haberdashervcs.server.frontend.review;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.DiffHunkList;
import com.haberdashervcs.common.diff.LineDiff;
import com.haberdashervcs.common.diff.git.RabTextSequence;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.server.browser.RepoBrowser;


public final class PageDiffFileEntry {

    private static final int MAX_FILE_VIEWING_SIZE_BYTES = 16 * 1024;


    private final ReviewsProto.ReviewContents review;
    private final RepoBrowser browser;
    private final FileReviewDisplay fileReview;
    private final HdUserStore userStore;
    private final Map<String, String> authorUserIdToEmail;

    PageDiffFileEntry(
            ReviewsProto.ReviewContents review,
            RepoBrowser browser,
            FileReviewDisplay fileReview,
            HdUserStore userStore,
            Map<String, String> authorUserIdToEmail) {
        this.review = review;
        this.browser = browser;
        this.fileReview = fileReview;
        this.userStore = userStore;
        this.authorUserIdToEmail = authorUserIdToEmail;
    }

    public String getFilePath() {
        return fileReview.getPath();
    }


    public List<PageDiffHunkEntry> getDiffHunkEntries() throws IOException {
        FileReviewDisplay.FileDiffResult diff = fileReview.computeDiff();

        if (diff.getType() == FileReviewDisplay.FileDiffResult.Type.DELETED) {
            LineDiff lineDiff = new LineDiff(
                    LineDiff.Type.MINUS, -1, -1, "(Deleted)");
            return ImmutableList.of(new PageDiffHunkEntry(ImmutableList.of(lineDiff), Collections.emptyList()));

        } else if (diff.getType() == FileReviewDisplay.FileDiffResult.Type.ADDED_THEN_DELETED) {
            LineDiff lineDiff = new LineDiff(
                    LineDiff.Type.MINUS, -1, -1, "(Added then deleted)");
            return ImmutableList.of(new PageDiffHunkEntry(ImmutableList.of(lineDiff), Collections.emptyList()));

        } else if (diff.getType() == FileReviewDisplay.FileDiffResult.Type.NO_CHANGE) {
            LineDiff lineDiff = new LineDiff(
                    LineDiff.Type.SAME, -1, -1, "(No change)");
            return ImmutableList.of(new PageDiffHunkEntry(ImmutableList.of(lineDiff), Collections.emptyList()));

        } else if (diff.isBinaryDiff()) {
            String msg;
            if (diff.getType() == FileReviewDisplay.FileDiffResult.Type.ADDED) {
                msg = String.format("Binary file: %d bytes", diff.getBinaryBytesAfter());
            } else {
                msg = String.format(
                        "Binary file: %d -> %d bytes", diff.getBinaryBytesBefore(), diff.getBinaryBytesAfter());
            }
            LineDiff lineDiff = new LineDiff(
                    LineDiff.Type.PLUS, -1, -1, msg);
            return ImmutableList.of(new PageDiffHunkEntry(ImmutableList.of(lineDiff), Collections.emptyList()));
        }


        if (diff.getType() == FileReviewDisplay.FileDiffResult.Type.ADDED) {
            return ImmutableList.of(displayWholeTextFile(diff));
        } else {
            return diffToHunks(diff);
        }
    }


    private List<PageDiffHunkEntry> diffToHunks(FileReviewDisplay.FileDiffResult diff) throws IOException {
        Preconditions.checkArgument(diff.getType() == FileReviewDisplay.FileDiffResult.Type.DIFF);
        Preconditions.checkNotNull(diff.getBeforeFileId());
        Preconditions.checkNotNull(diff.getAfterFileId());

        FileEntry beforeEntry = browser.getFile(diff.getBeforeFileId()).get();
        RandomAccessBytes beforeContents = browser.browseFile(beforeEntry).getWholeContents();
        RabTextSequence beforeText = new RabTextSequence(beforeContents);
        FileEntry afterEntry = browser.getFile(diff.getAfterFileId()).get();
        RandomAccessBytes afterContents = browser.browseFile(afterEntry).getWholeContents();
        RabTextSequence afterText = new RabTextSequence(afterContents);

        List<PageDiffHunkEntry> out = new ArrayList<>();
        ThreadHunkMerger merger = new ThreadHunkMerger(diff.getHunks(), diff.getThreads());
        List<DiffHunk> hunksWithThreads = merger.merge();

        for (DiffHunk hunk : hunksWithThreads) {
            List<LineDiff> lineDiffs = new ArrayList<>();

            if (hunk.forThreads != null) {
                int lineNum = hunk.forThreads.get(0).lineNum;
                String lineStr = afterText.getLine(lineNum);
                lineDiffs.add(new LineDiff(LineDiff.Type.SAME, -1, lineNum, lineStr));
                out.add(new PageDiffHunkEntry(lineDiffs, hunk.forThreads));
                continue;
            }

            for (int oLineNum = hunk.originalStart; oLineNum < hunk.originalEnd; ++oLineNum) {
                String lineStr = beforeText.getLine(oLineNum);
                lineDiffs.add(new LineDiff(LineDiff.Type.MINUS, oLineNum, -1, lineStr));
            }

            for (int mLineNum = hunk.modifiedStart; mLineNum < hunk.modifiedEnd; ++mLineNum) {
                String lineStr = afterText.getLine(mLineNum);
                lineDiffs.add(new LineDiff(LineDiff.Type.PLUS, -1, mLineNum, lineStr));
            }

            out.add(new PageDiffHunkEntry(lineDiffs, diff.getThreads()));
        }

        return out;
    }


    // We compute line maps on the fly, assuming text files in the repo aren't too large and reviews are occasional.
    // We may need to revisit that assumption one day.
    private PageDiffHunkEntry displayWholeTextFile(FileReviewDisplay.FileDiffResult diff) throws IOException {
        Preconditions.checkNotNull(diff.getAfterFileId());

        FileEntry fileEntry = browser.getFile(diff.getAfterFileId()).get();
        RandomAccessBytes contents = browser.browseFile(fileEntry).getWholeContents();

        // TODO: Cutting it off at a byte number probably causes a unicode error.
        if (contents.length() > MAX_FILE_VIEWING_SIZE_BYTES) {
            byte[] sample = new byte[MAX_FILE_VIEWING_SIZE_BYTES];
            for (int i = 0; i < MAX_FILE_VIEWING_SIZE_BYTES; ++i) {
                // TODO: Use readInto().
                sample[i] = contents.at(i);
            }
            contents = ByteArrayRandomAccessBytes.of(sample);
        }
        RabTextSequence text = new RabTextSequence(contents);

        List<LineDiff> lines = new ArrayList<>();
        int lineNumber = 1;
        while (lineNumber <= text.size()) {
            if (text.getLineStartingByte(lineNumber) > MAX_FILE_VIEWING_SIZE_BYTES) {
                break;
            }
            String thisLine = text.getLine(lineNumber);
            lines.add(new LineDiff(LineDiff.Type.PLUS, -1, lineNumber, thisLine));
            ++lineNumber;
        }

       return new PageDiffHunkEntry(lines, diff.getThreads());
    }


    public final class PageDiffHunkEntry {

        private final List<PageDiffLineEntry> lines;
        private final Map<Integer, List<DiffHunkList.ThreadLineNumberEntry>> lineNumberToThreads;

        private PageDiffHunkEntry(List<LineDiff> lineDiffs, List<DiffHunkList.ThreadLineNumberEntry> threads) {
            this.lines = new ArrayList<>();

            this.lineNumberToThreads = new HashMap<>();
            for (DiffHunkList.ThreadLineNumberEntry thread : threads) {
                int lineNum = thread.lineNum;
                if (!lineNumberToThreads.containsKey(lineNum)) {
                    lineNumberToThreads.put(lineNum, new ArrayList<>());
                }
                lineNumberToThreads.get(lineNum).add(thread);
            }

            int highestOld = 0, highestNew = 0;
            // TODO: There's got to be a better way.
            for (LineDiff diff : lineDiffs) {
                if (diff.getLineNumberOld() > highestOld) {
                    highestOld = diff.getLineNumberOld();
                }

                if (diff.getLineNumberNew() > highestNew) {
                    highestNew = diff.getLineNumberNew();
                }
            }

            int maxNumDigitsOld = String.valueOf(highestOld).length();
            int maxNumDigitsNew = String.valueOf(highestNew).length();
            for (LineDiff lineDiff : lineDiffs) {
                List<DiffHunkList.ThreadLineNumberEntry> threadsThisLine;
                if (lineNumberToThreads.containsKey(lineDiff.getLineNumberNew())) {
                    threadsThisLine = lineNumberToThreads.get(lineDiff.getLineNumberNew());
                } else {
                    threadsThisLine = Collections.emptyList();
                }
                lines.add(new PageDiffLineEntry(lineDiff, maxNumDigitsOld, maxNumDigitsNew, threadsThisLine));
            }
        }

        public List<PageDiffLineEntry> getLineEntries() {
            return lines;
        }
    }


    public final class PageDiffLineEntry {

        private final LineDiff lineDiff;
        private final int maxDigitsOld;
        private final int maxDigitsNew;
        private final String lineNumberFormatOld;
        private final String lineNumberFormatNew;
        private final List<DiffHunkList.ThreadLineNumberEntry> threadsThisLine;

        private PageDiffLineEntry(
                LineDiff lineDiff,
                int maxDigitsOld,
                int maxDigitsNew,
                List<DiffHunkList.ThreadLineNumberEntry> threadsThisLine) {
            this.lineDiff = lineDiff;
            this.maxDigitsOld = maxDigitsOld;
            this.maxDigitsNew = maxDigitsNew;
            this.lineNumberFormatOld = "%" + maxDigitsOld + "d";
            this.lineNumberFormatNew = "%" + maxDigitsNew + "d";
            this.threadsThisLine = threadsThisLine;
        }

        public String getType() {
            return lineDiff.getType().toString();
        }

        public String getLineNumberOld() {
            if (lineDiff.getLineNumberOld() < 0) {
                return Strings.repeat(" ", maxDigitsOld);
            } else {
                return String.format(lineNumberFormatOld, lineDiff.getLineNumberOld());
            }
        }

        public String getLineNumberNew() {
            if (lineDiff.getLineNumberNew() < 0) {
                return Strings.repeat(" ", maxDigitsNew);
            } else {
                return String.format(lineNumberFormatNew, lineDiff.getLineNumberNew());
            }
        }

        public String getLineText() {
            return lineDiff.getText();
        }


        public List<ReviewThreadView> getReviewThreads() {
            List<ReviewThreadView> out = new ArrayList<>();
            for (DiffHunkList.ThreadLineNumberEntry thread : threadsThisLine) {
                ReviewsProto.ReviewThread threadProto = getReviewThreadProtoForId(thread.threadId);
                out.add(new ReviewThreadView(threadProto));
            }
            return out;
        }
    }


    public final class ReviewThreadView {

        private final ReviewsProto.ReviewThread thread;

        private ReviewThreadView(ReviewsProto.ReviewThread thread) {
            this.thread = thread;
        }

        public List<ReviewCommentView> getComments() {
            List<ReviewCommentView> result = this.thread.getCommentsList().stream()
                    .map(comment -> new ReviewCommentView(comment))
                    .collect(Collectors.toList());
            return result;
        }

        public String getId() {
            return thread.getId();
        }

        public String getState() {
            if (thread.getState() == ReviewsProto.ReviewThread.State.RESOLVED) {
                return "RESOLVED";
            } else {
                return "ACTIVE";
            }
        }

        public String getPreviewText() {
            String text = this.thread.getComments(0).getText();
            if (text.length() > 20) {
                return text.substring(0, 20) + "...";
            } else {
                return text;
            }
        }
    }


    public final class ReviewCommentView {

        private ReviewsProto.ReviewComment comment;

        private ReviewCommentView(ReviewsProto.ReviewComment comment) {
            this.comment = comment;
        }

        public String getText() {
            return comment.getText();
        }

        public String getAuthor() throws IOException {
            return authorEmailForUserId(comment.getUserId());
        }
    }


    private String authorEmailForUserId(String userId) throws IOException {
        if (authorUserIdToEmail.containsKey(userId)) {
            return authorUserIdToEmail.get(userId);
        }

        String email = userStore.getUserById(userId).get().getEmail();
        authorUserIdToEmail.put(userId, email);
        return email;
    }


    // TODO: Maybe ReviewDisplay or FileReviewDisplay should handle this.
    private ReviewsProto.ReviewThread getReviewThreadProtoForId(String threadId) {
        for (ReviewsProto.ReviewThread thread : this.review.getThreadsList()) {
            if (thread.getId().equals(threadId)) {
                return thread;
            }
        }
        throw new NoSuchElementException("Thread id not found: " + threadId);
    }

}
