package com.haberdashervcs.server.browser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.protobuf.ReviewsProto;


/**
 * Provides info on the contents of a repo, e.g. for viewing it in a web browser.
 */
public interface RepoBrowser {

    // TODO: Limits? A configurable query object?
    List<BranchEntry> getBranches();

    Optional<BranchEntry> getBranch(String branchName) throws IOException;

    // Will fall back to main if a folder entry doesn't exist directly on the branch.
    Optional<FolderListing> getFolderAt(String branchName, String path, long commitId) throws IOException;



    /////// Reviews

    class ReviewWithOriginalBytes {
        private final ReviewsProto.ReviewContents review;
        private final byte[] originalBytes;

        public ReviewWithOriginalBytes(ReviewsProto.ReviewContents review, byte[] originalBytes) {
            this.review = review;
            this.originalBytes = originalBytes;
        }

        public ReviewsProto.ReviewContents getReview() {
            return review;
        }

        public byte[] getOriginalBytes() {
            return Arrays.copyOf(originalBytes, originalBytes.length);
        }
    }

    Optional<ReviewWithOriginalBytes> getReview(
            String org,
            String repo,
            String thisBranch,
            String otherBranch,
            long otherBranchCommitId)
            throws IOException;

    void updateReview(ReviewsProto.ReviewContents updated, ReviewWithOriginalBytes original) throws IOException;



    /////// Log
    List<CommitEntry> getLog(String branchName, String path, long atCommitId);


    /////// Files
    Optional<FileEntry> getFile(String fileId) throws IOException;
    FileBrowser browseFile(FileEntry file) throws IOException;
}
