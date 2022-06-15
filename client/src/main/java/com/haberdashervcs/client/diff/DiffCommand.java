package com.haberdashervcs.client.diff;

import java.util.List;

import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.crawl.LocalChangeHandler;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchAndCommit;


public class DiffCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(DiffCommand.class);


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;

    public DiffCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = otherArgs;
        this.db = db;
    }


    @Override
    public void perform() throws Exception {

        // TODO: Support diffing a subfolder.
        if (otherArgs.size() != 0) {
            throw new IllegalArgumentException("Usage: hd diff");
        }

        db.startTransaction();

        LocalBranchState currentBanch = db.getCurrentBranch();
        PrintChangeHandler handler = new PrintChangeHandler(db);

        LocalChangeCrawler crawler = new LocalChangeCrawler(
                config,
                db,
                BranchAndCommit.of(currentBanch.getBranchName(), currentBanch.getCurrentlySyncedCommitId()),
                db.getGlobalCheckedOutPaths(),
                handler);
        crawler.crawl();

        handler.printFinalMessage();

        db.finishTransaction();
    }
}
