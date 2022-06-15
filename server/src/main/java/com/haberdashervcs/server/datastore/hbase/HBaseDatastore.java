package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.HdObjectInputStream;
import com.haberdashervcs.common.io.HdObjectOutputStream;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.objects.RepoEntry;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.protobuf.ServerProto;
import com.haberdashervcs.common.rules.HdNameRules;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdDatastore;
import com.haberdashervcs.server.datastore.HdLargeFileStore;
import com.haberdashervcs.server.operations.checkout.CheckoutResult;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;


public final class HBaseDatastore implements HdDatastore {

    private static HdLogger LOG = HdLoggers.create(HBaseDatastore.class);


    public static HBaseDatastore forConnection(Connection conn, HdLargeFileStore largeFileStore) {
        return new HBaseDatastore(conn, largeFileStore);
    }


    private final Connection conn;
    private final HBaseRawHelper helper;
    private final HdLargeFileStore largeFileStore;

    private HBaseDatastore(Connection conn, HdLargeFileStore largeFileStore) {
        this.conn = conn;
        this.helper = HBaseRawHelper.forConnection(conn);
        this.largeFileStore = largeFileStore;
    }


    @Override
    public void createRepo(String org, String repo) throws Exception {
        // TODO: Separate org names.
        if (!org.equals(repo)) {
            throw new IllegalArgumentException("Unexpected naming error!");
        }

        List<String> nameErrors = HdNameRules.validateRepoName(repo);
        if (!nameErrors.isEmpty()) {
            throw new IllegalArgumentException("Invalid repo name: " + String.join(";", nameErrors));
        }

        HBaseRawHelper helper = HBaseRawHelper.forConnection(conn);
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);

        RepoEntry repoEntry = RepoEntry.of(org, repo);
        helper.putRepoEntryIfNotExists(repoEntry);

        BranchEntry main = BranchEntry.of("main", 1, 1);
        helper.createBranch(rowKeyer.forBranch("main"), main);

        FolderListing rootFolder = FolderListing.withoutMergeLock(
                ImmutableList.of(), "/", "main", 1);
        helper.putFolderIfNotExists(
                rowKeyer.forFolderAt("main", rootFolder.getPath(), rootFolder.getCommitId()),
                rootFolder);
    }


    @Override
    public ServerCheckoutSpec computeCheckout(
            String org, String repo, String branchName, long commitId, CheckoutPathSet paths)
            throws Exception {

        // TODO: Fix cyclic dependency b/w this class and HBaseCheckoutHandler.
        HBaseCheckoutHandler checkoutHandler = new HBaseCheckoutHandler(
                org, repo, this, helper, largeFileStore, paths);
        return checkoutHandler.computeCheckout(org, repo, branchName, commitId);
    }


    @Override
    public ServerProto.PushQueryResponse handlePushQuery(
            ServerProto.PushQuery pushQuery,
            OrgSubscription orgSub)
            throws Exception {
        HBasePushHandler pushHandler = new HBasePushHandler(helper, largeFileStore, orgSub);
        return pushHandler.handleQuery(pushQuery);
    }


    // NOTE on transactional safety: We try to make pushes atomic here by only "sealing in" the push with the last
    //     operation: updating the BranchEntry's head commit id. Until then, a push can freely overwrite existing
    //     folder listings, because if those folder listings have a commit id less than the branch head, they're
    //     assumed to be left over from an earlier failed push.
    @Override
    public void writeObjectsFromPush(
            String userId,
            HdObjectInputStream objectsIn,
            OrgSubscription orgSub)
            throws Exception {
        HBasePushHandler pushHandler = new HBasePushHandler(helper, largeFileStore, orgSub);
        pushHandler.writeObjects(userId, objectsIn);
    }


    @Override
    public CheckoutResult doCheckout(
            String org,
            String repo,
            String branchName,
            long commitId,
            CheckoutPathSet paths,
            ClientCheckoutSpec clientSpec,
            HdObjectOutputStream objectsOut)
            throws IOException {
        try {
            // TODO: Fix cyclic dependency b/w this class and HBaseCheckoutHandler.
            HBaseCheckoutHandler checkoutHandler = new HBaseCheckoutHandler(
                    org, repo, this, helper, largeFileStore, paths);
            return checkoutHandler.doCheckout(org, repo, branchName, commitId, objectsOut, clientSpec);
        } catch (IOException ioEx) {
            LOG.exception(ioEx, "Error checking out paths: %s", paths);
            return CheckoutResult.failed(ioEx);
        }
    }


    @Override
    public Optional<BranchEntry> getBranch(String org, String repo, String branchName) {
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);
        try {
            return helper.getBranch(rowKeyer.forBranch(branchName));
        } catch (IOException ioEx) {
            LOG.exception(ioEx, "Error getting branch head for: %s", branchName);
            return Optional.empty();
        }
    }


    @Override
    public MergeResult merge(String org, String repo, String branchName, long headCommitId) throws IOException {
        HBaseMerger merger = new HBaseMerger();
        MergeResult result = merger.merge(org, repo, branchName, headCommitId, helper);
        return result;
    }


    @Override
    public Optional<RepoBrowser> getBrowser(String org, String repo) throws IOException {
        Optional<RepoEntry> entry = helper.getRepoEntry(org, repo);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(HBaseRepoBrowser.forRepo(entry.get(), helper, largeFileStore));
    }


    @Override
    public HdLargeFileStore getLargeFileStore() {
        return largeFileStore;
    }


    // TODO: Make this safer, i.e. inaccessible in staging/prod somehow.
    public void resetAllData() throws IOException {
        Admin admin = conn.getAdmin();

        final List<String> allTables = ImmutableList.of(
                "Branches", "Files", "Folders", "Commits", "Merges", "Repos", "Users", "Tokens", "Tasks");

        final List<String> cfMainTables = new ArrayList<>(allTables);
        cfMainTables.remove("Users");

        for (String nameStr : allTables) {
            TableName name = TableName.valueOf(nameStr);
            if (admin.tableExists(name)) {
                if (admin.isTableEnabled(name)) {
                    admin.disableTable(name);
                }
                admin.deleteTable(name);
            }
        }

        for (String nameStr : cfMainTables) {
            TableDescriptor desc = TableDescriptorBuilder
                    .newBuilder(TableName.valueOf(nameStr))
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                    .build();
            admin.createTable(desc);
        }

        // TODO: This shouldn't use separate column families.
        TableDescriptor userDesc = TableDescriptorBuilder
                .newBuilder(TableName.valueOf("Users"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfEmailToId"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfIdToUser"))
                .build();
        admin.createTable(userDesc);
    }
}
