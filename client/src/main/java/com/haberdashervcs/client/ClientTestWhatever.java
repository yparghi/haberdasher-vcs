package com.haberdashervcs.client;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.MessageDigest;

import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.FileEntryWithRawContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDb;
import com.haberdashervcs.common.diff.git.GitDeltaDiffer;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import org.eclipse.jgit.internal.storage.pack.BinaryDelta;


class ClientTestWhatever {

    private static final HdLogger LOG = HdLoggers.create(ClientTestWhatever.class);


    public static void main(String[] args) throws Exception {
        //testDiffs();
        testPathMatcher();
    }


    private static void testPathMatcher() {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        LOG.info("Result 1: %s", matcher.matches(Paths.get("/bluh.txt")));
        LOG.info("Result 2: %s", matcher.matches(Paths.get("bluh.txt")));
        LOG.info("Result 3: %s", matcher.matches(Paths.get("/folder/bluh.txt")));
        LOG.info("Result 4: %s", matcher.matches(Paths.get("folder/bluh.txt")));
    }


    public static void testDiffs() throws Exception {
        RepoConfig config = RepoConfig.find().get();
        LocalDb db = SqliteLocalDb.inRepo(config);
        db.startTransaction();

        FileEntryWithPatchedContents original = db.getFile("227733d7f10e656d78ef3849ca6ceb46b413b6412998dffeeab95a6bfae0da3c");
        byte[] originalBytes = RandomAccessBytes.toByteArray(original.getContents());
        LOG.info("Original bytes length is %d", originalBytes.length);
        String originalHash = hashBytes(originalBytes);
        LOG.info("Original hash is %s",  originalHash);

        FileEntryWithPatchedContents modified = db.getFile("9da487d61980bc2d236e9d63c7c981a75b5f1ab7f596e058d4484aafbd00ffe6");
        byte[] modifiedBytes = RandomAccessBytes.toByteArray(modified.getContents());
        LOG.info("modified bytes length is %d", modifiedBytes.length);
        String modifiedHash = hashBytes(modifiedBytes);
        LOG.info("Modified hash is %s",  modifiedHash);

        RandomAccessBytes correctModified = FileRandomAccessBytes.of(new File("debugfile.txt"));
        byte[] correctModifiedBytes = RandomAccessBytes.toByteArray(correctModified);
        LOG.info("correctModified bytes length is %d", correctModifiedBytes.length);
        String correctModifiedHash = hashBytes(correctModifiedBytes);
        LOG.info("correctModified hash is %s",  correctModifiedHash);


        // Try the jgit patch directly, from the rabs
        byte[] gitDelta = GitDeltaDiffer.computeGitDiff(original.getContents(), correctModified);
        LOG.info("gitDelta hash is %s", hashBytes(gitDelta));
        FileEntryWithRawContents modifiedRawDelta = db.getFileWithRawContents("9da487d61980bc2d236e9d63c7c981a75b5f1ab7f596e058d4484aafbd00ffe6").get();
        LOG.info("modifiedRawDelta hash is %s", hashBytes(RandomAccessBytes.toByteArray(modifiedRawDelta.getContents())));


        byte[] gitPatched = BinaryDelta.apply(originalBytes, gitDelta);
        LOG.info("gitPatched hash is %s", hashBytes(gitPatched));

        // Now try HD patched view directly, from the rabs, for a direct comparison to jgit
        /*
        List<com.haberdashervcs.common.diff.git.BinaryDelta.PatchMapping> hdPatches = com.haberdashervcs.common.diff.git.BinaryDelta.computePatchMappings(
                ByteArrayRandomAccessBytes.of(gitDelta));
        LOG.info("Got hd patch mappings: %s", hdPatches);
        PatchedViewRandomAccessBytes patchedView = PatchedViewRandomAccessBytes.build(
                original.getContents(), ImmutableList.of(ByteArrayRandomAccessBytes.of(gitDelta)));
        byte[] patchedViewBytes = RandomAccessBytes.toByteArray(patchedView);
        LOG.info("patchedView hash is %s", hashBytes(patchedViewBytes));
         */
    }


    private static String hashBytes(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        byte[] hash = digest.digest();
        return String.format("%064x", new BigInteger(1, hash));
    }

}
