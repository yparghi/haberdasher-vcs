package com.haberdashervcs.client.commands;

import java.util.List;

import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class StatusCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(StatusCommand.class);


    private final LocalDb db;

    public StatusCommand(RepoConfig repoConfig, LocalDb db, List<String> otherArgs) {
        this.db = db;
    }

    @Override
    public void perform() throws Exception {
        db.startTransaction();
        LocalBranchState currentBranch = db.getCurrentBranch();
        List<String> checkedOutPaths = db.getGlobalCheckedOutPaths().toList();
        db.finishTransaction();

        LOG.info(
                "At %s:%d. Branch head is %s:%d and its base is main:%d",
                currentBranch.getBranchName(), currentBranch.getCurrentlySyncedCommitId(),
                currentBranch.getBranchName(), currentBranch.getHeadCommitId(),
                currentBranch.getBaseCommitId());
        LOG.info("\nYour checked out paths are:");
        for (String path : checkedOutPaths) {
            LOG.info("- %s", path);
        }
        LOG.debug("Current branch state: %s", currentBranch.getDebugString());
    }
}
