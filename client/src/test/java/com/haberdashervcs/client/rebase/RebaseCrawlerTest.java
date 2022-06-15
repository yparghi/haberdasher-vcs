package com.haberdashervcs.client.rebase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDb;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.FolderListing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


// TODO: Add asserts for base & changed file ids on FileChange's.
public class RebaseCrawlerTest {

    private Path repoDir;
    private Path dotHdLocalDir;
    private SqliteLocalDb db;

    @Before
    public void setUp() throws Exception {
        repoDir = Files.createTempDirectory("hd-unit-test-");
        dotHdLocalDir = repoDir.resolve(".hdlocal");
        Files.createDirectories(dotHdLocalDir);
        db = SqliteLocalDb.forTesting(dotHdLocalDir);

        BranchEntry stubMainBranch = BranchEntry.of("main", 0, 1);
        db.init(stubMainBranch);

        db.startTransaction();
    }

    @After
    public void tearDown() throws Exception {
        db.finishTransaction();
    }


    @Test
    public void simpleCrawl() throws Exception {
        db.addGlobalCheckedOutPath("/");

        FolderListing baseRoot = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("subfolder"),
                        FolderListing.Entry.forFile("file1.txt", "xxx"),
                        FolderListing.Entry.forFile("onlyOnMain.txt", "onlyOnMain-id")),
                "/",
                "main",
                1);
        db.putFolder(baseRoot);

        FolderListing baseSub = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subfile1.txt", "subfile1-id"),
                        FolderListing.Entry.forFile("subOnlyOnMain.txt", "subOnlyOnMain-id")),
                "/subfolder/",
                "main",
                1);
        db.putFolder(baseSub);

        FolderListing branchRoot = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("subfolder"),
                        FolderListing.Entry.forFile("file1.txt", "yyy"),
                        FolderListing.Entry.forFile("file2.txt", "zzz")),
                "/",
                "branch",
                1);
        db.putFolder(branchRoot);

        FolderListing branchSub = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subfile1.txt", "subfile1-modified-id"),
                        FolderListing.Entry.forFile("subOnlyOnBranch.txt", "subOnlyOnBranch-id")),
                "/subfolder/",
                "branch",
                1);
        db.putFolder(branchSub);

        RebaseCrawler crawler = new RebaseCrawler(db, "main", 1, 0, "branch", 1, 1);
        List<RebaseCrawler.FileChange> changes = crawler.crawl();

        assertEquals(6, changes.size());

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(0).change);
        assertEquals("/onlyOnMain.txt", changes.get(0).path);

        assertEquals(RebasePathComparison.Change.MODIFIED, changes.get(1).change);
        assertEquals("/file1.txt", changes.get(1).path);

        assertEquals(RebasePathComparison.Change.ADDED, changes.get(2).change);
        assertEquals("/file2.txt", changes.get(2).path);

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(3).change);
        assertEquals("/subfolder/subOnlyOnMain.txt", changes.get(3).path);

        assertEquals(RebasePathComparison.Change.MODIFIED, changes.get(4).change);
        assertEquals("/subfolder/subfile1.txt", changes.get(4).path);

        assertEquals(RebasePathComparison.Change.ADDED, changes.get(5).change);
        assertEquals("/subfolder/subOnlyOnBranch.txt", changes.get(5).path);
    }


    @Test
    public void addAndDeleteFolders() throws Exception {
        db.addGlobalCheckedOutPath("/");

        FolderListing baseRoot = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("subfolderMain"),
                        FolderListing.Entry.forFile("file1.txt", "file1-id"),
                        FolderListing.Entry.forFile("onlyOnMain.txt", "onlyOnMain-id")),
                "/",
                "main",
                1);
        db.putFolder(baseRoot);

        FolderListing baseSub = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subOnlyOnMain.txt", "subOnlyOnMain-id")),
                "/subfolderMain/",
                "main",
                1);
        db.putFolder(baseSub);

        FolderListing branchRoot = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("subfolderBranch"),
                        FolderListing.Entry.forFile("file1.txt", "file1-id"),
                        FolderListing.Entry.forFile("onlyOnBranch.txt", "onlyOnBranch-id")),
                "/",
                "branch",
                1);
        db.putFolder(branchRoot);

        FolderListing branchSub = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subOnlyOnBranch.txt", "subOnlyOnBranch-id")),
                "/subfolderBranch/",
                "branch",
                1);
        db.putFolder(branchSub);

        RebaseCrawler crawler = new RebaseCrawler(db, "main", 1, 0, "branch", 1, 1);
        List<RebaseCrawler.FileChange> changes = crawler.crawl();

        assertEquals(4, changes.size());

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(0).change);
        assertEquals("/onlyOnMain.txt", changes.get(0).path);

        assertEquals(RebasePathComparison.Change.ADDED, changes.get(1).change);
        assertEquals("/onlyOnBranch.txt", changes.get(1).path);

        assertEquals(RebasePathComparison.Change.ADDED, changes.get(2).change);
        assertEquals("/subfolderBranch/subOnlyOnBranch.txt", changes.get(2).path);

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(3).change);
        assertEquals("/subfolderMain/subOnlyOnMain.txt", changes.get(3).path);
    }


    @Test
    public void fileFolderReplacements() throws Exception {
        db.addGlobalCheckedOutPath("/");

        FolderListing baseRoot = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("folderToFile"),
                        FolderListing.Entry.forFile("fileToFolder", "fileToFolder-id")),
                "/",
                "main",
                1);
        db.putFolder(baseRoot);

        FolderListing baseSub = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subfileMain.txt", "subfileMain-id")),
                "/folderToFile/",
                "main",
                1);
        db.putFolder(baseSub);


        // We want to test that the crawler never finds or falls back to a branch folder -- that it only sees the
        // folder -> file replacement as the head change/state. So this branch folder shouldn't appear in the result.
        FolderListing branchRoot1 = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forSubFolder("folderToFile"),
                        FolderListing.Entry.forFile("fileToFolder", "fileToFolder-id")),
                "/",
                "main",
                1);
        db.putFolder(branchRoot1);

        // This subfolder is just a token change on the branch. It shouldn't appear in the result.
        FolderListing branchSub1 = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("subOnlyOnBranch.txt", "subOnlyOnBranch-id")),
                "/folderToFile/",
                "branch",
                1);
        db.putFolder(branchSub1);

        // Now do replacements.
        FolderListing branchRoot2 = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("folderToFile", "folderToFile-asFile-id"),
                        FolderListing.Entry.forSubFolder("fileToFolder")),
                "/",
                "branch",
                2);
        db.putFolder(branchRoot2);

        FolderListing branchSub2 = FolderListing.withoutMergeLock(
                ImmutableList.of(
                        FolderListing.Entry.forFile("fileToFolder-file.txt", "fileToFolder-file-id")),
                "/fileToFolder/",
                "branch",
                2);
        db.putFolder(branchSub2);


        RebaseCrawler crawler = new RebaseCrawler(db, "main", 1, 0, "branch", 2, 1);
        List<RebaseCrawler.FileChange> changes = crawler.crawl();

        assertEquals(4, changes.size());

        // TODO: Figure out how to assert the list is correct regardless of ordering.
        assertEquals(RebasePathComparison.Change.ADDED, changes.get(0).change);
        assertEquals("/folderToFile", changes.get(0).path);

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(1).change);
        assertEquals("/fileToFolder", changes.get(1).path);

        assertEquals(RebasePathComparison.Change.ADDED, changes.get(2).change);
        assertEquals("/fileToFolder/fileToFolder-file.txt", changes.get(2).path);

        assertEquals(RebasePathComparison.Change.DELETED, changes.get(3).change);
        assertEquals("/folderToFile/subfileMain.txt", changes.get(3).path);
    }

}