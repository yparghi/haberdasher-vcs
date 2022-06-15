package com.haberdashervcs.client.localdb.sqlite;


final class SqliteLocalDbSchemas {

    private SqliteLocalDbSchemas() {
    }


    // TODO: Migration mechanism for db changes.
    static final int VERSION = 1;

    static final String META_SCHEMA = "" +
            "CREATE TABLE Meta (" +
            "key VARCHAR(255) PRIMARY KEY, " +
            "value BLOB NOT NULL " +
            ");";

    static final String BRANCHES_SCHEMA = "" +
            "CREATE TABLE Branches (" +
            "branchName VARCHAR(255) PRIMARY KEY, " +
            "branchState BLOB NOT NULL " +
            ");";

    static final String COMMITS_SCHEMA = "" +
            "CREATE TABLE Commits (" +
            "id VARCHAR(255) PRIMARY KEY, " +
            "contents BLOB NOT NULL " +
            ");";

    static final String FOLDERS_SCHEMA = "" +
            "CREATE TABLE Folders (" +
            "id VARCHAR(255) PRIMARY KEY, " +
            "contents BLOB NOT NULL " +
            ");";

    static final String FILES_SCHEMA = "" +
            "CREATE TABLE Files (" +
            "id VARCHAR(255) PRIMARY KEY, " +
            "contents BLOB NOT NULL " +
            ");";
}
