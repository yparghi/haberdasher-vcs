package com.haberdashervcs.client.localdb.sqlite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Strings;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.objects.BranchEntry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class SqliteLocalDbTest {

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
    }


    @Test
    public void testPutMultipleDiffs() throws Exception {
        // We use long strings to avoid the delta being so small that it just contains the entire new contents.
        String original = Strings.repeat("text1\n", 100);
        String modified1 = original + Strings.repeat("text2\n", 100);
        String modified2 = modified1 + Strings.repeat("text3\n", 100);

        Path filePath = repoDir.resolve("somefile.txt");
        db.startTransaction();

        Files.write(filePath, original.getBytes(StandardCharsets.UTF_8));
        String originalHash = db.putNewFile(filePath);

        Files.write(filePath, modified1.getBytes(StandardCharsets.UTF_8));
        String modified1Hash = HdHasher.hashLocalFile(filePath);
        FileEntryWithPatchedContents originalCommit = db.getFile(originalHash);
        db.putFileHandlingDiffEntries(modified1Hash, filePath, originalCommit.getEntry());

        Files.write(filePath, modified2.getBytes(StandardCharsets.UTF_8));
        String modified2Hash = HdHasher.hashLocalFile(filePath);
        FileEntryWithPatchedContents modified1Commit = db.getFile(modified1Hash);
        db.putFileHandlingDiffEntries(modified2Hash, filePath, modified1Commit.getEntry());


        FileEntryWithPatchedContents modified2SavedFile = db.getFile(modified2Hash);
        byte[] modified2SavedBytes = RandomAccessBytes.toByteArray(modified2SavedFile.getContents());
        String modified2SavedStr = new String(modified2SavedBytes, StandardCharsets.UTF_8);
        Assert.assertEquals(modified2, modified2SavedStr);

        db.finishTransaction();
    }

}