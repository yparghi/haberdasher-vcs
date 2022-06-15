package com.haberdashervcs.client.localdb.sqlite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.FileEntryWithPatchedContents;
import com.haberdashervcs.client.localdb.FileEntryWithRawContents;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.localdb.objects.LocalDbObjectByteConverter;
import com.haberdashervcs.client.localdb.objects.LocalRepoState;
import com.haberdashervcs.client.localdb.objects.ProtobufLocalDbObjectByteConverter;
import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.diff.git.GitDeltaDiffer;
import com.haberdashervcs.common.diff.git.PatchedViewRandomAccessBytes;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.FileRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;


public final class SqliteLocalDb implements LocalDb {

    private static final HdLogger LOG = HdLoggers.create(SqliteLocalDb.class);


    public static SqliteLocalDb inRepo(RepoConfig config) {
        return new SqliteLocalDb(config.getDotHdLocalFolder());
    }

    public static SqliteLocalDb forTesting(Path testingRepoDir) {
        return new SqliteLocalDb(testingRepoDir);
    }


    private static final String DB_FILENAME = "hdlocal.db";
    private static final Splitter META_LIST_SPLITTER = Splitter.on(':').omitEmptyStrings();
    private static final Joiner META_LIST_JOINER = Joiner.on(':');


    private final Path dbFilePath;
    // Sqlite in JDBC should only have one Connection open at a time, so we manage it through this.
    private final ConnectionHolder conn;
    private final HdObjectByteConverter byteConv;
    private final LocalDbObjectByteConverter localByteConv;
    private final LocalFolderLargeFileStore largeFileStore;

    private SqliteLocalDb(Path dotHdLocalDir) {
        this.dbFilePath = dotHdLocalDir.resolve(DB_FILENAME);
        this.conn = new ConnectionHolder(dbFilePath.toAbsolutePath().toString());
        this.byteConv = ProtobufObjectByteConverter.getInstance();
        this.localByteConv = ProtobufLocalDbObjectByteConverter.getInstance();
        this.largeFileStore = new LocalFolderLargeFileStore(dotHdLocalDir);
    }


    @Override
    public void init(BranchEntry mainFromServer) throws Exception {
        Preconditions.checkArgument(mainFromServer.getName().equals("main"));

        final File dbFile = dbFilePath.toFile();
        if (dbFile.exists()) {
            throw new IllegalStateException(
                    String.format("The file '%s' already exists!", dbFile));
        }

        startTransaction();

        createTable(SqliteLocalDbSchemas.META_SCHEMA);
        createTable(SqliteLocalDbSchemas.BRANCHES_SCHEMA);
        createTable(SqliteLocalDbSchemas.COMMITS_SCHEMA);
        createTable(SqliteLocalDbSchemas.FOLDERS_SCHEMA);
        createTable(SqliteLocalDbSchemas.FILES_SCHEMA);

        insertMetaValue(
                "SCHEMA_VERSION", String.valueOf(SqliteLocalDbSchemas.VERSION).getBytes(StandardCharsets.UTF_8));
        insertMetaValue(
                "REPO_STATE",
                localByteConv.repoStateToBytes(LocalRepoState.normal()));

        insertMetaValue("CURRENT_BRANCH", "main");
        createNewBranch(
                "main",
                LocalBranchState.of(
                        "main",
                        mainFromServer.getBaseCommitId(),
                        mainFromServer.getHeadCommitId(),
                        mainFromServer.getHeadCommitId()));
        insertMetaValue("GLOBAL_CHECKED_OUT_PATHS", "");

        finishTransaction();
    }


    @Override
    public void startTransaction() throws Exception {
        conn.start();
    }


    @Override
    public void finishTransaction() throws Exception {
        conn.commit();
    }


    @Override
    public void cancelTransaction() throws Exception {
        conn.cancel();
    }


    private void createTable(String sql) {
        try {
            Statement stmt = conn.get().createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }


    @Override
    public LocalBranchState getCurrentBranch() {
        String currentBranch = getMetaValueAsString("CURRENT_BRANCH");
        return getBranchState(currentBranch).get();
    }


    @Override
    public void switchToBranch(String branchName) {
        LocalBranchState state = getBranchState(branchName).get();
        updateMetaValue("CURRENT_BRANCH", branchName);
    }


    @Override
    public void createNewBranch(String branchName, LocalBranchState initialState) {
        try {
            byte[] branchStateBytes = localByteConv.branchStateToBytes(initialState);

            PreparedStatement stmt = conn.get().prepareStatement(
                    "INSERT INTO Branches (branchName, branchState) VALUES (?, ?)");
            stmt.setString(1, branchName);
            stmt.setBytes(2, branchStateBytes);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
        updateMetaValue("CURRENT_BRANCH", branchName);
    }


    @Override
    public Optional<LocalBranchState> getBranchState(String branchName) {
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT branchState FROM Branches WHERE branchName = ?");
            getStmt.setString(1, branchName);
            ResultSet rs = getStmt.executeQuery();
            if (rs.next()) {
                byte[] branchStateBytes = rs.getBytes("branchState");
                return Optional.of(localByteConv.branchStateFromBytes(branchStateBytes));
            } else {
                return Optional.empty();
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    @Override
    public void updateBranchState(String branchName, LocalBranchState newState) {
        try {
            PreparedStatement stmt = conn.get().prepareStatement(
                    "UPDATE Branches SET branchState = ? WHERE branchName = ?");
            stmt.setBytes(1, localByteConv.branchStateToBytes(newState));
            stmt.setString(2, branchName);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }


    @Override
    public LocalRepoState getRepoState() {
        try {
            return localByteConv.repoStateFromBytes(getMetaValue("REPO_STATE"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get repo state", e);
        }
    }


    @Override
    public void updateRepoState(LocalRepoState newState) {
        byte[] bytes = localByteConv.repoStateToBytes(newState);
        updateMetaValue("REPO_STATE", bytes);
    }


    private byte[] getMetaValue(String key) {
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT value FROM Meta WHERE key = ?");
            getStmt.setString(1, key);
            ResultSet rs = getStmt.executeQuery();
            rs.next();
            return rs.getBytes("value");
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }

    private String getMetaValueAsString(String key) {
        return new String(getMetaValue(key), StandardCharsets.UTF_8);
    }

    private void insertMetaValue(String key, String value) {
        insertMetaValue(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private void insertMetaValue(String key, byte[] value) {
        try {
            PreparedStatement stmt = conn.get().prepareStatement(
                    "INSERT INTO Meta (key, value) VALUES (?, ?)");
            stmt.setString(1, key);
            stmt.setBytes(2, value);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }

    private void updateMetaValue(String key, String value) {
        updateMetaValue(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private void updateMetaValue(String key, byte[] value) {
        try {
            PreparedStatement stmt = conn.get().prepareStatement(
                    "UPDATE Meta SET value = ? WHERE key = ?");
            stmt.setBytes(1, value);
            stmt.setString(2, key);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row updated, got " + rowsUpdated);
            }
        } catch (SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }


    @Override
    public CommitEntry getCommit(String key) {
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT contents FROM Commits WHERE id = ?");
            getStmt.setString(1, key);
            ResultSet rs = getStmt.executeQuery();
            rs.next();
            byte[] contentsBytes = rs.getBytes("contents");
            return byteConv.commitFromBytes(contentsBytes);
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Couldn't find commit: " + key, ex);
        }
    }


    private Optional<FolderListing> getFolderMaybe(String key) {
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT contents FROM Folders WHERE id = ?");
            getStmt.setString(1, key);
            ResultSet rs = getStmt.executeQuery();
            if (rs.next()) {
                byte[] contentsBytes = rs.getBytes("contents");
                return Optional.of(byteConv.folderFromBytes(contentsBytes));
            } else {
                return Optional.empty();
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Couldn't find folder: " + key, ex);
        }
    }


    @Override
    public Optional<FolderListing> findFolderAt(String branchName, String path, long commitId) {
        LOG.debug("TEMP: findFolderAt: %s | %s | %020d", branchName, path, commitId);
        String maxRow = String.format("%s:%s:%020d", branchName, path, commitId);
        String rowLike = String.format("%s:%s:%%", branchName, path);
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT contents FROM Folders WHERE id <= ? AND id LIKE ? ORDER BY id DESC LIMIT 1");
            getStmt.setString(1, maxRow);
            getStmt.setString(2, rowLike);
            ResultSet rs = getStmt.executeQuery();
            if (rs.next()) {
                byte[] contentsBytes = rs.getBytes("contents");
                return Optional.of(byteConv.folderFromBytes(contentsBytes));
            } else {
                return Optional.empty();
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Couldn't find folder at: " + maxRow, ex);
        }
    }


    @Override
    public FileEntryWithPatchedContents getFile(String key) throws IOException {
        Optional<FileEntryWithPatchedContents> maybe = getFileMaybe(key);
        if (!maybe.isPresent()) {
            throw new IllegalStateException("No such file found: " + key);
        } else {
            return maybe.get();
        }
    }


    private Optional<FileEntryWithPatchedContents> getFileMaybe(String key) throws IOException {
        Optional<FileEntry> entry = getFileEntry(key);
        if (entry.isEmpty()) {
            return Optional.empty();
        } else {
            RandomAccessBytes contents = resolveDiffsToBytes(entry.get());
            return Optional.of(FileEntryWithPatchedContents.of(entry.get(), contents));
        }
    }


    private Optional<FileEntry> getFileEntry(String key) {
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT contents FROM Files WHERE id = ?");
            getStmt.setString(1, key);
            ResultSet rs = getStmt.executeQuery();
            if (rs.next()) {
                byte[] contentsBytes = rs.getBytes("contents");
                FileEntry entry = byteConv.fileFromBytes(contentsBytes);
                return Optional.of(entry);
            } else {
                return Optional.empty();
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Couldn't find file: " + key, ex);
        }
    }


    @Override
    public List<String> fileIdsClientHasFrom(List<String> fileIdsToCheck) {
        try {
            ArrayList<String> out = new ArrayList<>();
            // JDBC for Sqlite doesn't support IN clauses and setArray, which is terrible. So we query each fileId.
            for (String fileId : fileIdsToCheck) {
                PreparedStatement getStmt = conn.get().prepareStatement(
                        "SELECT id FROM Files WHERE id = ?");
                getStmt.setString(1, fileId);
                ResultSet rs = getStmt.executeQuery();
                if (rs.next()) {
                    out.add(rs.getString("id"));
                }
            }

            return out;

        } catch (SQLException ex) {
            throw new RuntimeException("Error in fileIdsWeHaveFrom", ex);
        }
    }


    @Override
    public void putCommit(String key, CommitEntry commit) {
        try {
            byte[] commitBytes = byteConv.commitToBytes(commit);

            PreparedStatement stmt = conn.get().prepareStatement(
                    "INSERT INTO Commits (id, contents) VALUES (?, ?)");
            stmt.setString(1, key);
            stmt.setBytes(2, commitBytes);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error putting commit: " + key, ex);
        }
    }

    @Override
    public void putFolder(FolderListing folder) {
        String key = SqliteLocalDbRowKeyer.getInstance().forFolder(
                folder.getBranch(), folder.getPath(), folder.getCommitId());
        LOG.debug("putFolder: %s / %s", key, folder.getDebugString());

        try {
            // TODO: Is there a better way? If not, should we check that their byte[] forms are the same?
            //     - If I do this, note protobuf byte encoding is NOT DETERMINISTIC!:
            //           https://developers.google.com/protocol-buffers/docs/encoding
            Optional<FolderListing> maybeExisting = getFolderMaybe(key);
            if (maybeExisting.isPresent()) {
                LOG.debug("putFolder: Already exists");
                return;
            }

            byte[] folderBytes = byteConv.folderToBytes(folder);

            PreparedStatement stmt = conn.get().prepareStatement(
                    "INSERT INTO Folders (id, contents) VALUES (?, ?)");
            stmt.setString(1, key);
            stmt.setBytes(2, folderBytes);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error putting folder: " + key, ex);
        }
    }


    @Override
    // TODO: Consolidate repeated logic with other putFile() methods.
    public String putNewFile(Path localPath) throws IOException {
        final String hash = HdHasher.hashLocalFile(localPath);

        // TODO: Check if the key exists without pulling the whole entry.
        Optional<FileEntryWithPatchedContents> maybeExisting = getFileMaybe(hash);
        if (maybeExisting.isPresent()) {
            LOG.debug("putNewFile: Already exists");
            return hash;
        }

        LOG.info("New file: %s (%s...)", localPath, hash.substring(0, 6));
        RandomAccessBytes contents = FileRandomAccessBytes.of(localPath.toFile());
        FileEntry newEntry;
        if (contents.length() > HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
            newEntry = FileEntry.forFullContents(
                    hash, ByteArrayRandomAccessBytes.of(new byte[0]), FileEntry.StorageType.LARGE_FILE_STORE);
            largeFileStore.putRab(hash, contents);
        } else {
            newEntry = FileEntry.forFullContents(
                    hash, contents, FileEntry.StorageType.DATASTORE);
        }

        putFileEntry(newEntry);
        return hash;
    }


    private void putLargeFileEntryWithRab(FileEntry entry, RandomAccessBytes contents) throws IOException {
        Preconditions.checkArgument(entry.getEntryContents().length() == 0, "Invalid entry for large file");
        if (contents.length() <= HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
            throw new IllegalStateException("Expected large file contents, got size: " + contents.length());
        }

        putFileEntry(entry);
        largeFileStore.putRab(entry.getId(), contents);
    }


    @Override
    public void putLargeFileContents(String fileId, InputStream contents) throws IOException {
        largeFileStore.putStream(fileId, contents);
    }


    @Override
    public void putFileEntry(FileEntry entry) throws IOException {
        LOG.debug("putFileEntry: %s", entry.getDebugString());
        try {
            final String key = SqliteLocalDbRowKeyer.getInstance().forFile(entry.getId());
            // TODO: Check if the key exists without pulling the whole entry.
            Optional<FileEntryWithPatchedContents> maybeExisting = getFileMaybe(key);
            if (maybeExisting.isPresent()) {
                LOG.debug("putFileEntryAsIs: Already exists");
                return;
            }

            byte[] entryBytes = byteConv.fileToBytes(entry);
            PreparedStatement stmt = conn.get().prepareStatement(
                    "INSERT INTO Files (id, contents) VALUES (?, ?)");
            stmt.setString(1, key);
            stmt.setBytes(2, entryBytes);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated != 1) {
                throw new IllegalStateException("Expected 1 row inserted, got " + rowsUpdated);
            }

        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error putting file: " + entry.getId(), ex);
        }
    }


    // TODO: Write a full entry anyway if the diff is some percent size of the original/base contents.
    @Override
    public void putFileHandlingDiffEntries(
            String localFileHash, Path localFilePath, FileEntry commitEntry)
            throws IOException {

        FileEntry currentBase = commitEntry;
        for (int diffSearchLength = 0; diffSearchLength < HdConstants.MAX_DIFF_SEARCH; ++diffSearchLength) {

            if (currentBase.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                currentBase = getFile(currentBase.getBaseEntryId().get()).getEntry();
                continue;

            } else if (currentBase.getContentsType() == FileEntry.ContentsType.FULL) {
                RandomAccessBytes commitContents = getFile(commitEntry.getId()).getContents();
                RandomAccessBytes gitDeltaContents = ByteArrayRandomAccessBytes.of(GitDeltaDiffer.computeGitDiff(
                        commitContents, FileRandomAccessBytes.of(localFilePath.toFile())));
                FileEntry diffEntry;
                if (gitDeltaContents.length() > HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
                    diffEntry = FileEntry.forDiffGit(
                            localFileHash,
                            ByteArrayRandomAccessBytes.of(new byte[0]),
                            commitEntry.getId(),
                            FileEntry.StorageType.LARGE_FILE_STORE);
                    putLargeFileEntryWithRab(diffEntry, gitDeltaContents);
                } else {
                    diffEntry = FileEntry.forDiffGit(
                            localFileHash,
                            gitDeltaContents,
                            commitEntry.getId(),
                            FileEntry.StorageType.DATASTORE);
                    putFileEntry(diffEntry);
                }
                return;

            } else {
                throw new IllegalStateException("Unexpected contents type: " + currentBase.getContentsType());
            }
        }

        // TODO: Reconsider this policy if there are large file entries along the way -- the longer diff path
        // might be worth it. (And make corresponding changes on the server, if any.)
        LOG.debug("Putting a full FileEntry for %s (too many diffs)", localFileHash);
        RandomAccessBytes fullFileContents = FileRandomAccessBytes.of(localFilePath.toFile());
        FileEntry fullEntry;
        if (fullFileContents.length() > HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES) {
            fullEntry = FileEntry.forFullContents(
                    localFileHash,
                    ByteArrayRandomAccessBytes.of(new byte[0]),
                    FileEntry.StorageType.LARGE_FILE_STORE);
            putLargeFileEntryWithRab(fullEntry, fullFileContents);
        } else {
            fullEntry = FileEntry.forFullContents(
                    localFileHash,
                    fullFileContents,
                    FileEntry.StorageType.DATASTORE);
            putFileEntry(fullEntry);
        }
    }


    @Override
    public Optional<FileEntryWithRawContents> getFileWithRawContents(String key) throws IOException {
        Optional<FileEntry> entry = getFileEntry(key);
        if (entry.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(FileEntryWithRawContents.of(entry.get(), getRawContentsForEntry(entry.get())));
        }
    }


    private RandomAccessBytes getRawContentsForEntry(FileEntry entry) throws IOException {
        if (entry.getStorageType() == FileEntry.StorageType.DATASTORE) {
            return entry.getEntryContents();
        } else {
            return largeFileStore.get(entry.getId());
        }
    }


    private RandomAccessBytes resolveDiffsToBytes(final FileEntry file) throws IOException {
        if (file.getContentsType() == FileEntry.ContentsType.FULL) {
            return getRawContentsForEntry(file);
        } else if (file.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
            return resolveGitDiffs(file);
        } else {
            throw new IllegalStateException("Unexpected FileEntry contents type: " + file.getContentsType());
        }
    }


    private RandomAccessBytes resolveGitDiffs(FileEntry file) throws IOException {
        Preconditions.checkArgument(file.getContentsType() == FileEntry.ContentsType.DIFF_GIT);

        ArrayList<RandomAccessBytes> deltas = new ArrayList<>();
        FileEntry current = file;
        // We search N+1 times, because we allow N diffs, plus 1 full entry as the base.
        for (int i = 0; i < HdConstants.MAX_DIFF_SEARCH + 1; ++i) {

            if (current.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                RandomAccessBytes deltaContents = getRawContentsForEntry(current);
                deltas.add(0, deltaContents);

                String baseId = current.getBaseEntryId().get();
                try {
                    current = getFileEntry(baseId).get();
                } catch (NoSuchElementException ex) { // TEMP!
                    LOG.info("Not found base id: %s", baseId);
                    throw ex;
                }

            } else if (current.getContentsType() == FileEntry.ContentsType.FULL) {
                RandomAccessBytes baseContents = getRawContentsForEntry(current);
                return PatchedViewRandomAccessBytes.build(baseContents, deltas);

            } else {
                throw new IllegalStateException("Unexpected contents type: " + current.getContentsType());
            }
        }

        throw new IllegalStateException("Couldn't resolve a diff after " + HdConstants.MAX_DIFF_SEARCH + " entries");
    }


    @Override
    // TODO: Can this be combined with or used by other diff resolution methods?
    public List<String> getAllDiffEntryIds(String fileId) {
        SqliteLocalDbRowKeyer rowKeyer = SqliteLocalDbRowKeyer.getInstance();
        List<String> out = new ArrayList<>();

        String currentId = fileId;
        FileEntry current;
        int diffSearchLength = 0;
        // We search N+1 times, because we allow N diffs, plus 1 full entry as the base.
        for (; diffSearchLength < HdConstants.MAX_DIFF_SEARCH + 1; ++diffSearchLength) {
            current = getFileEntry(rowKeyer.forFile(currentId)).get();
            out.add(current.getId());
            if (current.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                currentId = current.getBaseEntryId().get();
            } else if (current.getContentsType() == FileEntry.ContentsType.FULL) {
                break;
            } else {
                throw new IllegalStateException("Unknown contents type: " + current.getContentsType());
            }
        }

        if (diffSearchLength >= HdConstants.MAX_DIFF_SEARCH + 1) {
            throw new IllegalStateException("Couldn't resolve a diff after " + HdConstants.MAX_DIFF_SEARCH + " entries");
        }
        return out;
    }


    @Override
    public List<FolderListing> getListingsSinceCommit(String branchName, String path, long commitIdExclusive) {
        // TODO: Sort out the relationship b/w the db impl and the row keyer. I think the keyer should be internal to
        //     this db impl.
        String rowMinimum = String.format("%s:%s:%020d", branchName, path, commitIdExclusive);
        String rowLike = String.format("%s:%s:%%", branchName, path);
        LOG.debug("getListingsSinceCommit: filters: %s / %s", rowMinimum, rowLike);
        try {
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT id, contents FROM Folders WHERE id > ? AND id LIKE ?");
            getStmt.setString(1, rowMinimum);
            getStmt.setString(2, rowLike);
            ResultSet rs = getStmt.executeQuery();

            ArrayList<FolderListing> out = new ArrayList<>();
            while (rs.next()) {
                String id = rs.getString("id");
                byte[] contentsBytes = rs.getBytes("contents");
                FolderListing folder = byteConv.folderFromBytes(contentsBytes);
                if (!(folder.getCommitId() > commitIdExclusive)
                        || !folder.getPath().equals(path)) {
                    throw new AssertionError(
                            "SQL bug: Unexpected folder!: id: " + id +
                                    " / contents: " + folder.getDebugString());
                }
                out.add(folder);
            }
            return out;

        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error getting folder range for: " + path, ex);
        }
    }


    @Override
    public CheckoutPathSet getGlobalCheckedOutPaths() {
        String rawString = getMetaValueAsString("GLOBAL_CHECKED_OUT_PATHS");
        return CheckoutPathSet.fromStrings(META_LIST_SPLITTER.splitToList(rawString));
    }


    @Override
    public void addGlobalCheckedOutPath(String path) {
        String rawString = getMetaValueAsString("GLOBAL_CHECKED_OUT_PATHS");
        CheckoutPathSet pathSet = CheckoutPathSet.fromStrings(META_LIST_SPLITTER.splitToList(rawString));
        CheckoutPathSet newPathSet = CheckoutPathSet.withAddition(pathSet, path);
        String newRawString = META_LIST_JOINER.join(newPathSet.toList());
        updateMetaValue("GLOBAL_CHECKED_OUT_PATHS", newRawString);
    }


    @Override
    public List<CommitEntry> getCommitsSince(String branchName, long commitIdExclusive) {
        Preconditions.checkArgument(commitIdExclusive >= 0);

        try {
            String idLike = String.format("'%s:%%'", branchName);
            String idMinimum = String.format("%s:%020d", branchName, commitIdExclusive);
            PreparedStatement stmt = conn.get().prepareStatement(
                    "SELECT contents FROM Commits WHERE id LIKE " + idLike + " AND id > ? ORDER BY id");
            stmt.setString(1, idMinimum);

            ResultSet rs = stmt.executeQuery();
            ArrayList<CommitEntry> out = new ArrayList<>();
            while (rs.next()) {
                byte[] contentsBytes = rs.getBytes("contents");
                CommitEntry commit = byteConv.commitFromBytes(contentsBytes);
                if (!commit.getBranchName().equals(branchName)
                        || commit.getCommitId() <= commitIdExclusive) {
                    throw new AssertionError(String.format(
                            "getCommitsSince: Unexpected commit: Branch %s and commit %d",
                            commit.getBranchName(),
                            commit.getCommitId()));
                }
                out.add(commit);
            }

            return out;

        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error getting commits since: " + commitIdExclusive, ex);
        }
    }


    @Override
    public List<FolderListing> getAllBranchHeads(String branchName) {
        try {
            String likePhrase = branchName + "%";
            PreparedStatement getStmt = conn.get().prepareStatement(
                    "SELECT contents FROM Folders WHERE id LIKE '" + likePhrase + "'");
            ResultSet rs = getStmt.executeQuery();

            HashMap<String, FolderListing> newestPerPath = new HashMap<>();
            while (rs.next()) {
                byte[] bytes = rs.getBytes("contents");
                FolderListing folder = byteConv.folderFromBytes(bytes);

                if (!newestPerPath.containsKey(folder.getPath())) {
                    newestPerPath.put(folder.getPath(), folder);

                } else if (newestPerPath.get(folder.getPath()).getCommitId() < folder.getCommitId()) {
                    newestPerPath.put(folder.getPath(), folder);
                }
            }

            return new ArrayList<>(newestPerPath.values());

        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error in getAllBranchHeadsSince", ex);
        }
    }

    @Override
    public Optional<FolderListing> getMostRecentListingForPath(
            long maxCommitId, String branchName, String path) {
        try {
            String likePhrase = String.format("%s:%s:%%", branchName, path);
            String maxCommitPhrase = String.format("%s:%s:%020d", branchName, path, maxCommitId);
            PreparedStatement getStmt = conn.get().prepareStatement("" +
                    "SELECT contents FROM Folders " +
                    "WHERE id LIKE '" + likePhrase + "' " +
                    "AND id <= '" + maxCommitPhrase + "' " +
                    "ORDER BY id DESC LIMIT 1");
            ResultSet rs = getStmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            } else {
                byte[] bytes = rs.getBytes("contents");
                FolderListing folder = byteConv.folderFromBytes(bytes);
                return Optional.of(folder);
            }
        } catch (SQLException | IOException ex) {
            throw new RuntimeException("Error in getAllBranchHeadsSince", ex);
        }
    }
}
