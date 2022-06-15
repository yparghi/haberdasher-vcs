package com.haberdashervcs.client.checkout;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.LocalDbRowKeyer;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDbRowKeyer;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchAndCommit;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;


/**
 * Tracks the state of locally checked out paths and branches. Used to sync to different commits forwards, backwards,
 * or laterally in the revision history.
 */
public final class CheckoutStateManager {

    private static final HdLogger LOG = HdLoggers.create(CheckoutStateManager.class);


    public static CheckoutStateManager of(RepoConfig config, LocalDb db, ServerTalker serverTalker) {
        return new CheckoutStateManager(config, db, serverTalker);
    }


    private final RepoConfig config;
    private final LocalDb db;
    private final ServerTalker serverTalker;
    private final LocalDbRowKeyer rowKeyer;

    private CheckoutStateManager(RepoConfig config, LocalDb db, ServerTalker serverTalker) {
        this.config = config;
        this.db = db;
        this.serverTalker = serverTalker;
        this.rowKeyer = SqliteLocalDbRowKeyer.getInstance();
    }


    // TODO: Break out separate methods for sync operations, and toss this enum.
    public enum SyncType {
        DOWNLOAD_ONLY,
        DOWNLOAD_AND_SYNC_LOCAL
    }


    // TODO: Bail out if there are local uncommitted changes?
    public void sync(String branchName, long commitIdToSyncTo, SyncType syncType) throws Exception {
        Preconditions.checkArgument(commitIdToSyncTo >= 0);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(branchName));

        final LocalBranchState branchStateToSyncTo;
        Optional<LocalBranchState> localBranchMaybe = db.getBranchState(branchName);

        // TODO: Can all this (coordinating local branch state w/ server branch state) be simplified?
        if (localBranchMaybe.isPresent() && localBranchMaybe.get().getHeadCommitId() >= commitIdToSyncTo) {
            branchStateToSyncTo = localBranchMaybe.get();

        } else {
            LOG.info("Getting branch %s from the server...", branchName);

            Optional<BranchEntry> branchFromServer = serverTalker.getBranch(branchName);
            if (!branchFromServer.isPresent()
                    || branchFromServer.get().getHeadCommitId() < commitIdToSyncTo) {
                throw new IllegalStateException(String.format(
                        "%s:%d not found locally or on the server.", branchName, commitIdToSyncTo));
            }

            if (localBranchMaybe.isPresent()
                    // The base commit on main is irrelevant, both remote and locally. It's just a placeholder value.
                    && !localBranchMaybe.get().getBranchName().equals("main")
                    && branchFromServer.get().getBaseCommitId() != localBranchMaybe.get().getBaseCommitId()) {
                throw new IllegalStateException(String.format(
                        "Unexpected: Server base commit (%d) and local base commit (%d) differ!",
                        branchFromServer.get().getBaseCommitId(), localBranchMaybe.get().getBaseCommitId()));
            }

            LocalBranchState stateFromServer = LocalBranchState.of(
                    branchFromServer.get().getName(),
                    branchFromServer.get().getBaseCommitId(),
                    branchFromServer.get().getHeadCommitId(),
                    commitIdToSyncTo);
            if (localBranchMaybe.isPresent()) {
                db.updateBranchState(branchFromServer.get().getName(), stateFromServer);
            } else {
                db.createNewBranch(branchFromServer.get().getName(), stateFromServer);
            }
            branchStateToSyncTo = db.getBranchState(branchFromServer.get().getName()).get();
        }


        CheckoutPathSet globalCheckedOutPaths = db.getGlobalCheckedOutPaths();
        ServerCheckoutSpec serverSpec = serverTalker.queryForCheckout(
                branchStateToSyncTo.getBranchName(),
                commitIdToSyncTo,
                globalCheckedOutPaths);


        List<String> fileIdsServerWantsToSend = serverSpec.getAllFileIdsFromServer();
        Set<String> fileIdsClientNeeds = new HashSet<>();
        List<String> fileIdsClientAlreadyHas = db.fileIdsClientHasFrom(fileIdsServerWantsToSend);
        for (String fileIdFromServer : fileIdsServerWantsToSend) {
            if (!fileIdsClientAlreadyHas.contains(fileIdFromServer)) {
                fileIdsClientNeeds.add(fileIdFromServer);
            }
        }
        ClientCheckoutSpec clientSpec = ClientCheckoutSpec.withFilesClientNeeds(fileIdsClientNeeds);

        serverTalker.checkout(branchName, globalCheckedOutPaths, commitIdToSyncTo, db, clientSpec);


        if (syncType == SyncType.DOWNLOAD_ONLY) {
            return;
        }

        final long newHeadCommitId = Math.max(branchStateToSyncTo.getHeadCommitId(), commitIdToSyncTo);
        LocalBranchState newBranchState = LocalBranchState.of(
                branchStateToSyncTo.getBranchName(),
                branchStateToSyncTo.getBaseCommitId(),
                newHeadCommitId,
                commitIdToSyncTo);

        db.updateBranchState(branchName, newBranchState);

        db.switchToBranch(branchStateToSyncTo.getBranchName());
        LocalChangeCrawler crawler = new LocalChangeCrawler(
                config,
                db,
                BranchAndCommit.of(branchName, commitIdToSyncTo),
                globalCheckedOutPaths,
                new SyncToCommitChangeHandler(config, db));
        crawler.crawl();
    }


    public void addCheckoutPathToRepo(String pathToAdd) {
        LOG.info("Adding checkout path: %s", pathToAdd);
        CheckoutPathSet currentlyCheckedOut = db.getGlobalCheckedOutPaths();
        List<String> asList = currentlyCheckedOut.toList();
        LOG.debug("Got global paths: '%s'", String.join(",", asList));
        for (String currentlyCheckedOutPath : asList) {
            if (pathToAdd.startsWith(currentlyCheckedOutPath)) {
                throw new IllegalStateException(
                        "A parent path (" + currentlyCheckedOutPath + ") is already checked out.");
            }
        }

        db.addGlobalCheckedOutPath(pathToAdd);

        LOG.info("\nYour checked out paths are:");
        List<String> updatedList = db.getGlobalCheckedOutPaths().toList();
        for (String path : updatedList) {
            LOG.info("- %s", path);
        }
    }

}
