package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.protobuf.FilesProto;
import com.haberdashervcs.common.protobuf.FoldersProto;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


// Assumes an HBase test cluster is already running -- see HBaseTestServerMain in the hbase-test-server module for a
// simple Java app you can start/stop.
//
// NOTE: I'd like to set up the test cluster right here in these JUnit tests, but the HBase test lib has all kinds of
// dependencies I don't want on my classpath. I think for convenience, HBase has to be running separately.
public class HBaseDatastoreTest {

    private static final HdLogger LOG = HdLoggers.create(HBaseDatastoreTest.class);

    private Connection conn;
    private Admin admin;


    @Before
    public void setUp() throws Exception {
        Configuration conf = HBaseConfiguration.create();
        conf.clear();

        // TODO!! Add specialty settings here pertaining only to the test cluster -- like custom port(s) for example.
        conn = ConnectionFactory.createConnection(conf);
        admin = conn.getAdmin();

        createTables();
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

        TableDescriptor commitsTableDesc = TableDescriptorBuilder
                .newBuilder(TableName.valueOf("Commits"))
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                .build();
        admin.createTable(commitsTableDesc);
    }

    private void clearTables() throws Exception {
        for (String tableName : Arrays.asList("Files", "Folders", "Commits")) {
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


    private String putFileRaw(String contents) throws IOException {
        final String fileId = UUID.randomUUID().toString();

        Table filesTable = conn.getTable(TableName.valueOf("Files"));
        final String rowKey = fileId;
        final String columnFamilyName = "cfMain";
        final String columnName = "contents";

        FilesProto.FileEntry fileProto = FilesProto.FileEntry.newBuilder()
                .setContents(ByteString.copyFrom(contents, Charsets.UTF_8))
                .build();

        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                fileProto.toByteArray());
        filesTable.put(put);

        return fileId;
    }

    // TODO! Move to raw helper, here and in putFile()
    private String putFolderRaw(List<String> fileIds, List<String> fileNames) throws IOException {
        Preconditions.checkArgument(fileIds.size() == fileNames.size());

        final String folderId = UUID.randomUUID().toString();

        Table filesTable = conn.getTable(TableName.valueOf("Folders"));
        final String rowKey = folderId;
        final String columnFamilyName = "cfMain";
        final String columnName = "listing";

        FoldersProto.FolderListing.Builder folderProto = FoldersProto.FolderListing.newBuilder();
        for (int i = 0; i < fileIds.size(); i++) {
            FoldersProto.FolderListingEntry entry = FoldersProto.FolderListingEntry.newBuilder()
                    .setType(FoldersProto.FolderListingEntry.Type.FILE)
                    .setName(fileNames.get(i))
                    .setId(fileIds.get(i))
                    .build();
            folderProto.addEntries(entry);
        }

        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                folderProto.build().toByteArray());
        filesTable.put(put);

        return folderId;
    }

    /*
    private String putCommitRaw(String rootFolderId) throws IOException {
        final String commitId = UUID.randomUUID().toString();

        Table commitsTable = conn.getTable(TableName.valueOf("Commits"));
        final String rowKey = commitId;
        final String columnFamilyName = "cfMain";
        final String columnName = "entry";

        CommitsProto.CommitEntry commitProto = CommitsProto.CommitEntry.newBuilder()
                .setRootFolderId(rootFolderId)
                .build();

        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(
                Bytes.toBytes(columnFamilyName),
                Bytes.toBytes(columnName),
                commitProto.toByteArray());
        commitsTable.put(put);

        return commitId;
    }

    @Test
    public void basicRootFolderCheckout() throws Exception {
        String firstFileId = putFileRaw("apple");
        String secondFileId = putFileRaw("banana");

        String folderId = putFolderRaw(
                Arrays.asList(firstFileId, secondFileId),
                Arrays.asList("apple.txt", "banana.txt"));

        String commitId = putCommitRaw(folderId);

        HBaseDatastore datastore = HBaseDatastore.forConnection(conn);
        CheckoutResult result = datastore.checkout("test-org", "test-repo", commitId, "/");

        assertEquals(CheckoutResult.Status.OK, result.getStatus());

        ArrayList<CheckoutStream.CheckoutFile> resultFiles = new ArrayList<>();
        for (CheckoutStream.CheckoutFile file : result.getStream()) {
            resultFiles.add(file);
        }

        // TODO: unordered check, and check contents
        assertEquals(2, resultFiles.size());
        assertEquals("/apple.txt", resultFiles.get(0).getPath());
        assertEquals("/banana.txt", resultFiles.get(1).getPath());
    }


    @Test
    public void basicInnerFolderCheckout() throws Exception {
        // TODO
        assertTrue(true);
    }


    @Test
    public void basicApplyChangeset() throws Exception {
        HBaseRawHelper helper = HBaseRawHelper.forConnection(conn);

        Changeset.Builder changesetBuilder = Changeset.builder();

        AddChange fileA = AddChange.forContents(
                "fileA_id", FileEntry.forContents("apple".getBytes(StandardCharsets.UTF_8)));
        AddChange fileB = AddChange.forContents(
                "fileB_id", FileEntry.forContents("banana".getBytes(StandardCharsets.UTF_8)));
        changesetBuilder = changesetBuilder.withAddChange(fileA);
        changesetBuilder = changesetBuilder.withAddChange(fileB);

        FolderListing folder = FolderListing.forEntries(Arrays.asList(
                FolderListing.FolderEntry.forFile("apple.txt", fileA.getId()),
                FolderListing.FolderEntry.forFile("banana.txt", fileB.getId())));
        changesetBuilder = changesetBuilder.withFolderAndPath("/", folder);

        final HdDatastore datastore = HBaseDatastore.forConnection(conn);
        final Changeset changeset = changesetBuilder.build();

        ApplyChangesetResult result = datastore.applyChangeset(changeset);
        assertEquals(ApplyChangesetResult.Status.OK, result.getStatus());
        assertEquals(changeset.getProposedCommitId(), result.getCommitId());

        CommitEntry commitEntry = helper.getCommit(result.getCommitId().getBytes(StandardCharsets.UTF_8));
        assertEquals(changeset.getProposedRootFolderId(), commitEntry.getRootFolderId());

        FolderListing rootFolder = helper.getFolder(commitEntry.getRootFolderId());
        assertEquals(2, rootFolder.getEntries().size());
        assertEquals("apple.txt", rootFolder.getEntries().get(0).getName());
        assertEquals("banana.txt", rootFolder.getEntries().get(1).getName());
    }
     */
}
