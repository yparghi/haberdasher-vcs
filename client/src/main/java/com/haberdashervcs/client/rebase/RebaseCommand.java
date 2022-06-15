package com.haberdashervcs.client.rebase;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.checkout.CheckoutStateManager;
import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.commit.CommitCommand;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.objects.LocalRepoState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.diff.TextOrBinaryChecker;
import com.haberdashervcs.common.diff.git.GitTextMerger;
import com.haberdashervcs.common.exceptions.HdNormalError;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.protobuf.CommitsProto;


public final class RebaseCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(RebaseCommand.class);

    private static final String USAGE = ""
            + "Usage:\n"
            + "hd rebase main:N  --  Integrate changes through main:N into this branch\n"
            + "hd rebase commit  --  Finalize and commit a rebase in progress\n"
            + "hd rebase cancel  --  Cancel a rebase in progress and revert to branch head";

    private static final Splitter COLON_SPLITTER = Splitter.on(':');


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;
    private final ServerTalker serverTalker;

    // Labels for merge chunk headers
    private List<String> sequenceNames;

    public RebaseCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = ImmutableList.copyOf(otherArgs);
        this.db = db;
        this.serverTalker = JettyServerTalker.forConfig(config);
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 1) {
            throw new HdNormalError(USAGE);
        }
        String firstArg = otherArgs.get(0);

        // TODO: Some sort of 'hd rebase status' to remind you of the result, same as what's printed out at the end of
        // doRebase(). So that result may need to be saved in the db?
        if (firstArg.equals("commit")) {
            commitRebase();
        } else if (firstArg.equals("cancel")) {
            cancelRebase();
        } else if (firstArg.startsWith("main:")) {
            doRebase(firstArg);
        } else {
            throw new HdNormalError(USAGE);
        }
    }


    private void commitRebase() throws Exception {

        db.startTransaction();
        LocalRepoState repoState = db.getRepoState();
        if (repoState.getState() != LocalRepoState.State.REBASE_IN_PROGRESS) {
            throw new IllegalStateException("No rebase is in progress.");
        }

        String mainCommitStr = repoState.getRebaseCommitBeingIntegrated();
        List<String> parts = COLON_SPLITTER.limit(2).splitToList(mainCommitStr);
        String branch = parts.get(0);
        long commitId = Long.parseLong(parts.get(1));
        CommitsProto.BranchIntegration integration = CommitsProto.BranchIntegration.newBuilder()
                .setBranch(branch)
                .setCommitId(commitId)
                .build();

        CommitCommand commitCommand = CommitCommand.forRebaseIntegration(
                config, db, "Rebase/integration of " + mainCommitStr, integration);
        commitCommand.perform();

        LocalRepoState newState = LocalRepoState.normal();
        db.updateRepoState(newState);
        db.finishTransaction();
    }


    private void cancelRebase() throws Exception {

        db.startTransaction();
        LocalRepoState repoState = db.getRepoState();
        if (repoState.getState() != LocalRepoState.State.REBASE_IN_PROGRESS) {
            throw new IllegalStateException("No rebase is in progress.");
        }

        LocalRepoState newState = LocalRepoState.normal();
        db.updateRepoState(newState);

        LocalBranchState currentBranch = db.getCurrentBranch();
        CheckoutStateManager checkoutStateManager = CheckoutStateManager.of(config, db, serverTalker);
        checkoutStateManager.sync(
                currentBranch.getBranchName(),
                currentBranch.getHeadCommitId(),
                CheckoutStateManager.SyncType.DOWNLOAD_AND_SYNC_LOCAL);
        db.finishTransaction();
        LOG.info(
                "Rebase cancelled, and synced back to %s:%d",
                currentBranch.getBranchName(), currentBranch.getHeadCommitId());
    }


    // TODO: Make sure I delete deleted folders.
    private void doRebase(String mainCommitStr) throws Exception {

        db.startTransaction();
        LocalRepoState repoState = db.getRepoState();
        if (repoState.getState() != LocalRepoState.State.NORMAL) {
            throw new IllegalStateException("A rebase is already in progress.");
        }


        LocalBranchState currentBranch = db.getCurrentBranch();
        if (currentBranch.getHeadCommitId() != currentBranch.getCurrentlySyncedCommitId()) {
            throw new IllegalStateException(String.format(
                    "You must be synced to the branch head (%s:%d) before rebasing.",
                    currentBranch.getBranchName(), currentBranch.getHeadCommitId()));
        }

        List<String> parts = COLON_SPLITTER.limit(2).splitToList(mainCommitStr);
        String branchFrom = parts.get(0);
        if (!branchFrom.equals("main")) {
            throw new IllegalStateException("You can only rebase from main.");
        }
        long newMainCommitId = Long.parseLong(parts.get(1));


        List<CommitEntry> branchCommitsDescending = db.getCommitsSince(currentBranch.getBranchName(), 0);
        branchCommitsDescending = ImmutableList.sortedCopyOf(
                Comparator.comparingLong(CommitEntry::getCommitId).reversed(),
                branchCommitsDescending);
        long mainIntegrationBaseCommit = currentBranch.getBaseCommitId();
        for (CommitEntry commit : branchCommitsDescending) {
            if (commit.getIntegration().isPresent()) {
                Verify.verify(commit.getIntegration().get().getBranch().equals("main"));
                mainIntegrationBaseCommit = commit.getIntegration().get().getCommitId();
                break;
            }
        }

        if (newMainCommitId <= mainIntegrationBaseCommit) {
            throw new IllegalStateException(String.format(
                    "You can only rebase forward past this branch's base commit (main:%d).",
                    mainIntegrationBaseCommit));
        }

        this.sequenceNames = ImmutableList.of(
                "main:" + mainIntegrationBaseCommit,
                "main:" + newMainCommitId,
                currentBranch.getBranchName() + ":" + currentBranch.getHeadCommitId());

        CheckoutStateManager checkoutStateManager = CheckoutStateManager.of(config, db, serverTalker);
        checkoutStateManager.sync("main", newMainCommitId, CheckoutStateManager.SyncType.DOWNLOAD_ONLY);

        int numRealChanges = doRebaseCrawl(currentBranch, mainIntegrationBaseCommit, newMainCommitId);

        LocalRepoState newState = LocalRepoState.forRebaseInProgress(mainCommitStr);
        db.updateRepoState(newState);
        db.finishTransaction();

        String message;
        if (numRealChanges == 0) {
            message = "No changes were found to integrate.";
        } else {
            message = "Merging complete. Review everything and resolve any merge conflicts by hand.";
        }

        LOG.info(""
                        + "\n%s Run:"
                        + "\nhd rebase commit  --  To finalize and commit the merge"
                        + "\nhd rebase cancel  --  To cancel the merge and revert to branch head",
                message);
    }


    private int doRebaseCrawl(
            LocalBranchState branch,
            long mainIntegrationBaseCommit,
            long newMainCommitId) throws IOException {
        RebaseCrawler newMainChangeCrawler = new RebaseCrawler(
                db,
                "main", mainIntegrationBaseCommit, 0,
                "main", newMainCommitId, mainIntegrationBaseCommit);
        List<RebaseCrawler.FileChange> newMainChanges = newMainChangeCrawler.crawl();

        RebaseCrawler branchChangeCrawler = new RebaseCrawler(
                db,
                "main", mainIntegrationBaseCommit, 0,
                branch.getBranchName(), branch.getHeadCommitId(), mainIntegrationBaseCommit);
        List<RebaseCrawler.FileChange> branchChanges = branchChangeCrawler.crawl();

        // Path -> change
        // TODO: Toss these maps and just use List.findFirst() ?
        Map<String, RebaseCrawler.FileChange> newMainMappedChanges = newMainChanges.stream()
                .collect(Collectors.toUnmodifiableMap(RebaseCrawler.FileChange::getPath, Function.identity()));
        Map<String, RebaseCrawler.FileChange> branchMappedChanges = branchChanges.stream()
                .collect(Collectors.toUnmodifiableMap(RebaseCrawler.FileChange::getPath, Function.identity()));
        List<RebasePathComparison> comparisons = new ArrayList<>();

        // Main-only changes
        for (RebaseCrawler.FileChange newMainChange : newMainChanges) {
            if (!branchMappedChanges.containsKey(newMainChange.getPath())) {
                comparisons.add(new RebasePathComparison(
                        newMainChange.getPath(),
                        newMainChange.getChange(),
                        RebasePathComparison.Change.NO_CHANGE,
                        // TODO: Move everything to Optional's.
                        newMainChange.baseId.orElse(null),
                        newMainChange.changedId.orElse(null),
                        null));
            }
        }


        // Branch changes and conflicts
        for (RebaseCrawler.FileChange branchChange : branchChanges) {
            if (!newMainMappedChanges.containsKey(branchChange.getPath())) {
                comparisons.add(new RebasePathComparison(
                        branchChange.getPath(),
                        RebasePathComparison.Change.NO_CHANGE,
                        branchChange.getChange(),
                        branchChange.baseId.orElse(null),
                        null,
                        branchChange.changedId.orElse(null)));

            } else {
                RebaseCrawler.FileChange newMainChange = newMainMappedChanges.get(branchChange.getPath());
                Verify.verify(newMainChange.baseId.equals(branchChange.baseId));
                comparisons.add(new RebasePathComparison(
                        branchChange.getPath(),
                        newMainChange.getChange(),
                        branchChange.getChange(),
                        branchChange.baseId.orElse(null),
                        newMainChange.changedId.orElse(null),
                        branchChange.changedId.orElse(null)));
            }
        }

        int numRealChanges = 0;
        for (RebasePathComparison comparison : comparisons) {
            numRealChanges += handleComparison(comparison);
        }
        return numRealChanges;
    }


    // How I tested these cases:
    //
    // - On a 'base_contents' branch, echo "fileN" > fileN.txt for N in 1..5.
    // - Merge base_contents.
    //
    // - On a 'to_merge' branch, make these changes and merge to main:
    //     No conflict:
    //     - Delete file1.txt
    //     - Add file6.txt
    //     Conflict:
    //     - Delete file2.txt
    //     - echo "to_merge change 3" >> file3.txt
    //     - Delete file4.txt
    //     - echo "to_merge change 5" >> file5.txt
    //     - Add file7.txt, "to_merge new 7"
    //
    // - On a 'working' branch FROM BASE MAIN:2 (base_contents) that I'll be integrating conflicts into:
    //     No conflict:
    //     - (file1.txt is unchanged)
    //     Conflict:
    //     - Delete file2.txt
    //     - Delete file3.txt
    //     - echo "working change 4" >> file4.txt
    //     - echo "working change 5" >> file5.txt
    //     - Add file7.txt, "working new 7"
    private int handleComparison(RebasePathComparison comparison) throws IOException {
        Preconditions.checkArgument(comparison.getPath().startsWith("/"));

        if (comparison.getMainChange() == RebasePathComparison.Change.NO_CHANGE) {
            // No op.
            return 0;

        } else if (comparison.getBranchChange() == RebasePathComparison.Change.NO_CHANGE) {
            Path localPath = config.getRoot().resolve(comparison.getPath().substring(1));

            switch (comparison.getMainChange()) {
                case ADDED:
                case MODIFIED:
                    String message = (comparison.getMainChange() == RebasePathComparison.Change.ADDED)
                            ? "New on main"
                            : "Modified on main";
                    LOG.info("\n%s: %s, adding it here.", comparison.getPath(), message);
                    RandomAccessBytes mainNewContents = db.getFile(comparison.getMainNewFileId().get()).getContents();
                    OutputStream localOut = Files.newOutputStream(localPath);
                    RandomAccessBytes.copyToStream(mainNewContents, localOut);
                    break;

                case DELETED:
                    LOG.info("\n%s: Deleted on main, deleting here.", comparison.getPath());
                    Files.delete(localPath);
                    break;

                default:
                    throw new IllegalStateException("Unexpected branch change: " + comparison.getBranchChange());
            }


        } else if (comparison.getMainChange() == RebasePathComparison.Change.ADDED
                && comparison.getBranchChange() == RebasePathComparison.Change.ADDED) {
            mergeFiles(
                    comparison.getPath(),
                    "New on main and branch.",
                    comparison.getMainBaseFileId().orElse(null),
                    comparison.getMainNewFileId().orElse(null),
                    comparison.getBranchFileId().orElse(null));


        } else if (comparison.getMainChange() == RebasePathComparison.Change.DELETED
                && comparison.getBranchChange() == RebasePathComparison.Change.DELETED) {
            LOG.info("\n%s: Deleted on both main and branch.", comparison.getPath());


        } else if (comparison.getMainChange() == RebasePathComparison.Change.DELETED
                && comparison.getBranchChange() == RebasePathComparison.Change.MODIFIED) {
            mergeFiles(
                    comparison.getPath(),
                    "Deleted on main, modified on branch.",
                    comparison.getMainBaseFileId().orElse(null),
                    comparison.getMainNewFileId().orElse(null),
                    comparison.getBranchFileId().orElse(null));


        } else if (comparison.getMainChange() == RebasePathComparison.Change.MODIFIED
                && comparison.getBranchChange() == RebasePathComparison.Change.DELETED) {
            mergeFiles(
                    comparison.getPath(),
                    "Modified on main, deleted on branch.",
                    comparison.getMainBaseFileId().orElse(null),
                    comparison.getMainNewFileId().orElse(null),
                    comparison.getBranchFileId().orElse(null));


        } else if (comparison.getMainChange() == RebasePathComparison.Change.MODIFIED
                && comparison.getBranchChange() == RebasePathComparison.Change.MODIFIED) {
            mergeFiles(
                    comparison.getPath(),
                    "Modified on both main and branch.",
                    comparison.getMainBaseFileId().orElse(null),
                    comparison.getMainNewFileId().orElse(null),
                    comparison.getBranchFileId().orElse(null));


        } else {
            throw new AssertionError(String.format(
                    "Unexpected rebase comparison: main is %s, while branch is %s.",
                    comparison.getMainChange(), comparison.getBranchChange()));
        }

        return 1;
    }


    private void mergeFiles(
            String path,
            String conflictStatusMessage,
            @Nullable String mainBaseFileId,
            @Nullable String mainNewFileId,
            @Nullable String branchFileId)
            throws IOException {
        Preconditions.checkState(sequenceNames != null);

        RandomAccessBytes mainBaseContents = (mainBaseFileId != null)
                ? db.getFile(mainBaseFileId).getContents()
                : ByteArrayRandomAccessBytes.empty();

        RandomAccessBytes mainNewContents = (mainNewFileId != null)
                ? db.getFile(mainNewFileId).getContents()
                : ByteArrayRandomAccessBytes.empty();

        RandomAccessBytes branchContents = (branchFileId != null)
                ? db.getFile(branchFileId).getContents()
                : ByteArrayRandomAccessBytes.empty();

        TextOrBinaryChecker.TextOrBinaryResult mainNewBinaryCheck = TextOrBinaryChecker.check(mainNewContents);
        TextOrBinaryChecker.TextOrBinaryResult branchBinaryCheck = TextOrBinaryChecker.check(branchContents);

        if (mainNewBinaryCheck.isBinary()) {
            if (branchBinaryCheck.isBinary()) {
                logConflict(
                        path, conflictStatusMessage, "Files are binary on both main and branch. You'll have to resolve the conflict yourself.");
            } else {
                logConflict(
                        path, conflictStatusMessage, "File is binary on main, but text on branch. You'll have to resolve the conflict yourself.");
            }
            return;
        } else if (branchBinaryCheck.isBinary()) {
            logConflict(
                    path, conflictStatusMessage, "File is text on main, but binary on branch. You'll have to resolve the conflict yourself.");
            return;
        }

        TextOrBinaryChecker.TextOrBinaryResult mainBaseBinaryCheck = TextOrBinaryChecker.check(mainBaseContents);
        if (mainBaseBinaryCheck.isBinary()) {
            mainBaseContents = ByteArrayRandomAccessBytes.empty();
        }

        logConflict(path, conflictStatusMessage, "Writing a text merge.");
        Path localPath = config.getRoot().resolve(path.substring(1));
        LOG.debug("Writing merged text to path %s", localPath);
        GitTextMerger textMerger = GitTextMerger.of(sequenceNames, mainBaseContents, mainNewContents, branchContents);
        textMerger.writeToLocalPath(localPath);
    }


    private void logConflict(String path, String conflictStatus, String mergeMessage) {
        LOG.info("\n%s: %s\n%s", path, conflictStatus, mergeMessage);
    }

}
