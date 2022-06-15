package com.haberdashervcs.client.commit;

import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDbRowKeyer;
import com.haberdashervcs.common.exceptions.HdNormalError;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchAndCommit;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.protobuf.CommitsProto;


public final class CommitCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(CommitCommand.class);

    // TODO: Figure out local display of the author.
    private static final String STUB_AUTHOR = "---";


    public static CommitCommand fromCommandLine(RepoConfig config, LocalDb db, List<String> args) {
        if (args.size() != 1) {
            throw new HdNormalError("Usage: hd commit <message>");
        }
        String message = args.get(0);
        return new CommitCommand(config, db, message, null, true);
    }


    public static CommitCommand forRebaseIntegration(
            RepoConfig config, LocalDb db, String message, CommitsProto.BranchIntegration integration) {
        Preconditions.checkNotNull(integration);
        return new CommitCommand(config, db, message, integration, false);
    }


    private final RepoConfig config;
    private final LocalDb db;
    private final String message;
    @Nullable private final CommitsProto.BranchIntegration integration;
    private final boolean newTransaction;

    private CommitCommand(
            RepoConfig config,
            LocalDb db,
            String message,
            @Nullable CommitsProto.BranchIntegration integration,
            boolean newTransaction) {
        this.config = config;
        this.db = db;
        this.message = message;
        this.integration = integration;
        this.newTransaction = newTransaction;
    }

    @Override
    public void perform() throws Exception {

        if (newTransaction) {
            db.startTransaction();
        }

        LocalBranchState currentBranch = db.getCurrentBranch();
        if (currentBranch.getBranchName().equals("main")) {
            throw new IllegalStateException(
                    "You can't commit directly to main. Please create a branch first.");
        }
        if (currentBranch.getHeadCommitId() != currentBranch.getCurrentlySyncedCommitId()) {
            throw new IllegalStateException(String.format(
                    "You must be synced to the branch's head commit (%d) to add a new commit. Currently you're synced to commit %d.",
                    currentBranch.getHeadCommitId(), currentBranch.getCurrentlySyncedCommitId()));
        }

        BranchAndCommit currentBC = BranchAndCommit.of(currentBranch.getBranchName(), currentBranch.getHeadCommitId());
        CommitChangeHandler changeHandler = new CommitChangeHandler(db, currentBC);
        LocalChangeCrawler crawler = new LocalChangeCrawler(
                config,
                db,
                currentBC,
                db.getGlobalCheckedOutPaths(),
                changeHandler);
        crawler.crawl();

        List<CommitEntry.CommitChangedPath> changedPaths = ImmutableList.copyOf(changeHandler.getChangedPaths());
        // It's okay to have an empty commit on rebasing, since it marks that the rebase/integration happened.
        if (changedPaths.isEmpty() && integration == null) {
            LOG.info("No changes found.");
            db.cancelTransaction();
            return;
        }

       CommitEntry newCommit = CommitEntry.of(
                currentBranch.getBranchName(),
                currentBranch.getHeadCommitId() + 1,
                STUB_AUTHOR,
                message,
                changedPaths);
        if (integration != null) {
            newCommit = newCommit.withIntegration(integration);
        }
        // TODO: Make the keyer internal to the db impl.
        db.putCommit(SqliteLocalDbRowKeyer.getInstance().forCommit(newCommit), newCommit);

        LocalBranchState newBranchState = LocalBranchState.of(
                currentBranch.getBranchName(),
                currentBranch.getBaseCommitId(),
                currentBranch.getHeadCommitId() + 1,
                currentBranch.getHeadCommitId() + 1);
        db.updateBranchState(currentBranch.getBranchName(), newBranchState);

        if (newTransaction) {
            db.finishTransaction();
        }
        LOG.info(
                "Changes committed to %s:%d",
                currentBranch.getBranchName(),
                newBranchState.getHeadCommitId());
    }

}
