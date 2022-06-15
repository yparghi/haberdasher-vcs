package com.haberdashervcs.client.crawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchAndCommit;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;


public final class LocalChangeCrawler {

    private static final HdLogger LOG = HdLoggers.create(LocalChangeCrawler.class);

    private static final String CONFIG_FILE_NAME = "hdconfig";


    private final RepoConfig config;
    private final LocalDb db;
    private final BranchAndCommit baseCommit;
    private final CheckoutPathSet allPaths;
    private final LocalChangeHandler changeHandler;
    private final ConfigFileTracker configFileTracker;

    public LocalChangeCrawler(
            RepoConfig config,
            LocalDb db,
            BranchAndCommit baseCommit,
            CheckoutPathSet allPaths,
            LocalChangeHandler changeHandler) {
        Preconditions.checkArgument(!allPaths.isEmpty());
        this.config = config;
        this.db = db;
        this.baseCommit = baseCommit;
        this.allPaths = allPaths;
        this.changeHandler = changeHandler;
        this.configFileTracker = new ConfigFileTracker();
    }


    public static final class CrawlEntry {
        private final HdFolderPath path;
        private final @Nullable FolderListing folderInCommit;

        private CrawlEntry(HdFolderPath path, @Nullable FolderListing folderInCommit) {
            this.path = path;
            this.folderInCommit = folderInCommit;
        }

        public HdFolderPath getPath() {
            return path;
        }

        @Nullable
        public FolderListing getFolderInCommit() {
            return folderInCommit;
        }

        private String getDebugString() {
            return MoreObjects.toStringHelper(this)
                    .add("path", path)
                    .add("folderInCommit", folderInCommit)
                    .toString();
        }
    }


    public void crawl() throws IOException {
        final LocalBranchState branchState = db.getBranchState(baseCommit.getBranchName()).get();

        LinkedList<CrawlEntry> crawlEntries = new LinkedList<>();
        for (String headPath : allPaths.toList()) {
            HdFolderPath hdPath = HdFolderPath.fromFolderListingFormat(headPath);
            Optional<FolderListing> headListing = findFolderFallBackToMain(hdPath, branchState);
            crawlEntries.add(new CrawlEntry(hdPath, headListing.orElse(null)));
        }

        while (!crawlEntries.isEmpty()) {
            final CrawlEntry thisEntry = crawlEntries.removeFirst();
            if (configFileTracker.shouldIgnorePath(thisEntry.path.forFolderListing())) {
                continue;
            }
            LOG.debug("Looking at crawl entry: %s", thisEntry.getDebugString());

            final Path localDir = thisEntry.path.toLocalPathFromRoot(config.getRoot());
            Path configFilePath = localDir.resolve(CONFIG_FILE_NAME);
            if (configFilePath.toFile().isFile()) {
                configFileTracker.addConfig(thisEntry.path, configFilePath);
            }

            List<LocalComparisonToCommit> comparisons = comparisonsForThisFolder(thisEntry);

            for (LocalComparisonToCommit comparison : comparisons) {
                if (comparison.pathInLocalRepo != null) {
                    addCrawlEntriesForLocalSubfolder(thisEntry.path, comparison, crawlEntries, branchState);

                } else if (comparison.entryInCommit.getType() == FolderListing.Entry.Type.FOLDER) {
                    // This path entry is present in the commit, but not locally. We still have to crawl its recursive
                    // absences.
                    HdFolderPath subfolderPath = thisEntry.path.joinWithSubfolder(comparison.entryInCommit.getName());
                    Optional<FolderListing> subfolderListing = findFolderFallBackToMain(
                            subfolderPath, branchState);
                    if (subfolderListing.isEmpty()) {
                        // There was some bug in client-server transmission or storage of folders. To fail gracefully,
                        // just skip this folder.
                        LOG.debug(
                                "BUG: No folder %s found on branch %s at commit %d",
                                subfolderPath, baseCommit.getBranchName(), baseCommit.getCommitId());
                        continue;
                    }

                    // Depth first
                    CrawlEntry nextFolderEntry = new CrawlEntry(subfolderPath, subfolderListing.get());
                    crawlEntries.add(0, nextFolderEntry);
                }
            }


            // Ignore empty, unchanged folders.
            if (!comparisons.isEmpty()) {
                changeHandler.handleComparisons(thisEntry.path, comparisons, thisEntry);
            }
        }
    }


    private Optional<FolderListing> findFolderFallBackToMain(HdFolderPath path, LocalBranchState branchState) {
        Optional<FolderListing> rootListing = db.findFolderAt(
                baseCommit.getBranchName(), path.forFolderListing(), baseCommit.getCommitId());
        if (rootListing.isPresent()) {
            return rootListing;
        } else if (!baseCommit.getBranchName().equals("main")) {
            return db.findFolderAt(
                    "main", path.forFolderListing(), branchState.getBaseCommitId());
        } else {
            return Optional.empty();
        }
    }


    private void addCrawlEntriesForLocalSubfolder(
            HdFolderPath parentPath,
            LocalComparisonToCommit comparison,
            LinkedList<CrawlEntry> crawlEntries,
            LocalBranchState branchState)
            throws IOException {
        Preconditions.checkArgument(comparison.pathInLocalRepo != null);
        HdFolderPath subfolderPath = parentPath.joinWithSubfolder(comparison.name);

        Optional<FolderListing> folderFromCommit;
        if (comparison.entryInCommit != null
                && comparison.entryInCommit.getType() == FolderListing.Entry.Type.FOLDER) {
            folderFromCommit = findFolderFallBackToMain(
                    subfolderPath, branchState);
        } else {
            folderFromCommit = Optional.empty();
        }

        if (comparison.pathInLocalRepo.toFile().isDirectory()) {
            crawlEntries.add(0, new CrawlEntry(subfolderPath, folderFromCommit.orElse(null)));

        } else if (folderFromCommit.isPresent()) {
            crawlEntries.add(0, new CrawlEntry(subfolderPath, folderFromCommit.get()));
        }
    }


    private List<LocalComparisonToCommit> comparisonsForThisFolder(CrawlEntry thisEntry) throws IOException {
        final Path localDir = thisEntry.path.toLocalPathFromRoot(config.getRoot());

        final List<Path> localPathsThisFolder;
        if (!localDir.toFile().exists()) {
            localPathsThisFolder = Collections.emptyList();

        } else if (localDir.toFile().isFile()) {
            // If a file is replacing a folder, there's nothing to crawl.
            return Collections.emptyList();

        } else {
            localPathsThisFolder = Files.list(localDir).collect(Collectors.toList());
        }

        Map<String, LocalComparisonToCommit> nameToComparison = new TreeMap<>();
        for (Path localPath : localPathsThisFolder) {
            if (config.isHdInternalPath(localPath)) {
                continue;
            }

            String localPathName = localPath.getFileName().toString();
            String hdPath = (localPath.toFile().isDirectory())
                    ? thisEntry.path.joinWithSubfolder(localPathName).forFolderListing()
                    : thisEntry.path.filePathForName(localPathName);
            if (configFileTracker.shouldIgnorePath(hdPath)) {
                continue;
            }

            LocalComparisonToCommit newComparison = new LocalComparisonToCommit(localPathName, db);
            newComparison.pathInLocalRepo = localPath;
            nameToComparison.put(localPathName, newComparison);
        }

        if (thisEntry.folderInCommit != null) {
            for (FolderListing.Entry entry : thisEntry.folderInCommit.getEntries()) {
                LocalComparisonToCommit comparison;
                if (!nameToComparison.containsKey(entry.getName())) {
                    comparison = new LocalComparisonToCommit(entry.getName(), db);
                } else {
                    comparison = nameToComparison.get(entry.getName());
                }
                comparison.entryInCommit = entry;
                nameToComparison.put(entry.getName(), comparison);
            }
        }

        return nameToComparison.values().stream().collect(Collectors.toList());
    }
}
