package com.haberdashervcs.client.localdb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.objects.LocalRepoState;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;


public interface LocalDb {

    void init(BranchEntry mainFromServer) throws Exception;

    // NOTE: Would it be simpler to return some kind of transaction token, and pass that into different calls? Or
    //     create a db instance bound to a transaction?
    void startTransaction() throws Exception;
    void finishTransaction() throws Exception;
    void cancelTransaction() throws Exception;

    LocalBranchState getCurrentBranch();
    void switchToBranch(String branchName);
    void createNewBranch(String branchName, LocalBranchState initialState);
    Optional<LocalBranchState> getBranchState(String branchName);
    void updateBranchState(String branchName, LocalBranchState newState);

    LocalRepoState getRepoState();
    void updateRepoState(LocalRepoState newState);

    Optional<FolderListing> findFolderAt(String branchName, String path, long commitId);

    List<FolderListing> getListingsSinceCommit(
            String branchName, String path, long commitIdExclusive);

    void putFolder(FolderListing folder);


    ///// Commits
    CommitEntry getCommit(String key);
    void putCommit(String key, CommitEntry commit);
    List<CommitEntry> getCommitsSince(String branchName, long commitIdExclusive);


    ///// Files
    // TODO: Consolidate all these get and put calls.
    FileEntryWithPatchedContents getFile(String key) throws IOException;
    Optional<FileEntryWithRawContents> getFileWithRawContents(String key) throws IOException;
    List<String> fileIdsClientHasFrom(List<String> fileIdsToCheck);

    /**
     * Returns the hash of the file.
     */
    String putNewFile(Path localPath) throws IOException;
    void putFileHandlingDiffEntries(
            String localFileHash, Path localFilePath, FileEntry commitEntry) throws IOException;
    void putFileEntry(FileEntry entry) throws IOException;
    void putLargeFileContents(String fileId, InputStream contents) throws IOException;

    /**
     * Returns a list of all file entries from the given file id down to its first full base entry, following diffs.
     */
    List<String> getAllDiffEntryIds(String fileId);


    List<FolderListing> getAllBranchHeads(String branchName);

    // TODO: Is this redundant with findFolderAt() ?
    Optional<FolderListing> getMostRecentListingForPath(
            long maxCommitId, String branchName, String path);

    CheckoutPathSet getGlobalCheckedOutPaths();
    void addGlobalCheckedOutPath(String path);
}
