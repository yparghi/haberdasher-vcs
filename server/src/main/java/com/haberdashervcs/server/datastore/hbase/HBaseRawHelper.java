package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;
import com.haberdashervcs.common.objects.RepoEntry;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.protobuf.ReposProto;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.server.browser.RepoBrowser;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.CheckAndMutate;
import org.apache.hadoop.hbase.client.CheckAndMutateResult;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


/**
 * Low-level logic for HBase gets and puts.
 */
// TODO: Consider visibility. I leave it public for hacking, but maybe I shouldn't.
public final class HBaseRawHelper {

    private static final HdLogger LOG = HdLoggers.create(HBaseRawHelper.class);

    public static HBaseRawHelper forConnection(Connection conn) {
        return new HBaseRawHelper(conn);
    }


    private final Connection conn;
    // TODO pass/configure this
    private final HdObjectByteConverter byteConv = ProtobufObjectByteConverter.getInstance();

    private HBaseRawHelper(Connection conn) {
        this.conn = conn;
    }


    Optional<FileEntry> getFileMaybe(final byte[] rowKey) throws IOException {
        final Table filesTable = conn.getTable(TableName.valueOf("Files"));
        final String columnFamilyName = "cfMain";

        Get get = new Get(rowKey);
        Result result = filesTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        byte[] fileValue = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes("contents"));
        return Optional.of(byteConv.fileFromBytes(fileValue));
    }

    FileEntry getFile(final byte[] rowKey) throws IOException {
        return getFileMaybe(rowKey).get();
    }


    // TODO: What if the file already exists? Should I use a CheckAndMutate if-not-exists here, and let callers make
    //     sure the file is new?
    void putFile(final byte[] rowKey, FileEntry fileEntry) throws IOException {
        LOG.debug(
                "TEMP: Putting file: %s / %s",
                new String(rowKey, StandardCharsets.UTF_8),
                fileEntry.getDebugString());

        final Table filesTable = conn.getTable(TableName.valueOf("Files"));
        final String columnFamilyName = "cfMain";
        final String columnName = "contents";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                byteConv.fileToBytes(fileEntry));
        filesTable.put(put);
    }


    void putFolderAllowingOverwrite(final byte[] rowKey, FolderListing folderListing) throws IOException {
        putFolder(rowKey, folderListing, true);
    }


    void putFolderIfNotExists(final byte[] rowKey, FolderListing folderListing) throws IOException {
        putFolder(rowKey, folderListing, false);
    }


    private void putFolder(final byte[] rowKey, FolderListing folderListing, boolean allowOverwrite) throws IOException {
        LOG.debug("putFolder: Writing FolderListing: %s", folderListing.getDebugString());

        final Table foldersTable = conn.getTable(TableName.valueOf("Folders"));
        final String columnFamilyName = "cfMain";
        final String columnName = "listing";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                byteConv.folderToBytes(folderListing));

        if (allowOverwrite) {
            foldersTable.put(put);

        } else {
            putIfNotExists(rowKey, columnFamilyName, columnName, put, foldersTable);
        }
    }


    // TODO: Simplify this to create the Put inline, given bytes? Put it somewhere common?
    private void putIfNotExists(
            byte[] rowKey, String cfName, String columnName, Put put, Table table) throws IOException {
        CheckAndMutate cAndM = CheckAndMutate.newBuilder(rowKey)
                .ifNotExists(Bytes.toBytes(cfName), Bytes.toBytes(columnName))
                .build(put);

        CheckAndMutateResult result = table.checkAndMutate(cAndM);
        if (!result.isSuccess()) {
            throw new IOException("CheckAndMutate failed");
        }
    }


    void putCommit(final byte[] rowKey, CommitEntry commitEntry) throws IOException {
        final Table commitsTable = conn.getTable(TableName.valueOf("Commits"));
        final String columnFamilyName = "cfMain";
        final String columnName = "entry";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                byteConv.commitToBytes(commitEntry));
        commitsTable.put(put);
    }


    // TODO! Consoliate all these folder lookup APIs. There are just too many of them.
    Optional<FolderListing> getMergedFolderAtCommit(
            String org,
            String repo,
            String branchName,
            long commitId,
            String path,
            MergeStates mergeStates)
            throws IOException {
        final Table historyTable = conn.getTable(TableName.valueOf("Folders"));
        final String columnFamilyName = "cfMain";
        final String columnName = "listing";

        String startRow = folderListingRowKey(org, repo, branchName, path, commitId);
        String stopRow = folderListingRowKey(org, repo, branchName, path, 0);
        LOG.debug("getHeadAtCommit scan: start / stop: %s / %s", startRow, stopRow);

        Scan scan = new Scan()
                .setReversed(true)
                .withStartRow(startRow.getBytes(StandardCharsets.UTF_8), /* inclusive = */ true)
                .withStopRow(stopRow.getBytes(StandardCharsets.UTF_8), true);

        ResultScanner scanner = historyTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] rowBytes = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            FolderListing listing = byteConv.folderFromBytes(rowBytes);

            if (!listing.getMergeLockId().isPresent()) {
                scanner.close();
                return Optional.of(listing);

            } else {
                String mergeId = listing.getMergeLockId().get();
                MergeLock lock = mergeStates.forMergeLockId(mergeId);
                if (lock.getState() == MergeLock.State.COMPLETED) {
                    scanner.close();
                    return Optional.of(listing);
                }
            }
        }

        // Nothing on the branch? Then look for the head commit on main.
        if (!branchName.equals("main")) {
            return getMergedFolderAtCommit(org, repo, "main", commitId, path, mergeStates);
        } else {
            scanner.close();
            return Optional.empty();
        }
    }


    private String folderListingRowKey(String org, String repo, String branchName, String path, long commitId) {
        // IDEA: Format the long as base-256 bytes? It would save space in row keys, but it's a big migration.
        //
        // 20 digits is long enough to hold the base-10 value of the max unsigned long: 18,446,744,073,709,551,615
        return String.format(
                "%s:%s:%s:%s:%020d",
                org, repo, branchName, path, commitId);
    }


    /**
     * For use by raw merging code. Returns newest to oldest.
     */
    List<FolderListing> getListingsSinceCommitIgnoringMergeLocks(
            String org, String repo, String branch, long sinceCommitIdExclusive, String path)
            throws IOException {
        final Table historyTable = conn.getTable(TableName.valueOf("Folders"));
        final String columnFamilyName = "cfMain";
        final String columnName = "listing";

        String startRow = folderListingRowKey(org, repo, branch, path, Long.MAX_VALUE);
        String stopRow = folderListingRowKey(org, repo, branch, path, sinceCommitIdExclusive);

        LOG.debug("History scan: start / stop: %s / %s", startRow, stopRow);
        Scan scan = new Scan()
                .setReversed(true)
                .withStartRow(startRow.getBytes(StandardCharsets.UTF_8), /* inclusive = */ true)
                .withStopRow(stopRow.getBytes(StandardCharsets.UTF_8), false);

        List<FolderListing> out = new ArrayList<>();
        ResultScanner scanner = historyTable.getScanner(scan);

        Result result;
        while ((result = scanner.next()) != null) {
            byte[] rowBytes = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            out.add(byteConv.folderFromBytes(rowBytes));
        }

        scanner.close();
        return out;
    }


    List<CommitEntry> getCommitsDescendingFrom(
            String org, String repo, String branchName, long maxCommitId, int batchSize) {
        try {
            // TODO: Remove redundancy w/ HBaseRowKeyer.
            byte[] maxCommitRowKey = String.format(
                    "%s:%s:%s:%020d", org, repo, branchName, maxCommitId)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] endingCommitRowKey = String.format(
                    "%s:%s:%s:%020d", org, repo, branchName, 0)
                    .getBytes(StandardCharsets.UTF_8);

            final Table historyTable = conn.getTable(TableName.valueOf("Commits"));
            final String columnFamilyName = "cfMain";
            final String columnName = "entry";

            Scan scan = new Scan()
                    .setReversed(true)
                    .withStartRow(maxCommitRowKey, true /* inclusive */)
                    .withStopRow(endingCommitRowKey, true /* inclusive */);
            if (batchSize > 0) {
                scan = scan.setLimit(batchSize);
            }

            ImmutableList.Builder<CommitEntry> out = ImmutableList.builder();
            ResultScanner scanner = historyTable.getScanner(scan);
            Result result;
            while ((result = scanner.next()) != null) {
                byte[] rowBytes = result.getValue(
                        Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
                out.add(byteConv.commitFromBytes(rowBytes));
            }

            return out.build();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Also writes the initial repo size entry.
     */
    void putRepoEntryIfNotExists(RepoEntry repoEntry) throws IOException {
        LOG.info("Creating repo: %s / %s", repoEntry.getOrg(), repoEntry.getRepoName());
        final Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        final String cfMain = "cfMain";
        final String entryColumnName = "repo";
        final String sizeColumnName = "repoSize";

        String org = repoEntry.getOrg();
        String repo = repoEntry.getRepoName();
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        byte[] rowKey = rowKeyer.forRepoEntry();
        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(cfMain),
                Bytes.toBytes(entryColumnName),
                byteConv.repoEntryToBytes(repoEntry));
        put.addColumn(
                Bytes.toBytes(cfMain),
                Bytes.toBytes(sizeColumnName),
                Bytes.toBytes(0L));
        putIfNotExists(rowKey, cfMain, entryColumnName, put, reposTable);


        long freeTrialStartDateMillis = System.currentTimeMillis();
        ReposProto.BillingState initialBillingState = ReposProto.BillingState.newBuilder()
                .setType("new")
                .putFields("freeTrialStartDate", Long.toString(freeTrialStartDateMillis))
                .build();

        OrgSubscription newOrgSub = OrgSubscription.of(
                org,
                OrgSubscription.State.ON_FREE_TRIAL,
                Collections.emptyList(),
                OrgSubscription.BillingPlan.FREE_TRIAL,
                initialBillingState);

        byte[] orgSubRowKey = String.format(":SUBSCRIPTION:%s", org).getBytes(StandardCharsets.UTF_8);
        Put orgSubPut = new Put(orgSubRowKey);
        orgSubPut.addColumn(
                Bytes.toBytes(cfMain),
                Bytes.toBytes("subscription"),
                byteConv.subscriptionToBytes(newOrgSub));
        putIfNotExists(orgSubRowKey, cfMain, "subscription", orgSubPut, reposTable);
    }


    Optional<RepoEntry> getRepoEntry(String org, String repo) throws IOException {
        final Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        final String columnFamilyName = "cfMain";
        final String columnName = "repo";

        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        byte[] rowKey = rowKeyer.forRepoEntry();
        Get get = new Get(rowKey);
        Result result = reposTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        } else {
            byte[] rowValue = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            return Optional.of(byteConv.repoEntryFromBytes(rowValue));
        }
    }


    OrgSubscription.WithOriginalBytes getSubscription(String org) throws IOException {
        Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        String columnFamilyName = "cfMain";
        String columnName = "subscription";

        byte[] rowKey = String.format(":SUBSCRIPTION:%s", org).getBytes(StandardCharsets.UTF_8);
        Get get = new Get(rowKey);
        Result result = reposTable.get(get);
        if (result.isEmpty()) {
            throw new IllegalStateException("No subscription found for org: " + org);
        } else {
            byte[] rowValue = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            return OrgSubscription.WithOriginalBytes.of(byteConv.subscriptionFromBytes(rowValue), rowValue);
        }
    }


    void updateOrgSubscription(
            OrgSubscription updated, OrgSubscription.WithOriginalBytes original)
            throws IOException {
        Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        String cfMain = "cfMain";
        String subColumn = "subscription";

        byte[] rowKey = String.format(":SUBSCRIPTION:%s", updated.getOrg()).getBytes(StandardCharsets.UTF_8);
        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(cfMain),
                Bytes.toBytes(subColumn),
                byteConv.subscriptionToBytes(updated));

        byte[] originalBytes = original.getOriginalBytes();
        CheckAndMutate cAndM = CheckAndMutate.newBuilder(rowKey)
                .ifEquals(Bytes.toBytes(cfMain), Bytes.toBytes(subColumn), originalBytes)
                .build(put);
        CheckAndMutateResult result = reposTable.checkAndMutate(cAndM);
        if (!result.isSuccess()) {
            throw new IllegalStateException("CheckAndMutate failed for org sub update");
        }
    }


    // TODO: Fix the abstractions here, generally. Passing in a row keyer doesn't make sense.
    long getRepoSize(HBaseRowKeyer rowKeyer) throws IOException {
        byte[] rowKey = rowKeyer.forRepoEntry();
        final Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        final String columnFamilyName = "cfMain";
        final String columnName = "repoSize";

        Get get = new Get(rowKey);
        // TODO: Look into specifying columns for the Get, and test what that means for the null check.
        Result result = reposTable.get(get);
        String notFoundError = String.format(
                "No repo size found for (%s, %s)", rowKeyer.getOrg(), rowKeyer.getRepo());
        if (result.isEmpty()) {
            throw new IllegalStateException(notFoundError);
        } else {
            byte[] rowValue = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            if (rowValue == null) {
                throw new IllegalStateException(notFoundError);
            }
            return Bytes.toLong(rowValue);
        }
    }


    /**
     * Returns the new repo size after the increment.
     */
    // TODO: Fix the abstractions here, generally. Passing in a row keyer doesn't make sense.
    long incrementRepoSize(HBaseRowKeyer rowKeyer, long numBytesToAdd) throws IOException {
        LOG.info("TEMP: increment by %d", numBytesToAdd);
        byte[] rowKey = rowKeyer.forRepoEntry();
        final Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        final String columnFamilyName = "cfMain";
        final String columnName = "repoSize";

        Increment increment = new Increment(rowKey)
                .addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName), numBytesToAdd);
        Result result = reposTable.increment(increment);
        byte[] rowValue = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
        return Bytes.toLong(rowValue);
    }


    // For use with checkAndMutate() to update FolderHistory entries atomically, respecting locks.
    static class FolderListingWithOriginalBytes {
        final FolderListing listing;
        final byte[] originalBytes;

        FolderListingWithOriginalBytes(FolderListing listing, byte[] bytes) {
            this.listing = listing;
            this.originalBytes = bytes;
        }
    }

    List<FolderListingWithOriginalBytes> getMostRecentFoldersOnBranch(
            String org, String repo, String branchName, long atCommitId) throws IOException {
        final Table historyTable = conn.getTable(TableName.valueOf("Folders"));
        final String columnFamilyName = "cfMain";
        final String columnName = "listing";

        final String rowPrefixStr = String.format(
                "%s:%s:%s:",
                org, repo, branchName);
        LOG.debug("All branch history prefix: %s", rowPrefixStr);

        Scan scan = new Scan()
                .setRowPrefixFilter(rowPrefixStr.getBytes(StandardCharsets.UTF_8));

        Map<String, FolderListingWithOriginalBytes> mostRecentSeenPerPath = new HashMap<>();

        ResultScanner scanner = historyTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] rowBytes = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            FolderListing folder = byteConv.folderFromBytes(rowBytes);

            if (folder.getCommitId() > atCommitId) {
                continue;
            }

            if (!mostRecentSeenPerPath.containsKey(folder.getPath())) {
                mostRecentSeenPerPath.put(folder.getPath(), new FolderListingWithOriginalBytes(folder, rowBytes));

            } else {
                FolderListingWithOriginalBytes existing = mostRecentSeenPerPath.get(folder.getPath());
                if (existing.listing.getCommitId() < folder.getCommitId()) {
                    mostRecentSeenPerPath.put(folder.getPath(), new FolderListingWithOriginalBytes(folder, rowBytes));
                }
            }
        }

        scanner.close();
        return ImmutableList.copyOf(mostRecentSeenPerPath.values());
    }


    List<MergeLock> getMerges(byte[] earlierRowKey, byte[] laterRowKey) throws IOException {
        ArrayList<MergeLock> out = new ArrayList<>();
        final Table mergesTable = conn.getTable(TableName.valueOf("Merges"));
        final String columnFamilyName = "cfMain";
        final String columnName = "lockContents";

        // Note HBase scans are *not* snapshots/consistent. But that's fine because I only care that *one* set of merge
        // states, regardless of which ones changed during the scan, is applied consistently to any read operation like
        // checkout.
        Scan scan = new Scan()
                .withStartRow(earlierRowKey)
                .withStopRow(laterRowKey);

        ResultScanner scanner = mergesTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] resultBytes = result.getValue(
                    Bytes.toBytes(columnFamilyName),
                    Bytes.toBytes(columnName));
            out.add(byteConv.mergeLockFromBytes(resultBytes));
        }

        scanner.close();
        return out;
    }


    Optional<MergeLock> getMergeById(HBaseRowKeyer rowKeyer, String mergeLockId) throws IOException {
        final Table mergesTable = conn.getTable(TableName.valueOf("Merges"));
        final String columnFamilyName = "cfMain";
        final String columnName = "lockContents";

        byte[] rowKey = rowKeyer.forMergeId(mergeLockId);
        Get get = new Get(rowKey);
        Result result = mergesTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        } else {
            byte[] rowValue = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            return Optional.of(byteConv.mergeLockFromBytes(rowValue));
        }
    }


    void putMerge(HBaseRowKeyer rowKeyer, MergeLock lock) throws IOException {
        final Table mergesTable = conn.getTable(TableName.valueOf("Merges"));
        final String columnFamilyName = "cfMain";
        final String columnName = "lockContents";

        byte[] rowKeyByTimestamp = rowKeyer.forMergeByTimestamp(lock);
        byte[] rowKeyById = rowKeyer.forMergeId(lock.getId());
        byte[] lockContents = byteConv.mergeLockToBytes(lock);

        Put tsPut = new Put(rowKeyByTimestamp);
        tsPut.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                lockContents);
        mergesTable.put(tsPut);

        Put idPut = new Put(rowKeyById);
        idPut.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                lockContents);
        mergesTable.put(idPut);
    }


    void createBranch(byte[] rowKey, BranchEntry branch) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(branch.getName()), "Empty branch name");
        LOG.info("Creating branch: " + branch.getName());
        final Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        final String columnFamilyName = "cfMain";
        final String columnName = "branch";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                byteConv.branchToBytes(branch));
        putIfNotExists(rowKey, columnFamilyName, columnName, put, branchesTable);
    }


    void createBranchReview(ReviewsProto.ReviewContents reviewContents) throws IOException {
        byte[] rowKey = String.format(
                ":REVIEW:%s:%s:%s:%s:%020d",
                reviewContents.getOrg(),
                reviewContents.getRepo(),
                reviewContents.getThisBranch(),
                reviewContents.getOtherBranch(),
                reviewContents.getOtherBranchCommitId())
                .getBytes(StandardCharsets.UTF_8);

        Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        String columnFamilyName = "cfMain";
        String columnName = "review";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                reviewContents.toByteArray());
        putIfNotExists(rowKey, columnFamilyName, columnName, put, branchesTable);
    }


    void updateBranchReview(
            ReviewsProto.ReviewContents review,
            RepoBrowser.ReviewWithOriginalBytes original)
            throws IOException {
        byte[] rowKey = String.format(
                ":REVIEW:%s:%s:%s:%s:%020d",
                review.getOrg(),
                review.getRepo(),
                review.getThisBranch(),
                review.getOtherBranch(),
                review.getOtherBranchCommitId())
                .getBytes(StandardCharsets.UTF_8);

        Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        String columnFamilyName = "cfMain";
        String columnName = "review";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                review.toByteArray());

        CheckAndMutate cAndM = CheckAndMutate.newBuilder(rowKey)
                .ifEquals(Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName), original.getOriginalBytes())
                .build(put);
        CheckAndMutateResult result = branchesTable.checkAndMutate(cAndM);
        if (!result.isSuccess()) {
            throw new IllegalStateException("CheckAndMutate failed for review update");
        }
    }


    Optional<RepoBrowser.ReviewWithOriginalBytes> getReview(
            String org,
            String repo,
            String thisBranch,
            String otherBranch,
            long otherBranchCommitId)
            throws IOException {
        byte[] rowKey = String.format(
                ":REVIEW:%s:%s:%s:%s:%020d",
                org, repo, thisBranch, otherBranch, otherBranchCommitId)
                .getBytes(StandardCharsets.UTF_8);

        Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        String columnFamilyName = "cfMain";
        String columnName = "review";

        Get get = new Get(rowKey);
        Result result = branchesTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        byte[] rowValue = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
        return Optional.of(new RepoBrowser.ReviewWithOriginalBytes(
                ReviewsProto.ReviewContents.parseFrom(rowValue), rowValue));
    }


    boolean updateBranchIfUnchanged(byte[] rowKey, BranchEntry branch, byte[] originalBytes) throws IOException {
        LOG.info("Updating branch to: %s", branch.getDebugString());
        final Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        final String columnFamilyName = "cfMain";
        final String columnName = "branch";

        Put put = new Put(rowKey);
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                byteConv.branchToBytes(branch));

        CheckAndMutate cAndM = CheckAndMutate.newBuilder(rowKey)
                .ifEquals(Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName), originalBytes)
                .build(put);

        CheckAndMutateResult result = branchesTable.checkAndMutate(cAndM);
        return (result.isSuccess());
    }


    static class BranchWithOriginalBytes {
        BranchEntry branch;
        byte[] originalBytes;
    }


    Optional<BranchEntry> getBranch(final byte[] rowKey) throws IOException {
        Optional<BranchWithOriginalBytes> bb = getBranchWithOriginalBytes(rowKey);
        if (bb.isPresent()) {
            return Optional.of(bb.get().branch);
        } else {
            return Optional.empty();
        }
    }


    Optional<BranchWithOriginalBytes> getBranchWithOriginalBytes(final byte[] rowKey) throws IOException {
        final Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
        final String columnFamilyName = "cfMain";
        final String columnName = "branch";

        Get get = new Get(rowKey);
        Result result = branchesTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        byte[] rowValue = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));

        BranchWithOriginalBytes out = new BranchWithOriginalBytes();
        out.branch = byteConv.branchFromBytes(rowValue);
        out.originalBytes = rowValue;
        return Optional.of(out);
    }


    List<BranchEntry> getAllBranches(String org, String repo) {
        String prefix = String.format("%s:%s:", org, repo);
        ArrayList<BranchEntry> out = new ArrayList<>();

        try {
            final Table branchesTable = conn.getTable(TableName.valueOf("Branches"));
            Scan scan = new Scan()
                    .setRowPrefixFilter(prefix.getBytes(StandardCharsets.UTF_8));
            ResultScanner scanner = branchesTable.getScanner(scan);

            Result result;
            while ((result = scanner.next()) != null) {
                byte[] rowBytes = result.getValue(
                        Bytes.toBytes("cfMain"), Bytes.toBytes("branch"));
                out.add(byteConv.branchFromBytes(rowBytes));
            }

            return out;

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
