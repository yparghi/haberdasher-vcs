package com.haberdashervcs.client.checkout;

import java.util.List;

import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


// IDEA: "hd sync --revert" to sync to a change as the current working state? Or, just use a separate "hd revert" cmd?
public final class SyncCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(SyncCommand.class);

    private static final String USAGE = "Usage: hd sync <branch name>:<commit #>";


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;

    public SyncCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = otherArgs;
        this.db = db;
    }


    // Then, considerations for the download:
    // - Conflicts? Do I need to track local changes separately from remote ones, even on the same branch?
    //
    // Notes on the overall approach, 9/19/2021:
    // - Example: "hd sync some_branch 4"
    // - Pull down the remote b.c. for that branch.
    // - Say the local b.c. is some_branch 6 and some_branch:4 *IS* in the database:
    //     - Sync to that using a LocalSyncChangeHandler or something.
    // - Say the local b.c. is some_branch 6 and some_branch:4 *IS NOT* in the database:
    //     - This means, for example, that the branch was initially checked out at 5 or 6. Or that the branch was never
    //         checked out at all.
    //     - Download it from the server, add it to the local d.b., and run a local sync.
    //         * TODO! Have the client and the server discuss which objects (e.g. files) the client already has -- how
    //             would I do this? Can the communication stream include questions and answers?
    //
    // TODO! What if the local d.b. has *some* of the branch+commit I'm syncing to, but not all, because new paths were
    //     checked out later? I need some better kind of state management, I think...
    // *** TODO! Figure out this "repo state" problem...
    //     - I *think* it's a sort of map (branch, commit) -> [list of checked out paths...]
    //
    // Repo state:
    // - The state of checkouts is (branch, commit #). Checked-out paths are a CONSTANT FOR THE WHOLE LOCAL REPO.
    // - So when I switch to (new branch, new commit), I have to make sure all checked-out paths have been downloaded.
    // - DB schema:
    //     - In Meta, just store all checked-out paths -- comma-separated list?
    //     - In Checkouts, row key (branch, commit #) points to some proto of CheckoutState? (put it in localdb.proto?)
    //         - The proto has a list of checked-out paths _for that branch and commit_.
    //
    // - CheckoutStateManager helper?:
    //     ? manager.verifyCheckedOut(branch, commit #, paths) ?
    //     - CheckoutStateManager.forDb(db) ?
    //
    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 1) {
            throw new IllegalArgumentException(USAGE);
        }

        String[] parts = otherArgs.get(0).split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(USAGE);
        }
        String branchName = parts[0];
        long commitId = Long.parseLong(parts[1]);

        ServerTalker serverTalker = JettyServerTalker.forConfig(config);
        CheckoutStateManager checkoutManager = CheckoutStateManager.of(config, db, serverTalker);

        db.startTransaction();
        checkoutManager.sync(branchName, commitId, CheckoutStateManager.SyncType.DOWNLOAD_AND_SYNC_LOCAL);
        db.finishTransaction();
        LOG.info("Done.");
    }
}
