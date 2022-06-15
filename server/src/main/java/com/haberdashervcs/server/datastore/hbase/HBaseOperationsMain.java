package com.haberdashervcs.server.datastore.hbase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.user.BCrypter;
import com.haberdashervcs.common.protobuf.UsersProto;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;


/**
 * Tests and tweaks used from the command line after shelling into a server.
 */
class HBaseOperationsMain {

    private static final HdLogger LOG = HdLoggers.create(HBaseOperationsMain.class);


    public static void main(String[] args) throws Exception {
        Configuration conf = HBaseConfiguration.create();
        conf.clear();
        Connection conn = ConnectionFactory.createConnection(conf);

        try {
            String op = args[0];
            switch (op) {
                case "putFreeTrialStartDate":
                    HBaseOperations.putFreeTrialStartDate(conn);
                    break;
                case "addUserRoles":
                    HBaseOperations.addUserRoles(conn);
                    break;
                case "addRepoSubscriptions":
                    HBaseOperations.addRepoSubscriptions(conn);
                    break;
                case "ensureRepoSize":
                    ensureRepoSize(conn, args);
                    break;
                case "migrateToBcrypt":
                    migrateToBcrypt(args, conn);
                    break;
                case "createTasksTable":
                    createTasksTable(args, conn);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown op: " + op);
            }
        } finally {
            conn.close();
        }
    }


    private static void createTasksTable(String[] args, Connection conn) throws Exception {
        Admin admin = conn.getAdmin();
        TableName name = TableName.valueOf("Tasks");
        if (admin.tableExists(name)) {
            LOG.info("Tasks table already exists.");
        } else {
            TableDescriptor desc = TableDescriptorBuilder
                    .newBuilder(name)
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                    .build();
            admin.createTable(desc);
            LOG.info("Created Tasks table.");
        }
    }


    private static void migrateToBcrypt(String[] args, Connection conn) throws Exception {
        Table usersTable = conn.getTable(TableName.valueOf("Users"));
        Scan scan = new Scan();
        ResultScanner scanner = usersTable.getScanner(scan);
        Result result;
        List<UsersProto.HdUser> updates = new ArrayList<>();
        while ((result = scanner.next()) != null) {
            byte[] rowBytes = result.getValue(
                    Bytes.toBytes("cfIdToUser"), Bytes.toBytes("user"));
            if (rowBytes == null) {  // Other column family
                continue;
            }
            UsersProto.HdUser userProto = UsersProto.HdUser.parseFrom(rowBytes);
            String plaintextPassword = userProto.getBcryptedPassword();
            if (plaintextPassword.length() < 20) {
                String bcrypted = BCrypter.bcryptPassword(plaintextPassword);
                UsersProto.HdUser updatedUser = UsersProto.HdUser.newBuilder(userProto)
                        .setBcryptedPassword(bcrypted)
                        .build();
                updates.add(updatedUser);
            }
        }
        scanner.close();

        for (UsersProto.HdUser updatedUser : updates) {
            LOG.info("Migrating: %s", updatedUser.getEmail());
            byte[] rowKey = updatedUser.getUserId().getBytes(StandardCharsets.UTF_8);
            Put put = new Put(rowKey);
            put.addColumn(Bytes.toBytes("cfIdToUser"), Bytes.toBytes("user"), updatedUser.toByteArray());
            usersTable.put(put);
        }

        LOG.info("Done.");
    }


    private static void p(String s)  {
        System.out.println(s);
    }


    private static void ensureRepoSize(Connection conn, String[] args) throws Exception {
        String org = args[1];
        String repo = args[2];

        HBaseOperations.ensureRepoSize(conn, org, repo);
    }

}
