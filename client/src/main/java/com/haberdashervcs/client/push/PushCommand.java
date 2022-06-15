package com.haberdashervcs.client.push;

import java.util.List;
import java.util.Optional;

import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.protobuf.ServerProto;


public final class PushCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(PushCommand.class);


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;

    public PushCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = otherArgs;
        this.db = db;
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 0) {
            throw new IllegalArgumentException("Usage: hd push");
        }

        db.startTransaction();
        ServerTalker serverTalker = JettyServerTalker.forConfig(config);

        LocalBranchState localBranch = db.getCurrentBranch();
        if (localBranch.getBranchName().equals("main")) {
            throw new IllegalStateException("You can't push directly to main.");
        } else if (localBranch.getCurrentlySyncedCommitId() != localBranch.getHeadCommitId()) {
            throw new IllegalStateException(String.format(
                    "You must sync to the head of this branch (%s:%d) before pushing.",
                    localBranch.getBranchName(), localBranch.getHeadCommitId()));
        }
        LOG.debug("Local branch state: %s", localBranch.getDebugString());

        Optional<BranchEntry> branchOnServer = serverTalker.getBranch(localBranch.getBranchName());
        final long serverHeadCommitId;
        if (branchOnServer.isPresent()) {
            serverHeadCommitId = branchOnServer.get().getHeadCommitId();
        } else {
            serverHeadCommitId = 0;
        }

        LOG.debug("TEMP server head commit: %d", serverHeadCommitId);
        if (serverHeadCommitId == localBranch.getHeadCommitId()) {
            LOG.info("Nothing to push: the server and local heads are both %s:%d",
                    localBranch.getBranchName(), serverHeadCommitId);
            return;
        }


        PushObjectSet pushQuerySet = buildPushQuerySet(localBranch, serverHeadCommitId);
        LOG.debug("Got pushQuerySet %s", pushQuerySet.getDebugString());

        ServerProto.PushQueryResponse pushQueryResponse = serverTalker.queryForPush(localBranch, pushQuerySet);
        if (pushQueryResponse.getResponseType() != ServerProto.PushQueryResponse.ResponseType.OK) {
            LOG.info("Push to server failed with error: %s", pushQueryResponse.getMessage());
            return;
        }

        PushObjectSet filtered = pushQuerySet.filterToResponse(pushQueryResponse);
        serverTalker.push(localBranch, filtered, db);

        db.finishTransaction();
        LOG.info("Done.");
    }


    private PushObjectSet buildPushQuerySet(LocalBranchState localBranch, long serverHeadCommitId) {
        PushObjectSet pushQuerySet = new PushObjectSet(localBranch, serverHeadCommitId, db);
        pushQuerySet.search();
        return pushQuerySet;
    }

}
