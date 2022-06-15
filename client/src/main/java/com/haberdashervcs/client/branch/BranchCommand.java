package com.haberdashervcs.client.branch;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;


public class BranchCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(BranchCommand.class);


    private final RepoConfig config;
    private final LocalDb db;
    private final List<String> otherArgs;

    public BranchCommand(RepoConfig config, LocalDb db, List<String> otherArgs) {
        this.config = config;
        this.db = db;
        this.otherArgs = ImmutableList.copyOf(otherArgs);
    }


    @Override
    public void perform() throws Exception {
        // TEMP! Notes from 6/18/2021, re. testing rebase:
        // - I start on main, on the head from the server, let's say commit #2.
        // - I'll *CREATE* a branch, say 'some_branch'. It has a base commit of 2 (on main).
        // - On 'some_branch' I'll echo 'branch text' > new.txt. Then I commit this as #branch-1.
        //     (since branch commits are increments, starting at 0)
        // - I'll *SWITCH* to main, and echo 'main text' > new.txt. Then I commit this as #main-3.
        // - Then I switch back to some_branch, and run rebase (onto 3, either implicitly or as a
        //     given argument)
        // - I'd expect rebase to give me a conflict on new.txt.
        //
        // That gives these subcommands:
        // - branch create (which is just local)
        // - branch switch
        // ? branch push? (No.)
        //     - I like (but I'm not certain about) an explicit command to create the branch on the
        //         server, independent of pushing changes. But I'm not sure it's worth the hassle.
        //         So for now, let's just use 'push' instead of 'branch push', and the server can
        //         create a BranchEntry as needed.

        if (otherArgs.size() != 2) {
            throw new IllegalArgumentException("Usage: hd branch create <name of new branch>");
        }

        final String subcommand = otherArgs.get(0);
        final String branchName = otherArgs.get(1);

        if (subcommand.equals("create")) {
            handleCreate(branchName);

        } else {
            throw new IllegalArgumentException("Unknown subcommand: " + subcommand);
        }
    }


    private void handleCreate(String branchName) throws Exception {
        db.startTransaction();

        LocalBranchState currentBranch = db.getCurrentBranch();
        if (!currentBranch.getBranchName().equals("main")) {
            throw new IllegalStateException("You must be on 'main' before you can create a new branch.");
        }

        ServerTalker serverTalker = JettyServerTalker.forConfig(config);

        Optional<BranchEntry> serverBranch = serverTalker.getBranch(branchName);
        if (serverBranch.isPresent()) {
            throw new IllegalStateException(
                    "Branch '" + serverBranch.get().getName()
                            + "' already exists on server, use 'sync' to switch to it.");
        }

        LocalBranchState mainState = db.getBranchState("main").get();
        LocalBranchState initialState = LocalBranchState.of(
                branchName, mainState.getCurrentlySyncedCommitId(), 0, 0);
        db.createNewBranch(branchName, initialState);

        db.finishTransaction();
        LOG.info("Done.");
    }
}
