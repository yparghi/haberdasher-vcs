package com.haberdashervcs.hbasetestserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.MiniZooKeeperCluster;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.log4j.Logger;


/**
 * TODO
 */
public class HBaseTestServerMain {

    private static final Logger LOG = Logger.getLogger(HBaseTestServerMain.class);

    private static final String USAGE = "Usage: hd-hbase-test-server <TESTING | PERSISTENT> <path for persistent storage> [--no-hdfs]";


    private enum PersistenceType {
        TESTING,
        PERSISTENT
    }


    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(USAGE);
        }

        final boolean shouldStartHdfs;
        if (args[args.length - 1].equals("--no-hdfs")) {
            shouldStartHdfs = false;
            args = Arrays.copyOfRange(args, 0, args.length - 1);
        } else {
            shouldStartHdfs = true;
        }

        final PersistenceType persistenceType = PersistenceType.valueOf(args[0]);

        final String pathPrefix;
        if (persistenceType == PersistenceType.PERSISTENT) {
            if (args.length != 2) {
                throw new IllegalArgumentException(USAGE);
            }
            pathPrefix = args[1];

        } else {
            if (args.length != 1) {
                throw new IllegalArgumentException(USAGE);
            }
            pathPrefix = String.format(
                    "/tmp/hdtest-%d",
                    ThreadLocalRandom.current().nextInt(10000000, 99999999));
        }

        Path hBaseRootPath = Paths.get(pathPrefix, "hbase-root");
        Path hBaseTmpPath = Paths.get(pathPrefix, "hbase-tmp");
        Path zkPath = Paths.get(pathPrefix, "zk");
        Path hdfsBasePath = Paths.get(pathPrefix, "hdfs");

        LOG.info("Starting the HBase test server at root path: " + hBaseRootPath);
        Configuration conf = HBaseConfiguration.create();
        conf.clear();

        conf.set(HConstants.ZOOKEEPER_CLIENT_PORT, "2181");
        conf.set("hbase.rootdir", hBaseRootPath.toAbsolutePath().toString());
        conf.set("hbase.tmp.dir", hBaseTmpPath.toAbsolutePath().toString());
        conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, hdfsBasePath.toAbsolutePath().toString());

        conf.set("hbase.cluster.distributed", "false");
        // This prevents error messages with the WAL -- by disabling it, I think.
        conf.set("hbase.unsafe.stream.capability.enforce", "false");

        MiniZooKeeperCluster zk = new MiniZooKeeperCluster(conf);
        zk.setDefaultClientPort(2181);
        zk.startup(zkPath.toFile());

        // This constructor call apparently starts the cluster??
        MiniHBaseCluster hBase = new MiniHBaseCluster(conf, 1);
        markClusterType(persistenceType, conf);

        if (shouldStartHdfs) {
            LOG.info("Starting HDFS.");
            startHdfs(conf);
        } else {
            LOG.info("Skipping HDFS.");
        }

        LOG.info("Done with cluster setup.");
        hBase.join();
    }


    private static void markClusterType(PersistenceType persistenceType, Configuration conf) throws IOException {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();
        TableName metaTableName = TableName.valueOf("Meta");
        if (!admin.tableExists(metaTableName)) {
            TableDescriptor desc = TableDescriptorBuilder
                    .newBuilder(metaTableName)
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of("cfMain"))
                    .build();
            admin.createTable(desc);
        }

        Table metaTable = conn.getTable(TableName.valueOf("Meta"));
        byte[] rowKey = "CLUSTER_TYPE".getBytes(StandardCharsets.UTF_8);

        Get get = new Get(rowKey);
        Result result = metaTable.get(get);
        if (result.isEmpty()) {
            Put put = new Put(rowKey);
            put.addColumn(
                    Bytes.toBytes("cfMain"),
                    Bytes.toBytes("value"),
                    persistenceType.toString().getBytes(StandardCharsets.UTF_8));
            metaTable.put(put);

        } else {
            byte[] rowValue = result.getValue(
                    Bytes.toBytes("cfMain"), Bytes.toBytes("value"));
            String existingClusterType = new String(rowValue, StandardCharsets.UTF_8);
            if (!existingClusterType.equals(persistenceType.toString())) {
                throw new IllegalStateException(String.format(
                        "Tried to mark cluster type %s, but existing type is %s",
                        persistenceType, existingClusterType));
            }
        }

    }


    private static void startHdfs(Configuration conf) throws IOException {
        MiniDFSCluster hdfs = new MiniDFSCluster.Builder(conf)
                .nameNodePort(9006)
                .nameNodeHttpPort(9007)
                .build();
        LOG.info("HDFS is running at: " + hdfs.getNameNodePort());
    }

}
