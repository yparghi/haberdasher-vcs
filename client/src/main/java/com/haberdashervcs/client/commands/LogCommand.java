package com.haberdashervcs.client.commands;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.protobuf.CommitsProto;


final class LogCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(LogCommand.class);


    private final RepoConfig repoConfig;
    private final LocalDb db;
    private final List<String> otherArgs;

    public LogCommand(RepoConfig repoConfig, LocalDb db, List<String> otherArgs) {
        this.repoConfig = repoConfig;
        this.db = db;
        this.otherArgs = otherArgs;
    }


    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 0) {
            throw new IllegalArgumentException("Usage: hd log");
        }

        db.startTransaction();

        LocalBranchState currentBranch = db.getCurrentBranch();
        String branchName = currentBranch.getBranchName();
        // TODO: Take a path prefix as an optional argument.
        String path = "/";
        long atCommitId = currentBranch.getCurrentlySyncedCommitId();

        ServerTalker server = JettyServerTalker.forConfig(repoConfig);

        List<CommitEntry> serverCommits = server.log(branchName, path, atCommitId);

        long maxServerCommitId = 0;
        for (CommitEntry commit : serverCommits) {
            printOneCommit(commit, false);
            if (commit.getCommitId() > maxServerCommitId) {
                maxServerCommitId = commit.getCommitId();
            }
        }

        // TODO: Apply `atCommitId` to the db call, for filtering/range in the query?
        List<CommitEntry> localCommitsUnfiltered = db.getCommitsSince(branchName, maxServerCommitId);
        localCommitsUnfiltered = Lists.reverse(localCommitsUnfiltered);
        List<CommitEntry> localCommits = filter(localCommitsUnfiltered, path, atCommitId);

        for (CommitEntry commit : localCommits) {
            printOneCommit(commit.withAuthor("(local)"), true);
        }

        // Ensure no writes.
        db.cancelTransaction();
    }


    private List<CommitEntry> filter(List<CommitEntry> localCommitsUnfiltered, String path, long atCommitId) {
        List<CommitEntry> out = new ArrayList<>();
        for (CommitEntry commit : localCommitsUnfiltered) {
            if (path.equals("/")) {
                // We special-case "/" because we want it to return even empty commits, like integration commits.
                out.add(commit);
            } else if (commit.getCommitId() <= atCommitId && matchesPath(commit, path)) {
                out.add(commit);
            }
        }
        return out;
    }


    private boolean matchesPath(CommitEntry commit, String path) {
        for (CommitEntry.CommitChangedPath changedPath : commit.getChangedPaths()) {
            if (changedPath.getPath().startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private void printOneCommit(CommitEntry commit, boolean isLocal) {
        String header = String.format("Commit: %d", commit.getCommitId());
        if (isLocal) {
            header += " (local)";
        }
        String logMessage = String.format(
                "%s\nAuthor: %s\nMessage: %s\n",
                header,
                commit.getAuthorUserId(),
                commit.getMessage());
        if (commit.getIntegration().isPresent()) {
            CommitsProto.BranchIntegration integration = commit.getIntegration().get();
            logMessage += String.format(
                    "Integration of %s:%d\n", integration.getBranch(), integration.getCommitId());
        }
        LOG.info(logMessage);
    }
}
