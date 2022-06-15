package com.haberdashervcs.client.localdb.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


/**
 * Manages the sqlite connection / transaction state.
 */
final class ConnectionHolder {

    private static final HdLogger LOG = HdLoggers.create(ConnectionHolder.class);


    private final String dbFilename;
    private Connection conn;
    private boolean transactionInProgress;

    ConnectionHolder(String dbFilename) {
        this.dbFilename = dbFilename;
        this.conn = null;
        this.transactionInProgress = false;
    }


    synchronized void start() throws SQLException {
        Preconditions.checkState(!transactionInProgress, "A sqlite transaction is already in progress");
        if (conn == null) {
            LOG.debug("Opening DB file at: %s", dbFilename);
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFilename);
            conn.setAutoCommit(false);
        }

        transactionInProgress = true;
    }

    synchronized Connection get() {
        Preconditions.checkState(transactionInProgress, "No sqlite transaction is in progress");
        return conn;
    }

    synchronized void cancel() throws SQLException {
        Preconditions.checkState(transactionInProgress, "No sqlite transaction is in progress");
        conn.rollback();
        transactionInProgress = false;
    }

    synchronized void commit() throws SQLException {
        Preconditions.checkState(transactionInProgress, "No sqlite transaction is in progress");
        conn.commit();
        transactionInProgress = false;
    }
}
