package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.io.HdObjectOutputStream;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.CheckoutContentsHandler;
import com.haberdashervcs.server.datastore.HdLargeFileStore;
import com.haberdashervcs.server.operations.checkout.CheckoutResult;


// TODO: Consider an optimization that reuses the query to avoid crawling twice. (This might also prevent some race
// condition w/ the merge states.)
//
// Example:
// - The checkout query returns both file ids and full folder listings. The client writes folders to its db.
// - The second checkout call skips the crawl and just writes out file entries from the given ids that the client needs.
final class HBaseCheckoutHandler {

    private static final HdLogger LOG = HdLoggers.create(HBaseCheckoutHandler.class);


    private final String org;
    private final String repo;
    private final HBaseDatastore datastore;
    private final HBaseRawHelper helper;
    private final HdLargeFileStore largeFileStore;
    private final CheckoutPathSet paths;

    HBaseCheckoutHandler(
            String org,
            String repo,
            HBaseDatastore datastore,
            HBaseRawHelper helper,
            HdLargeFileStore largeFileStore,
            CheckoutPathSet paths) {
        this.org = org;
        this.repo = repo;
        this.datastore = datastore;
        this.helper = helper;
        this.largeFileStore = largeFileStore;
        this.paths = paths;
    }


    ServerCheckoutSpec computeCheckout(
            String org, String repo, String branchName, long commitId)
            throws IOException {
        Set<String> fileIds = new HashSet<>();
        RepoBrowser browser = datastore.getBrowser(org, repo).get();

        CheckoutContentsHandler handler = new CheckoutContentsHandler() {
            @Override public void sendFolder(FolderListing folder) throws IOException {
                // No op, we just want to know what file ids are in the checkout.
            }

            @Override public void sendFile(String fileId) throws IOException {
                String diffBaseFileId = fileId;
                while (true) {
                    fileIds.add(diffBaseFileId);
                    FileEntry entry = browser.getFile(diffBaseFileId).get();
                    if (entry.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                        diffBaseFileId = entry.getBaseEntryId().get();
                    } else {
                        break;
                    }
                }
            }
        };

        doCheckoutCrawl(org, repo, branchName, commitId, paths, handler);
        return ServerCheckoutSpec.withServerFileIds(fileIds);
    }


    CheckoutResult doCheckout(
            String org,
            String repo,
            String branchName,
            long commitId,
            HdObjectOutputStream objectsOut,
            ClientCheckoutSpec clientSpec)
            throws IOException {
        Set<String> fileIdsFound = new HashSet<>();
        CheckoutContentsHandler handler = new CheckoutContentsHandler() {
            @Override public void sendFolder(FolderListing folder) throws IOException {
                objectsOut.writeFolder("TODO toss folder ids", folder);
            }

            @Override public void sendFile(String fileId) throws IOException {
                fileIdsFound.add(fileId);
            }
        };

        doCheckoutCrawl(org, repo, branchName, commitId, paths, handler);

        RepoBrowser browser = datastore.getBrowser(org, repo).get();
        Set<String> fileIdsClientNeeds = clientSpec.getFileIdsClientNeeds();
        actuallySendFileEntries(fileIdsFound, fileIdsClientNeeds, browser, objectsOut);
        return CheckoutResult.ok();
    }


    // TODO: Improve this, it's partly pointless. See the note above about not having to crawl twice.
    private void actuallySendFileEntries(
            Set<String> fileIdsFound,
            Set<String> fileIdsClientNeeds,
            RepoBrowser browser,
            HdObjectOutputStream objectsOut)
            throws IOException {

        for (String fileIdToSend : fileIdsClientNeeds) {
            FileEntry entry = browser.getFile(fileIdToSend).get();

            if (entry.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE) {
                HdLargeFileStore largeFileStore = datastore.getLargeFileStore();
                HdLargeFileStore.FileWithSize largeFile = largeFileStore.getFileById(
                        org, repo, entry.getId());
                objectsOut.writeLargeFileContents(
                        entry,
                        largeFile.getContents(),
                        Math.toIntExact(largeFile.getSizeInBytes()));
            } else {
                objectsOut.writeFile(fileIdToSend, entry);
            }
        }
    }


    // For tracking built-up paths like "/some/dir/in/the/tree/<filename goes here>"
    private static class CheckoutCrawlEntry {

        private String path;
        private FolderListing listing;

        public CheckoutCrawlEntry(String path, FolderListing listing) {
            this.path = path;
            this.listing = listing;
        }
    }


    // TODO! tests
    private void doCheckoutCrawl(
            String org,
            String repo,
            String branchName,
            long commitId,
            CheckoutPathSet paths,
            CheckoutContentsHandler handler)
            throws IOException {
        Preconditions.checkArgument(!paths.isEmpty(), "Empty set of checkout paths");

        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        // TODO: Make sure all folder browsing uses MergeStates correctly. I think HBaseRawHelper needs to be polished
        //     into a clean, correct browsing API.
        final long nowTs = System.currentTimeMillis();
        MergeStates mergeStates = MergeStates.fromPastSeconds(nowTs, TimeUnit.MINUTES.toSeconds(30), helper, rowKeyer);
        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, branchName, helper, mergeStates);

        LinkedList<CheckoutCrawlEntry> foldersToCrawl = new LinkedList<>();
        for (String path : paths.toList()) {
            Optional<FolderListing> headListing = historyLoader.getFolderAtCommit(commitId, path);
            if (!headListing.isPresent()) {
                throw new IllegalStateException(String.format(
                        "No such folder found on the server: %s on branch %s:%d", path, branchName, commitId));
            }
            handler.sendFolder(headListing.get());
            foldersToCrawl.add(new CheckoutCrawlEntry(path, headListing.get()));
        }

        while (!foldersToCrawl.isEmpty()) {
            CheckoutCrawlEntry thisCrawlEntry = foldersToCrawl.pop();

            for (FolderListing.Entry entryInFolder : thisCrawlEntry.listing.getEntries()) {
                if (entryInFolder.getType() == FolderListing.Entry.Type.FOLDER) {
                    String subfolderPath = thisCrawlEntry.path + entryInFolder.getName() + "/";
                    Optional<FolderListing> thisEntryFolderListingOpt = historyLoader.getFolderAtCommit(
                            commitId, subfolderPath);
                    if (thisEntryFolderListingOpt.isEmpty()) {
                        // There was some bug in client-server transmission or storage of folders. To fail gracefully,
                        // just skip this folder.
                        LOG.warn(
                                "BUG: No folder %s found on branch %s at commit %d",
                                subfolderPath, branchName, commitId);
                        continue;
                    }
                    FolderListing thisEntryFolderListing = thisEntryFolderListingOpt.get();
                    handler.sendFolder(thisEntryFolderListing);
                    // BFS to stay within a folder, for row locality.
                    // TODO: Test this vs. DFS. Because of prefixing, maybe DFS is faster.
                    foldersToCrawl.add(
                            new CheckoutCrawlEntry(subfolderPath, thisEntryFolderListing));

                } else {
                    handler.sendFile(entryInFolder.getId());
                }
            }
        }
    }

}
