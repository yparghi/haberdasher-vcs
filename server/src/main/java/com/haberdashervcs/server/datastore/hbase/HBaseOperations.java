package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.protobuf.ReposProto;
import com.haberdashervcs.common.protobuf.UsersProto;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


/**
 * One-off tasks that manipulate HBase directly.
 */
final class HBaseOperations {

    private static final HdLogger LOG = HdLoggers.create(HBaseOperations.class);


    static void ensureRepoSize(Connection conn, String org, String repo) throws IOException {
        HBaseRowKeyer rowKeyer = HBaseRowKeyer.forRepo(org, repo);

        byte[] rowKey = rowKeyer.forRepoEntry();
        final Table reposTable = conn.getTable(TableName.valueOf("Repos"));
        final String columnFamilyName = "cfMain";
        final String columnName = "repoSize";

        Get get = new Get(rowKey);
        Result result = reposTable.get(get);
        byte[] resultValue = result.getValue(Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));

        if (resultValue == null) {
            Put put = new Put(rowKey);
            put.addColumn(
                    Bytes.toBytes(columnFamilyName),
                    Bytes.toBytes(columnName),
                    Bytes.toBytes(0L));
            reposTable.put(put);
            LOG.info("Put new repo size of 0.");

        } else {
            LOG.info("Repo size already found: %d", Bytes.toLong(resultValue));
        }
    }


    static void addRepoSubscriptions(Connection conn) throws IOException {
        HdObjectByteConverter byteConv = ProtobufObjectByteConverter.getInstance();
        Table usersTable = conn.getTable(TableName.valueOf("Users"));
        Table reposTable = conn.getTable(TableName.valueOf("Repos"));

        String cfUser = "cfIdToUser";
        String userColumn = "user";

        String cfSub = "cfMain";
        String subColumn = "subscription";

        Scan scan = new Scan();
        ResultScanner scanner = usersTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] userBytes = result.getValue(
                    Bytes.toBytes(cfUser), Bytes.toBytes(userColumn));
            if (userBytes == null) {
                continue;
            }
            HdUser user = byteConv.userFromBytes(userBytes).getUser();

            OrgSubscription.OrgUserEntry orgUser = OrgSubscription.OrgUserEntry.of(
                    user.getOrg(), user.getUserId(), OrgSubscription.OrgUserEntry.State.ACTIVE);
            OrgSubscription newSub = OrgSubscription.of(
                    user.getOrg(),
                    OrgSubscription.State.ON_FREE_TRIAL,
                    ImmutableList.of(orgUser),
                    OrgSubscription.BillingPlan.FREE_TRIAL,
                    ReposProto.BillingState.newBuilder().setType("new").build());

            // This is unsafe, we're overwriting without checking anything. It's ONLY valid when migrating from a
            // one-user-per-repo state.
            byte[] subRowKey = String.format(":SUBSCRIPTION:%s", user.getOrg()).getBytes(StandardCharsets.UTF_8);
            Put subPut = new Put(subRowKey);
            subPut.addColumn(
                    Bytes.toBytes(cfSub),
                    Bytes.toBytes(subColumn),
                    byteConv.subscriptionToBytes(newSub));
            LOG.info("Putting subscription for user %s / org %s", user.getEmail(), user.getOrg());
            reposTable.put(subPut);
        }
    }


    static void addUserRoles(Connection conn) throws IOException {
        Table usersTable = conn.getTable(TableName.valueOf("Users"));
        String cf = "cfIdToUser";
        String userColumn = "user";

        Scan scan = new Scan();
        ResultScanner scanner = usersTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] userBytes = result.getValue(
                    Bytes.toBytes(cf), Bytes.toBytes(userColumn));
            if (userBytes == null) {
                // We've stumbled on a row for a different data type in a different column.
                continue;
            }

            UsersProto.HdUser userProto = UsersProto.HdUser.parseFrom(userBytes);
            UsersProto.HdUser updatedUser = UsersProto.HdUser.newBuilder(userProto)
                    .setRole(UsersProto.HdUser.Role.OWNER)
                    .build();

            Put updatePut = new Put(result.getRow());
            updatePut.addColumn(
                    Bytes.toBytes(cf),
                    Bytes.toBytes(userColumn),
                    updatedUser.toByteArray());
            LOG.info("Adding owner role for user: %s", updatedUser.getEmail());
            usersTable.put(updatePut);
        }
    }


    public static void putFreeTrialStartDate(Connection conn) throws Exception {
        HdObjectByteConverter byteConv = ProtobufObjectByteConverter.getInstance();
        Table reposTable = conn.getTable(TableName.valueOf("Repos"));

        String cfSub = "cfMain";
        String subColumn = "subscription";

        Scan scan = new Scan();
        ResultScanner scanner = reposTable.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            byte[] subBytes = result.getValue(
                    Bytes.toBytes(cfSub), Bytes.toBytes(subColumn));
            if (subBytes == null) {
                continue;
            }

            OrgSubscription sub = byteConv.subscriptionFromBytes(subBytes);

            ReposProto.BillingState newBillingState = ReposProto.BillingState.newBuilder(sub.getBillingState())
                    .putFields("freeTrialStartDate", Long.toString(System.currentTimeMillis()))
                    .build();
            OrgSubscription newSub = OrgSubscription.of(
                    sub.getOrg(), sub.getState(), sub.getUsers(), sub.getBillingPlan(), newBillingState);

            byte[] subRowKey = String.format(":SUBSCRIPTION:%s", newSub.getOrg()).getBytes(StandardCharsets.UTF_8);
            Put subPut = new Put(subRowKey);
            subPut.addColumn(
                    Bytes.toBytes(cfSub),
                    Bytes.toBytes(subColumn),
                    byteConv.subscriptionToBytes(newSub));
            LOG.info("Putting new orgsub %s", newSub);
            reposTable.put(subPut);
        }
    }

}
