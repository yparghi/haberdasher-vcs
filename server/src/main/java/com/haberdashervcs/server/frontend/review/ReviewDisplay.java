package com.haberdashervcs.server.frontend.review;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.haberdashervcs.common.protobuf.ReviewsProto;


final class ReviewDisplay {

    static ReviewDisplay forReview(
            ReviewsProto.ReviewContents review,
            long baseCommitId,
            long headCommitId) {
        return new ReviewDisplay(review, baseCommitId, headCommitId);
    }


    private final ReviewsProto.ReviewContents review;
    private final long baseCommitId;
    private final long headCommitId;

    ReviewDisplay(ReviewsProto.ReviewContents review, long baseCommitId, long headCommitId) {
        this.review = review;
        this.baseCommitId = baseCommitId;
        this.headCommitId = headCommitId;
    }


    List<FileReviewDisplay> byFile() {

        Map<String, List<FileReviewDisplay.DiffWithCommitId>> diffsByPath = new HashMap<>();
        for (ReviewsProto.CommitDiff commitDiff : review.getCommitDiffsList()) {
            for (ReviewsProto.FileDiffEntry diffEntry : commitDiff.getFileDiffsList()) {
                FileReviewDisplay.DiffWithCommitId dc = new FileReviewDisplay.DiffWithCommitId(
                        commitDiff.getCommitId(), diffEntry);
                String path = diffEntry.getPath();
                if (!diffsByPath.containsKey(path)) {
                    diffsByPath.put(path, new ArrayList<>());
                }
                diffsByPath.get(path).add(dc);
            }
        }

        Map<String, List<ReviewsProto.ReviewThread>> threadsByPath = new HashMap<>();
        for (ReviewsProto.ReviewThread thread : review.getThreadsList()) {
            String path = thread.getFilePath();
            if (!threadsByPath.containsKey(path)) {
                threadsByPath.put(path, new ArrayList<>());
            }
            threadsByPath.get(path).add(thread);
        }

        List<FileReviewDisplay> out = new ArrayList<>();
        for (Map.Entry<String, List<FileReviewDisplay.DiffWithCommitId>> entry : diffsByPath.entrySet()) {
            String path = entry.getKey();
            List<FileReviewDisplay.DiffWithCommitId> diffs = entry.getValue();
            List<ReviewsProto.ReviewThread> threads;
            if (threadsByPath.containsKey(path)) {
                threads = threadsByPath.get(path);
            } else {
                threads = Collections.emptyList();
            }
            out.add(FileReviewDisplay.of(path, baseCommitId, headCommitId, diffs, threads));
        }

        return out;
    }

}
