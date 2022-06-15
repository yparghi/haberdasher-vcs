package com.haberdashervcs.server.datastore.hbase;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;
import com.haberdashervcs.common.objects.MergeResult;
import org.apache.hadoop.hbase.client.Connection;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class HBaseMergerTest {

    private static final HdLogger LOG = HdLoggers.create(HBaseMergerTest.class);

    private static final String ORG = "test_org";
    private static final String REPO = "test_repo";
    private static final String BRANCH = "test_branch";


    private Connection conn;
    private HBaseRawHelper helper;
    private HBaseRowKeyer rowKeyer;
    private long nowTs;
    private MergeStates mergeStates;

    @Before
    public void setUp() throws Exception {
        conn = HBaseTestingUtils.getTestConn();
        HBaseTestingUtils.resetTables();

        helper = HBaseRawHelper.forConnection(conn);
        rowKeyer = HBaseRowKeyer.forRepo(ORG, REPO);

        nowTs = System.currentTimeMillis();
        mergeStates = MergeStates.fromPastSeconds(nowTs, TimeUnit.HOURS.toSeconds(1), helper, rowKeyer);
    }


    @Test
    public void mergeSimple() throws Exception {
        BranchEntry main = BranchEntry.of("main", -1, 100);
        helper.createBranch(rowKeyer.forBranch("main"), main);

        // TODO! Pushes should update the head commit id on the branch. I'm cheating and setting it here.
        BranchEntry branch = BranchEntry.of(BRANCH, 100, 101);
        helper.createBranch(rowKeyer.forBranch(BRANCH), branch);

        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.IN_PROGRESS, nowTs);
        helper.putMerge(rowKeyer, lock);

        ArrayList<FolderListing.Entry> entries = new ArrayList<>();
        entries.add(FolderListing.Entry.forFile("older.txt", "fileId_older"));

        FolderListing older = FolderListing.withoutMergeLock(entries, "/some/path", "main", 100);
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("main", older.getPath(), older.getCommitId()),
                older);

        entries.add(FolderListing.Entry.forFile("newer.txt", "fileId_newer"));
        FolderListing newer = FolderListing.withMergeLock(entries, "/some/path", BRANCH, 101, lock.getId());
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt(BRANCH, newer.getPath(), newer.getCommitId()),
                newer);

        HBaseMerger merger = new HBaseMerger();
        MergeResult result = merger.merge(ORG, REPO, BRANCH, 101, helper);

        assertEquals(MergeResult.ResultType.SUCCESSFUL, result.getResultType());
        assertEquals(101, result.getNewCommitIdOnMain());

        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, "main", helper, mergeStates);

        FolderListing afterMerge = historyLoader.getFolderAtCommit(result.getNewCommitIdOnMain(), "/some/path").get();
        assertEquals(2, afterMerge.getEntries().size());
    }


    @Test
    public void mergeFailsWithConflict() throws Exception {
        BranchEntry main = BranchEntry.of("main", -1, 100);
        helper.createBranch(rowKeyer.forBranch("main"), main);

        // TODO! Pushes should update the head commit id on the branch. I'm cheating and setting it here.
        BranchEntry branch1 = BranchEntry.of("branch1", 100, 101);
        helper.createBranch(rowKeyer.forBranch("branch1"), branch1);

        BranchEntry branch2 = BranchEntry.of("branch2", 100, 101);
        helper.createBranch(rowKeyer.forBranch("branch2"), branch2);

        MergeLock lock1 = MergeLock.of("merge1", "branch1", MergeLock.State.IN_PROGRESS, nowTs);
        helper.putMerge(rowKeyer, lock1);

        MergeLock lock2 = MergeLock.of("merge2", "branch2", MergeLock.State.IN_PROGRESS, nowTs);
        helper.putMerge(rowKeyer, lock2);


        ArrayList<FolderListing.Entry> entries = new ArrayList<>();
        entries.add(FolderListing.Entry.forFile("base.txt", "fileId_base"));

        FolderListing base = FolderListing.withoutMergeLock(entries, "/some/path", "main", 100);
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("main", base.getPath(), base.getCommitId()),
                base);


        entries.add(FolderListing.Entry.forFile("newer1.txt", "fileId_newer1"));
        FolderListing newer1 = FolderListing.withMergeLock(entries, "/some/path", "branch1", 101, lock1.getId());
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("branch1", newer1.getPath(), newer1.getCommitId()),
                newer1);

        entries.add(FolderListing.Entry.forFile("newer2.txt", "fileId_newer2"));
        FolderListing newer2 = FolderListing.withMergeLock(entries, "/some/path", "branch2", 101, lock2.getId());
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("branch2", newer2.getPath(), newer2.getCommitId()),
                newer2);


        HBaseMerger merger1 = new HBaseMerger();
        MergeResult result1 = merger1.merge(ORG, REPO, "branch1", 101, helper);
        assertEquals(MergeResult.ResultType.SUCCESSFUL, result1.getResultType());
        assertEquals(101, result1.getNewCommitIdOnMain());

        HBaseMerger merger2 = new HBaseMerger();
        MergeResult result2 = merger2.merge(ORG, REPO, "branch2", 101, helper);
        // NOTE: This *should* fail because the head commit on /some/path is now 101, not 100 (the base commit id for
        //     this merge).
        assertEquals(MergeResult.ResultType.FAILED, result2.getResultType());


        FolderHistoryLoader historyLoader = FolderHistoryLoader.forBranch(rowKeyer, "main", helper, mergeStates);
        FolderListing afterMerge = historyLoader.getFolderAtCommit(result1.getNewCommitIdOnMain(), "/some/path").get();
        assertEquals(2, afterMerge.getEntries().size());
        assertEquals("base.txt", afterMerge.getEntries().get(0).getName());
        assertEquals("newer1.txt", afterMerge.getEntries().get(1).getName());
    }


    @Test
    public void mergeSecondTimeFails() throws Exception {
        BranchEntry main = BranchEntry.of("main", -1, 100);
        helper.createBranch(rowKeyer.forBranch("main"), main);

        // TODO! Pushes should update the head commit id on the branch. I'm cheating and setting it here.
        BranchEntry branch = BranchEntry.of(BRANCH, 100, 101);
        helper.createBranch(rowKeyer.forBranch(BRANCH), branch);

        MergeLock lock = MergeLock.of("some-merge-id", "test-branch", MergeLock.State.IN_PROGRESS, nowTs);
        helper.putMerge(rowKeyer, lock);

        ArrayList<FolderListing.Entry> entries = new ArrayList<>();
        entries.add(FolderListing.Entry.forFile("older.txt", "fileId_older"));

        FolderListing older = FolderListing.withoutMergeLock(entries, "/some/path", "main", 100);
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("main", older.getPath(), older.getCommitId()),
                older);

        entries.add(FolderListing.Entry.forFile("newer.txt", "fileId_newer"));
        FolderListing newer = FolderListing.withMergeLock(entries, "/some/path", BRANCH, 101, lock.getId());
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt(BRANCH, newer.getPath(), newer.getCommitId()),
                newer);

        HBaseMerger merger = new HBaseMerger();
        MergeResult result = merger.merge(ORG, REPO, BRANCH, 101, helper);

        assertEquals(MergeResult.ResultType.SUCCESSFUL, result.getResultType());
        assertEquals(101, result.getNewCommitIdOnMain());


        HBaseMerger mergerRepeated = new HBaseMerger();
        MergeResult resultRepeated = mergerRepeated.merge(ORG, REPO, BRANCH, 101, helper);
        assertEquals(MergeResult.ResultType.FAILED, resultRepeated.getResultType());
    }
}
