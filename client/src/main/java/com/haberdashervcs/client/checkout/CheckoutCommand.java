package com.haberdashervcs.client.checkout;

import java.util.List;

import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


/**
 * Adds a path to the local repo.
 */
public final class CheckoutCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(CheckoutCommand.class);


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;

    public CheckoutCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = otherArgs;
        this.db = db;
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 1) {
            throw new IllegalArgumentException("Usage: hd checkout /some/path/");
        }
        String path = otherArgs.get(0);
        if (!path.startsWith("/") || !path.endsWith("/")) {
            throw new IllegalArgumentException("Path must start and end with /");
        }

        ServerTalker serverTalker = JettyServerTalker.forConfig(config);
        CheckoutStateManager checkoutManager = CheckoutStateManager.of(config, db, serverTalker);

        // What makes this transactional/atomic?: If it fails partway to check out /somepath, then some local files will
        // be left lying around in somepath/ but since that path won't be in the DB, those files will just be ignored.
        //
        // TODO: Make sure this is actually true, i.e. that any crawling sticks to checked-out paths.
        db.startTransaction();

        checkoutManager.addCheckoutPathToRepo(path);

        LocalBranchState currentBranch = db.getCurrentBranch();
        checkoutManager.sync(
                currentBranch.getBranchName(),
                currentBranch.getCurrentlySyncedCommitId(),
                CheckoutStateManager.SyncType.DOWNLOAD_AND_SYNC_LOCAL);

        db.finishTransaction();
    }
}
