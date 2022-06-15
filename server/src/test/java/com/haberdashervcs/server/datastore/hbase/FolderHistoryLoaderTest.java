package com.haberdashervcs.server.datastore.hbase;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class FolderHistoryLoaderTest {

    private static final HdLogger LOG = HdLoggers.create(FolderHistoryLoaderTest.class);

    private static final String ORG = "test_org";
    private static final String REPO = "test_repo";
    private static final String BRANCH = "test_branch";

    private Connection conn;
    private Admin admin;
    private HBaseRawHelper helper;
    private HBaseRowKeyer rowKeyer;
    private long nowTs;
    private MergeStates mergeStates;


    @Before
    public void setUp() throws Exception {
        nowTs = System.currentTimeMillis();
        mergeStates = MergeStates.fromPastSeconds(nowTs, TimeUnit.HOURS.toSeconds(1), helper, rowKeyer);

        Configuration conf = HBaseConfiguration.create();
        conf.clear();

        conn = ConnectionFactory.createConnection(conf);
        admin = conn.getAdmin();

        createTables();

        helper = HBaseRawHelper.forConnection(conn);
        rowKeyer = HBaseRowKeyer.forRepo(ORG, REPO);
    }

    private void createTables() throws Exception {
        LOG.info("Creating test tables.");

        clearTables();

        TableDescriptor filesTableDesc = TableDescriptorBuilder
                .newBuilder(TableName.valueOf("Files"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                .build();
        admin.createTable(filesTableDesc);

        TableDescriptor foldersTableDesc = TableDescriptorBuilder
                .newBuilder(TableName.valueOf("Folders"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                .build();
        admin.createTable(foldersTableDesc);

        TableDescriptor mergesTableDesc = TableDescriptorBuilder
                .newBuilder(TableName.valueOf("Merges"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                .build();
        admin.createTable(mergesTableDesc);
    }

    private void clearTables() throws Exception {
        for (String tableName : Arrays.asList("Files", "Folders", "Merges")) {
            if (admin.tableExists(TableName.valueOf(tableName))) {
                admin.disableTable(TableName.valueOf(tableName));
                admin.deleteTable(TableName.valueOf(tableName));
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        clearTables();
    }


    @Test
    public void loadFromMain() throws Exception {
        HBaseRawHelper helper = HBaseRawHelper.forConnection(conn);
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(ORG, REPO);

        TreeMaker tree = TreeMaker.ofMerged(
                ORG, REPO, BRANCH, 123, helper)
                .addFile("/some/path/file.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing result = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, result.getEntries().size());

        FolderListing resultLaterCommit = historyLoader.getFolderAtCommit(124, "/some/path").get();
        assertEquals(1, resultLaterCommit.getEntries().size());
    }

    @Test
    public void loadFromBranch() throws Exception {
        HBaseRawHelper helper = HBaseRawHelper.forConnection(conn);
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(ORG, REPO);

        TreeMaker tree = TreeMaker.ofMerged(
                ORG, REPO, BRANCH, 123, helper)
                .addFile("/some/path/file.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing result = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, result.getEntries().size());

        FolderListing resultLaterCommit = historyLoader.getFolderAtCommit(124, "/some/path").get();
        assertEquals(1, resultLaterCommit.getEntries().size());
    }

    @Test
    // - Branch at 123 loads a recent branch folder listing
    // - Branch at 122 loads a listing from main
    public void loadFromBranchHistoryThatReachesIntoMain() throws Exception {
        HBaseRawHelper helper = HBaseRawHelper.forConnection(conn);
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(ORG, REPO);

        TreeMaker oldTree = TreeMaker.ofMerged(
                ORG, REPO, "main", 122, helper)
                .addFile("/some/path/older.txt", "file contents")
                .write();

        TreeMaker newTree = TreeMaker.ofMerged(
                ORG, REPO, BRANCH, 123, helper)
                .addFile("/some/path/newer.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing resultNewer = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, resultNewer.getEntries().size());
        assertEquals("newer.txt", resultNewer.getEntries().get(0).getName());

        FolderListing resultOlder = historyLoader.getFolderAtCommit(122, "/some/path").get();
        assertEquals(1, resultOlder.getEntries().size());
        assertEquals("older.txt", resultOlder.getEntries().get(0).getName());
    }

    @Test
    public void loadFromBranchWithMergeInProgress() throws Exception {
        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.IN_PROGRESS, nowTs);
        helper.putMerge(rowKeyer, lock);

        TreeMaker oldTree = TreeMaker.ofMerged(ORG, REPO, "main", 122, helper)
                .addFile("/some/path/older.txt", "file contents")
                .write();

        TreeMaker newTree = TreeMaker.withMergeLock(ORG, REPO, BRANCH, 123, helper, lock)
                .addFile("/some/path/newer.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing resultOlder = historyLoader.getFolderAtCommit(122, "/some/path").get();
        assertEquals(1, resultOlder.getEntries().size());
        assertEquals("older.txt", resultOlder.getEntries().get(0).getName());

        FolderListing resultNewer = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, resultNewer.getEntries().size());
        // Unchanged, because the merge isn't complete.
        assertEquals("older.txt", resultNewer.getEntries().get(0).getName());
    }

    @Test
    public void loadFromBranchWithFailedMerge() throws Exception {
        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.FAILED, nowTs);
        helper.putMerge(rowKeyer, lock);

        TreeMaker oldTree = TreeMaker.ofMerged(ORG, REPO, "main", 122, helper)
                .addFile("/some/path/older.txt", "file contents")
                .write();

        TreeMaker newTree = TreeMaker.withMergeLock(ORG, REPO, BRANCH, 123, helper, lock)
                .addFile("/some/path/newer.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing resultOlder = historyLoader.getFolderAtCommit(122, "/some/path").get();
        assertEquals(1, resultOlder.getEntries().size());
        assertEquals("older.txt", resultOlder.getEntries().get(0).getName());

        FolderListing resultNewer = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, resultNewer.getEntries().size());
        // Unchanged, because the merge isn't complete.
        assertEquals("older.txt", resultNewer.getEntries().get(0).getName());
    }

    @Test
    public void loadFromBranchAfterSuccessfulMerge() throws Exception {
        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.COMPLETED, nowTs);
        helper.putMerge(rowKeyer, lock);

        TreeMaker oldTree = TreeMaker.ofMerged(ORG, REPO, "main", 122, helper)
                .addFile("/some/path/older.txt", "file contents")
                .write();

        TreeMaker newTree = TreeMaker.withMergeLock(ORG, REPO, BRANCH, 123, helper, lock)
                .addFile("/some/path/newer.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, BRANCH, helper, mergeStates);

        FolderListing resultOlder = historyLoader.getFolderAtCommit(122, "/some/path").get();
        assertEquals(1, resultOlder.getEntries().size());
        assertEquals("older.txt", resultOlder.getEntries().get(0).getName());

        FolderListing resultNewer = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, resultNewer.getEntries().size());
        assertEquals("newer.txt", resultNewer.getEntries().get(0).getName());
    }

    @Test
    public void loadFromMainAfterSuccessfulMerge() throws Exception {
        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.COMPLETED, nowTs);
        helper.putMerge(rowKeyer, lock);

        TreeMaker oldTree = TreeMaker.ofMerged(ORG, REPO, "main", 122, helper)
                .addFile("/some/path/older.txt", "file contents")
                .write();

        // We assume the merge code would write the folder history at 123 into the main history.
        TreeMaker newTree = TreeMaker.withMergeLock(ORG, REPO, "main", 123, helper, lock)
                .addFile("/some/path/newer.txt", "file contents")
                .write();

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, "main", helper, mergeStates);

        FolderListing resultOlder = historyLoader.getFolderAtCommit(122, "/some/path").get();
        assertEquals(1, resultOlder.getEntries().size());
        assertEquals("older.txt", resultOlder.getEntries().get(0).getName());

        FolderListing resultNewer = historyLoader.getFolderAtCommit(123, "/some/path").get();
        assertEquals(1, resultNewer.getEntries().size());
        assertEquals("newer.txt", resultNewer.getEntries().get(0).getName());
    }
}
