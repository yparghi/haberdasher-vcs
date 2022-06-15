package com.haberdashervcs.client.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.commit.CommitCommand;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;


// OLD NOTES on whether to refactor into/with LocalChangeCrawler, 11/6/2021:
//
// - LocalChangeCrawler:
//     - some kind of "CommitReader" that pulls out the left-side file: from git, for this use case, and locally for the
//       other?
//
// - CommitChangeHandler:
//     - Can we refactor it (using LocalComparisonToCommit?) so that stuff like detecting the change "type" is
//       done in a shared/reusable class/method b/w local and git? And, say, whether the folder is changed?...
// *** Reuse SyncToCommitHandler???...
//
// - LocalComparisonToCommit:
//     - Needs an abstracted way to read the "local" (left-side) file...
//
// Ultimately, I decided to keep it separate, since git crawling has different behavior, like it will add or overwrite
// files, but not delete ones elsewhere in the repo.
final class GitChangeCrawler {

    private static final HdLogger LOG = HdLoggers.create(GitChangeCrawler.class);


    private final RepoConfig config;
    private final LocalDb db;
    private final RevCommit gitCommit;
    private final Repository gitRepo;

    GitChangeCrawler(RepoConfig config, LocalDb db, RevCommit gitCommit, Repository gitRepo) {
        this.config = config;
        this.db = db;
        this.gitCommit = gitCommit;
        this.gitRepo = gitRepo;
    }


    void compare() {
        try {
            compareInternal();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    private static class WalkEntry {
        private final AnyObjectId gitTree;
        private final Path localPath;

        private WalkEntry(AnyObjectId gitTree, Path localPath) {
            this.gitTree = Preconditions.checkNotNull(gitTree);
            this.localPath = Preconditions.checkNotNull(localPath);
        }
    }


    // TODO!!! Set up diff testing.
    void compareInternal() throws Exception {
        LOG.info("Crawling git commit: %s", gitCommit.getId().name());

        TreeWalk treeWalk = new TreeWalk(gitRepo);
        treeWalk.setRecursive(false);

        treeWalk.reset(gitCommit.getTree());
        if (!treeWalk.next()) {
            LOG.info("Skipping this commit -- empty tree.");
            return;
        }
        // Clear the TreeWalk's state. In crawling, it will be reset to a git tree at the start of each loop.
        treeWalk.reset();

        LinkedList<WalkEntry> walkEntries = new LinkedList<>();
        WalkEntry rootWalkEntry = new WalkEntry(gitCommit.getTree(), config.getRoot());
        walkEntries.add(rootWalkEntry);

        while (!walkEntries.isEmpty()) {
            WalkEntry thisEntry = walkEntries.pop();
            treeWalk.reset(thisEntry.gitTree);
            ArrayList<FolderListing.Entry> folderEntries = new ArrayList<>();
            Set<String> seenSubpaths = new HashSet<>();

            while (treeWalk.next()) {
                final String subpathName = treeWalk.getNameString();
                seenSubpaths.add(subpathName);
                final Path localSubpath = thisEntry.localPath.resolve(subpathName);
                final File localSubfile = localSubpath.toFile();

                if (treeWalk.isSubtree()) {
                    if (localSubfile.isFile()) {
                        Files.delete(localSubpath);
                        Files.createDirectories(localSubpath);
                    } else if (!localSubfile.exists()) {
                        Files.createDirectories(localSubpath);
                    }

                    WalkEntry walkThisSubfolder = new WalkEntry(treeWalk.getObjectId(0), localSubpath);
                    walkEntries.add(walkThisSubfolder);


                } else {
                    if (localSubfile.isDirectory()) {
                        deleteRecursive(localSubpath);
                    }

                    ObjectLoader loader = gitRepo.open(treeWalk.getObjectId(0));
                    byte[] blobBytes = loader.getBytes();
                    Files.write(localSubpath, blobBytes);
                }
            }


            // Delete local files/folders not in the git tree.
            List<Path> localFilesThisFolder = Files.list(thisEntry.localPath).collect(Collectors.toList());
            for (Path localPath : localFilesThisFolder) {
                if (config.isHdInternalPath(localPath)) {
                    continue;
                }
                if (!seenSubpaths.contains(localPath.getFileName().toString())) {
                    File localFile = localPath.toFile();
                    if (localFile.isFile()) {
                        Files.delete(localPath);
                    } else {
                        deleteRecursive(localPath);
                    }
                }
            }
        }


        String commitMessage = String.format(
                "Imported from git commit %s:\n%s",
                gitCommit.getId().name(),
                gitCommit.getFullMessage());
        CommitCommand commitCommand = CommitCommand.fromCommandLine(
                config,
                db,
                ImmutableList.of(
                        gitCommit.getAuthorIdent().toExternalString(),
                        commitMessage));
        commitCommand.perform();
    }


    // Thanks to: https://stackoverflow.com/a/27917071
    private void deleteRecursive(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
