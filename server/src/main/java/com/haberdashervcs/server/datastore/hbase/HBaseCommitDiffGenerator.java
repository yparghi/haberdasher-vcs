package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.TextOrBinaryChecker;
import com.haberdashervcs.common.diff.git.HistogramDiffer;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.server.browser.FileBrowser;
import com.haberdashervcs.server.datastore.HdLargeFileStore;


/**
 * Takes the changes in a CommitEntry and generates the corresponding review/diff contents.
 */
final class HBaseCommitDiffGenerator {


    static HBaseCommitDiffGenerator of(
            String branchName, HBaseRawHelper helper, HBaseRowKeyer rowKeyer, HdLargeFileStore largeFileStore) {
        return new HBaseCommitDiffGenerator(branchName, helper, rowKeyer, largeFileStore);
    }


    private final String branchName;
    private final HBaseRawHelper helper;
    private final HBaseRowKeyer rowKeyer;
    private final HdLargeFileStore largeFileStore;

    private HBaseCommitDiffGenerator(
            String branchName, HBaseRawHelper helper, HBaseRowKeyer rowKeyer, HdLargeFileStore largeFileStore) {
        this.branchName = branchName;
        this.helper = helper;
        this.rowKeyer = rowKeyer;
        this.largeFileStore = largeFileStore;
    }


    List<ReviewsProto.CommitDiff> diff(Collection<CommitEntry> entriesThisPush) throws IOException {
        List<ReviewsProto.CommitDiff> out = new ArrayList<>();
        List<CommitEntry> eachCommitById = ImmutableList.sortedCopyOf(
                Comparator.comparingLong(CommitEntry::getCommitId), entriesThisPush);

        for (CommitEntry commit : eachCommitById) {
            out.add(diffOneCommit(commit));
        }
        return out;
    }


    private ReviewsProto.CommitDiff diffOneCommit(CommitEntry commit) throws IOException {
        ReviewsProto.CommitDiff.Builder out = ReviewsProto.CommitDiff.newBuilder()
                .setCommitId(commit.getCommitId());

        for (CommitEntry.CommitChangedPath changedPath : commit.getChangedPaths()) {
            ReviewsProto.FileDiffEntry.Builder thisDiffEntry = ReviewsProto.FileDiffEntry.newBuilder()
                    .setPath(changedPath.getPath());
            if (changedPath.getPreviousId().isPresent()) {
                thisDiffEntry.setPreviousId(changedPath.getPreviousId().get());
            }
            if (changedPath.getThisId().isPresent()) {
                thisDiffEntry.setThisId(changedPath.getThisId().get());
            }
            switch (changedPath.getChangeType()) {
                case ADD:
                    thisDiffEntry.setType(ReviewsProto.FileDiffEntry.Type.ADD);
                    break;
                case DELETE:
                    thisDiffEntry.setType(ReviewsProto.FileDiffEntry.Type.DELETE);
                    break;
                case DIFF:
                    thisDiffEntry.setType(ReviewsProto.FileDiffEntry.Type.DIFF);
                    ReviewsProto.FileDiffHunks diffHunks = diffFile(commit.getCommitId(), changedPath);
                    thisDiffEntry.setDiffHunks(diffHunks);
                    break;
                default:
                    throw new IllegalStateException("Unknown change type: " + changedPath.getChangeType());
            }
            out.addFileDiffs(thisDiffEntry.build());
        }

        return out.build();
    }


    private ReviewsProto.FileDiffHunks diffFile(
            long commitId, CommitEntry.CommitChangedPath changedPath) throws IOException {
        Preconditions.checkArgument(changedPath.getChangeType() == CommitEntry.CommitChangedPath.ChangeType.DIFF);
        ReviewsProto.FileDiffHunks.Builder out = ReviewsProto.FileDiffHunks.newBuilder();

        FileEntry previousFile = helper.getFile(rowKeyer.forFile(changedPath.getPreviousId().get()));
        FileEntry thisFile = helper.getFile(rowKeyer.forFile(changedPath.getThisId().get()));
        FileBrowser previousFileBrowser = HBaseFileBrowser.forFile(previousFile, largeFileStore, rowKeyer, helper);
        FileBrowser thisFileBrowser = HBaseFileBrowser.forFile(thisFile, largeFileStore, rowKeyer, helper);
        RandomAccessBytes previousFileContents = previousFileBrowser.getWholeContents();
        RandomAccessBytes thisFileContents = thisFileBrowser.getWholeContents();
        TextOrBinaryChecker.TextOrBinaryResult previousBinaryCheck = TextOrBinaryChecker.check(previousFileContents);
        TextOrBinaryChecker.TextOrBinaryResult thisBinaryCheck = TextOrBinaryChecker.check(thisFileContents);

        if (previousBinaryCheck.isBinary() && thisBinaryCheck.isBinary()) {
            ReviewsProto.BinaryDiff bDiff = ReviewsProto.BinaryDiff.newBuilder()
                    .setNumBytesOriginal(previousFileContents.length())
                    .setNumBytesModified(thisFileContents.length())
                    .build();
            out.setBinaryDiff(bDiff);

        } else if (previousBinaryCheck.isBinary() && thisBinaryCheck.isText()) {
            ReviewsProto.BinaryDiff bDiff = ReviewsProto.BinaryDiff.newBuilder()
                    .setNumBytesOriginal(previousFileContents.length())
                    .setNumBytesModified(-1)
                    .build();
            out.setBinaryDiff(bDiff);

        } else if (previousBinaryCheck.isText() && thisBinaryCheck.isBinary()) {
            ReviewsProto.BinaryDiff bDiff = ReviewsProto.BinaryDiff.newBuilder()
                    .setNumBytesOriginal(-1)
                    .setNumBytesModified(thisFileContents.length())
                    .build();
            out.setBinaryDiff(bDiff);

        } else {
            List<DiffHunk> diffHunks = new HistogramDiffer().computeLineDiffs(
                    previousFileContents, thisFileContents);
            List<ReviewsProto.TextDiffHunk> protoHunks = new ArrayList<>();
            for (DiffHunk hunk : diffHunks) {
                ReviewsProto.TextDiffHunk pHunk = ReviewsProto.TextDiffHunk.newBuilder()
                        .setOriginalFromLine(hunk.originalStart)
                        .setOriginalToLine(hunk.originalEnd)
                        .setModifiedFromLine(hunk.modifiedStart)
                        .setModifiedToLine(hunk.modifiedEnd)
                        .build();
                protoHunks.add(pHunk);
            }
            out.addAllTextDiffHunks(protoHunks);
        }

        return out.build();
    }

}
