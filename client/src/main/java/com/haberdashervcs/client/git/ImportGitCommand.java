package com.haberdashervcs.client.git;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;


// TODO: Refactor this to write objects directly to the DB from git, and toss the incredibly inefficient crawling
// approach.
public class ImportGitCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(ImportGitCommand.class);


    private final RepoConfig config;
    private final List<String> otherArgs;
    private final LocalDb db;

    public ImportGitCommand(RepoConfig config, List<String> otherArgs, LocalDb db) {
        this.config = config;
        this.otherArgs = ImmutableList.copyOf(otherArgs);
        this.db = db;
    }

    @Override
    public void perform() throws Exception {
        if (otherArgs.size() != 2) {
            throw new IllegalArgumentException("Usage: hd import_git <path to repo> <name of git branch>");
        }

        final Path repoPath = Paths.get(otherArgs.get(0));
        final String branch = otherArgs.get(1);

        Git git = Git.open(repoPath.toFile());
        RevWalk walk = new RevWalk(git.getRepository());
        // Walk from oldest commit to newest.
        walk.sort(RevSort.REVERSE, true);
        ObjectId headId = git.getRepository().resolve(branch);
        RevCommit headCommit = walk.parseCommit(headId);
        walk.markStart(headCommit);

        int testCounter = 0; // TEMP!
        for (RevCommit commit : walk) {
            processCommit(commit, git.getRepository());
            if ((++testCounter) >= 20) {
                break;
            }
        }

        walk.close();
        git.getRepository().close();
        git.close();
    }

    private void processCommit(RevCommit commit, Repository repo) throws Exception {
        LOG.debug("Got commit: %s", commit.getId());
        GitChangeCrawler crawler = new GitChangeCrawler(config, db, commit, repo);
        crawler.compare();
    }
}
