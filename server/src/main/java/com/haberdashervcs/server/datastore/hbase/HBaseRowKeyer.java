package com.haberdashervcs.server.datastore.hbase;

import java.nio.charset.StandardCharsets;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.MergeLock;


public final class HBaseRowKeyer {

    private static final HdLogger LOG = HdLoggers.create(HBaseRowKeyer.class);


    public static HBaseRowKeyer forRepo(String org, String repo) {
        return new HBaseRowKeyer(org, repo);
    }


    private final String org;
    private final String repo;

    private HBaseRowKeyer(String org, String repo) {
        this.org = org;
        this.repo = repo;
    }

    public String getOrg() {
        return org;
    }

    public String getRepo() {
        return repo;
    }

    public byte[] forCommit(CommitEntry commit) {
        return String.format("%s:%s:%s:%020d", org, repo, commit.getBranchName(), commit.getCommitId())
                .getBytes(StandardCharsets.UTF_8);
    }

    // TODO: Should the start/stop row prefixes for folder history scans also be implemented in this class?
    public byte[] forFolderAt(String branchName, String path, long commitId) {
        // TODO: Should I trim the leading and trailing slash, just to save a little space?
        String key = String.format(
                "%s:%s:%s:%s:%020d", org, repo, branchName, path, commitId);
        return key.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] forFile(String fileId) {
        return String.format("%s:%s:%s", org, repo, fileId).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] prefixForMergeLocksAtTimestamp(long timestampMillis) {
        return String.format("%s:%s:%d", org, repo, timestampMillis).getBytes(StandardCharsets.UTF_8);

    }

    public byte[] forMergeByTimestamp(MergeLock lock) {
        return String.format("%s:%s:%d:%s", org, repo, lock.getTimestampMillis(), lock.getId())
                .getBytes(StandardCharsets.UTF_8);
    }

    public byte[] forMergeId(String mergeLockId) {
        return String.format("%s:%s:ID_%s", org, repo, mergeLockId)
                .getBytes(StandardCharsets.UTF_8);
    }

    public byte[] forBranch(String branchName) {
        return String.format("%s:%s:%s", org, repo, branchName).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] forRepoEntry() {
        return String.format("%s:%s", org, repo).getBytes(StandardCharsets.UTF_8);
    }
}
