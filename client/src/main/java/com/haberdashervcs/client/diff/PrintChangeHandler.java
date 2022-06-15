package com.haberdashervcs.client.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.haberdashervcs.client.crawl.LocalChangeCrawler;
import com.haberdashervcs.client.crawl.LocalChangeHandler;
import com.haberdashervcs.client.crawl.LocalComparisonToCommit;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.diff.DiffHunk;
import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.diff.LineDiff;
import com.haberdashervcs.common.diff.TextOrBinaryChecker;
import com.haberdashervcs.common.diff.git.HistogramDiffer;
import com.haberdashervcs.common.diff.git.RabTextSequence;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.HdFolderPath;


final class PrintChangeHandler implements LocalChangeHandler {

    private static final HdLogger LOG = HdLoggers.create(PrintChangeHandler.class);


    private final LocalDb db;

    private int totalDifferences = 0;

    PrintChangeHandler(LocalDb db) {
        this.db = db;
    }


    @Override
    public void handleComparisons(
            HdFolderPath path, List<LocalComparisonToCommit> comparisons, LocalChangeCrawler.CrawlEntry crawlEntry)
            throws IOException {

        for (LocalComparisonToCommit comparison : comparisons) {
            //LOG.debug("Got comparison in %s: %s", path, comparison);

            DiffType diffType = getDiffTypeAgainstCommit(comparison);
            String displayPath = path.filePathForName(comparison.getName());
            switch (diffType) {
                case FOLDER:
                case SAME:
                    break;

                case NEW:
                    ++totalDifferences;
                    LOG.info("New: %s", displayPath);
                    break;

                case MISSING:
                    ++totalDifferences;
                    LOG.info("Deleted: %s", displayPath);
                    break;

                case TEXT_DIFFERENCE:
                    ++totalDifferences;
                    printTextDiff(displayPath, comparison);
                    break;

                case BINARY_DIFFERENCE:
                    ++totalDifferences;
                    LOG.info("Binary file: %s: %s", displayPath, getBinaryDiff(comparison));
                    break;
            }
        }
    }


    private void printTextDiff(String displayPath, LocalComparisonToCommit comparison) throws IOException {
        LOG.debug(
                "Diff at %s is %s vs. %s",
                displayPath,
                comparison.getEntryInCommit().getId(),
                comparison.getPathInLocalRepo());
        FileEntryWithPatchedContents commitContents = db.getFile(comparison.getEntryInCommit().getId());
        RandomAccessBytes commitRab = commitContents.getContents();
        RabTextSequence commitText = new RabTextSequence(commitRab);

        FileRandomAccessBytes localFileContents = FileRandomAccessBytes.of(comparison.getPathInLocalRepo().toFile());
        RabTextSequence localFileText = new RabTextSequence(localFileContents);

        HistogramDiffer differ = new HistogramDiffer();
        List<DiffHunk> hunks = differ.computeLineDiffs(commitRab, localFileContents);
        List<LineDiff> lineDiffs = new ArrayList<>();
        for (DiffHunk hunk : hunks) {
            lineDiffs.addAll(hunk.asUdiff(commitText, localFileText));
        }

        LOG.info("Diff for %s:", displayPath);
        for (LineDiff lineDiff : lineDiffs) {
            LOG.info("%s", lineDiff.getDisplayLine());
        }
    }


    ////// Diff computations

    private enum DiffType {
        FOLDER,
        NEW,
        MISSING,
        TEXT_DIFFERENCE,
        BINARY_DIFFERENCE,
        SAME
    }


    // TODO: Refactor to avoid redundant ops like getting contents/entry from the db.
    private DiffType getDiffTypeAgainstCommit(LocalComparisonToCommit comparison) throws IOException {
        if (comparison.getEntryInCommit() == null) {
            return DiffType.NEW;

        } else if (comparison.getPathInLocalRepo() == null) {
            return DiffType.MISSING;

        } else if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FILE
                && !comparison.getPathInLocalRepo().toFile().isFile()) {
            return DiffType.MISSING;

        } else if (comparison.getEntryInCommit().getType() == FolderListing.Entry.Type.FOLDER
                && !comparison.getPathInLocalRepo().toFile().isDirectory()) {
            return DiffType.NEW;

        } else if (comparison.getPathInLocalRepo().toFile().isDirectory()) {
            return DiffType.FOLDER;

        } else if (contentsEqual(comparison)) {
            return DiffType.SAME;

        } else {
            // TODO: Consolidate this kind of diffing logic with the stuff in CommitChangeHandler?
            FileEntryWithPatchedContents commitContents = db.getFile(comparison.getEntryInCommit().getId());
            FileRandomAccessBytes localFileContents = FileRandomAccessBytes.of(
                    comparison.getPathInLocalRepo().toFile());

            TextOrBinaryChecker.TextOrBinaryResult localTextCheck = TextOrBinaryChecker.check(localFileContents);
            TextOrBinaryChecker.TextOrBinaryResult commitTextCheck = TextOrBinaryChecker.check(
                    commitContents.getContents());

            if (localTextCheck.isText() != commitTextCheck.isText()) {
                return DiffType.NEW;
            } else if (localTextCheck.isText()) {
                return DiffType.TEXT_DIFFERENCE;
            } else {
                return DiffType.BINARY_DIFFERENCE;
            }
        }
    }


    private String getBinaryDiff(LocalComparisonToCommit comparison) throws IOException {
        FileEntryWithPatchedContents commitContents = db.getFile(comparison.getEntryInCommit().getId());
        FileRandomAccessBytes localFileContents = FileRandomAccessBytes.of(comparison.getPathInLocalRepo().toFile());
        return String.format(
                "%d bytes -> %d bytes",
                commitContents.getContents().length(),
                localFileContents.length());
    }


    private boolean contentsEqual(LocalComparisonToCommit comparison) throws IOException {
        FileEntryWithPatchedContents commitContents = db.getFile(comparison.getEntryInCommit().getId());
        String fileHash = HdHasher.hashLocalFile(comparison.getPathInLocalRepo());
        return (commitContents.getId().equals(fileHash));
    }


    void printFinalMessage() {
        if (totalDifferences == 0) {
            LOG.info("No changes.");
        }
    }
}
