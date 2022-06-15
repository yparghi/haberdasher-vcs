package com.haberdashervcs.client.commands;

import java.util.List;

import com.haberdashervcs.client.checkout.CheckoutStateManager;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.MergeResult;


final class MergeCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(MergeCommand.class);


    private final RepoConfig config;
    private final LocalDb db;
    private final List<String> otherArgs;

    MergeCommand(RepoConfig repoConfig, LocalDb db, List<String> otherArgs) {
        this.config = repoConfig;
        this.db = db;
        this.otherArgs = otherArgs;
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 0) {
            throw new IllegalArgumentException("Usage: hd merge");
        }

        db.startTransaction();

        LocalBranchState currentBranch = db.getCurrentBranch();
        if (currentBranch.getBranchName().equals("main")) {
            throw new IllegalStateException("You are currently on main. Please sync to another branch first.");
        }

        ServerTalker server = JettyServerTalker.forConfig(config);
        MergeResult result = server.merge(currentBranch);

        LOG.info("Result: %s", result.getResultType());
        LOG.info("Message: %s", result.getMessage());

        if (result.getResultType() == MergeResult.ResultType.SUCCESSFUL) {
            long newCommitIdOnMain = result.getNewCommitIdOnMain();
            LOG.info("Syncing to main:%d...", newCommitIdOnMain);
            CheckoutStateManager manager = CheckoutStateManager.of(config, db, server);
            manager.sync("main", newCommitIdOnMain, CheckoutStateManager.SyncType.DOWNLOAD_AND_SYNC_LOCAL);
        }

        db.finishTransaction();
        LOG.info("Done.");
    }
}
