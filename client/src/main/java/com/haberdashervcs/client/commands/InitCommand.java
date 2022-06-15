package com.haberdashervcs.client.commands;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDb;
import com.haberdashervcs.client.talker.JettyServerTalker;
import com.haberdashervcs.client.talker.ServerTalker;
import com.haberdashervcs.common.exceptions.HdNormalError;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;


class InitCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(InitCommand.class);


    private final List<String> otherArgs;

    InitCommand(List<String> otherArgs) {
        this.otherArgs = ImmutableList.copyOf(otherArgs);
    }

    @Override
    public void perform() throws Exception {

        if (otherArgs.size() != 3) {
            throw new HdNormalError("Usage: init <host> <repo> <command-line token>");
        }

        final String host = otherArgs.get(0);
        // TODO: Separate orgs and repos in the client.
        final String repo = otherArgs.get(1);
        final String org = repo;
        final String cliToken = otherArgs.get(2);

        Optional<RepoConfig> existingConfig = RepoConfig.find();
        if (existingConfig.isPresent()) {
            throw new IllegalStateException("This is already an HD repo.");
        }

        LOG.info("Creating local repo in ./%s/", repo);
        RepoConfig createdConfig = RepoConfig.create(host, org, repo, cliToken);

        ServerTalker server = JettyServerTalker.forConfig(createdConfig);
        Optional<BranchEntry> mainFromServer = server.getBranch("main");
        if (mainFromServer.isEmpty()) {
            throw new IllegalStateException("No main branch was found on the server! Does this repo exist?");
        }

        LocalDb db = SqliteLocalDb.inRepo(createdConfig);
        db.init(mainFromServer.get());

        LOG.info(
                "\nYou are synced to commit main:%d.\n"
                        + "Use the 'checkout' command to add a path to your local copy.",
                mainFromServer.get().getHeadCommitId());
    }

}
