package com.haberdashervcs.server.admincommands;

import com.haberdashervcs.server.datastore.HdLargeFileStore;
import com.haberdashervcs.server.datastore.hbase.HBaseDatastore;
import com.haberdashervcs.server.datastore.hbase.HdfsLargeFileStore;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;


// TODO: Set up environments (devel, staging, prod) and disable in staging & prod.
final class ResetAllData {


    public static void main(String[] args) throws Exception {
        System.out.println("Hello ResetAllData!");

        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: ResetAllData");
        }

        Configuration conf = HBaseConfiguration.create();
        conf.clear();
        Connection conn = ConnectionFactory.createConnection(conf);

        HdLargeFileStore largeFileStore = HdfsLargeFileStore.forConfiguration(conf);
        HBaseDatastore datastore = HBaseDatastore.forConnection(conn, largeFileStore);
        datastore.resetAllData();

        conn.close();
        System.out.println("Done.");
    }

}
