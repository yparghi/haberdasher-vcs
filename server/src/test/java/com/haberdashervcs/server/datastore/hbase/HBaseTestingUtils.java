package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.base.Preconditions;
import com.haberdashervcs.server.datastore.HdLargeFileStore;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


// TODO: If I move management of the actual HBase cluster here, consider using a shutdown hook to verify that testing
// code has called for the cluster to be shut down.
public final class HBaseTestingUtils {


    private static Configuration conf = null;
    private static Connection conn = null;


    public static synchronized Connection getTestConn() throws IOException  {
        if (conn != null) {
            return conn;
        }

        conf = HBaseConfiguration.create();
        conf.clear();
        conn = ConnectionFactory.createConnection(conf);

        Table metaTable = conn.getTable(TableName.valueOf("Meta"));
        byte[] rowKey = "CLUSTER_TYPE".getBytes(StandardCharsets.UTF_8);
        Get get = new Get(rowKey);
        Result result = metaTable.get(get);
        byte[] rowValue = result.getValue(
                Bytes.toBytes("cfMain"), Bytes.toBytes("value"));
        String existingClusterType = new String(rowValue, StandardCharsets.UTF_8);
        if (!existingClusterType.equals("TESTING")) {
            throw new IllegalStateException(String.format(
                    "Expected testing cluster at localhost, but its type is %s", existingClusterType));
        }

        return conn;
    }


    public static synchronized void resetTables() throws IOException {
        Preconditions.checkState(conn != null, "testClusterConn is null");

        HdLargeFileStore largeFileStore = HdfsLargeFileStore.forConfiguration(conf);
        HBaseDatastore datastore = HBaseDatastore.forConnection(conn, largeFileStore);
        datastore.resetAllData();
    }

}
