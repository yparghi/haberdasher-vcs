package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;
import com.haberdashervcs.common.objects.RepoEntry;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.server.browser.FileBrowser;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdLargeFileStore;


final class HBaseRepoBrowser implements RepoBrowser {

    private static final HdLogger LOG = HdLoggers.create(HBaseRepoBrowser.class);


    static HBaseRepoBrowser forRepo(RepoEntry repo, HBaseRawHelper helper, HdLargeFileStore largeFileStore) {
        return new HBaseRepoBrowser(repo, helper, largeFileStore);
    }


    private final RepoEntry repo;
    private final HBaseRowKeyer rowKeyer;
    private final HBaseRawHelper helper;
    private final HdLargeFileStore largeFileStore;

    private MergeStates mergeStates = null;

    private HBaseRepoBrowser(RepoEntry repo, HBaseRawHelper helper, HdLargeFileStore largeFileStore) {
        this.repo = repo;
        this.rowKeyer = HBaseRowKeyer.forRepo(repo.getOrg(), repo.getRepoName());
        this.helper = helper;
        this.largeFileStore = largeFileStore;
    }


    private synchronized void initializeMergeStates() throws IOException{
        if (mergeStates != null) {
            return;
        }

        final long nowTs = System.currentTimeMillis();
        mergeStates = MergeStates.fromPastSeconds(nowTs, TimeUnit.MINUTES.toSeconds(30), helper, rowKeyer);
    }


    @Override
    public List<BranchEntry> getBranches() {
        return helper.getAllBranches(rowKeyer.getOrg(), rowKeyer.getRepo());
    }


    @Override
    public Optional<BranchEntry> getBranch(String branchName) throws IOException {
        return helper.getBranch(rowKeyer.forBranch(branchName));
    }


    @Override
    public Optional<FolderListing> getFolderAt(String branchName, String path, long commitId) throws IOException {
        initializeMergeStates();
        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, branchName, helper, mergeStates);
        Optional<FolderListing> listingOnBranch = historyLoader.getFolderAtCommit(commitId, path);
        return listingOnBranch;
    }


    @Override
    public Optional<ReviewWithOriginalBytes> getReview(
            String org,
            String repo,
            String thisBranch,
            String otherBranch,
            long otherBranchCommitId)
            throws IOException {
        Preconditions.checkArgument(!thisBranch.equals("main"));
        return helper.getReview(org, repo, thisBranch, otherBranch, otherBranchCommitId);
    }


    @Override
    public void updateReview(
            ReviewsProto.ReviewContents updated, ReviewWithOriginalBytes original)
            throws IOException {
        helper.updateBranchReview(updated, original);
    }


    @Override
    public List<CommitEntry> getLog(String branchName, String path, long atCommitId) {
        CommitLogger logger = CommitLogger.forBranch(branchName, helper, rowKeyer);
        return logger.getLog(HdFolderPath.fromFolderListingFormat(path), atCommitId);
    }


    @Override
    public Optional<FileEntry> getFile(String fileId) throws IOException {
        return helper.getFileMaybe(rowKeyer.forFile(fileId));
    }

    @Override
    public FileBrowser browseFile(FileEntry file) throws IOException {
        return HBaseFileBrowser.forFile(file, largeFileStore, rowKeyer, helper);
    }
}
