package com.haberdashervcs.client.talker;

import java.io.IOException;
import java.util.Optional;

import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.LocalDbRowKeyer;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDbRowKeyer;
import com.haberdashervcs.common.io.HdObjectId;
import com.haberdashervcs.common.io.HdObjectInputStream;
import com.haberdashervcs.common.io.LargeObjectInputStream;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;


final class ClientCheckoutObjectHandler {

    private static final HdLogger LOG = HdLoggers.create(ClientCheckoutObjectHandler.class);


    private final HdObjectInputStream objectsIn;
    private final LocalDb db;
    private final LocalDbRowKeyer rowKeyer;
    private final String branchName;

    // TODO: Consider moving this state-handling into the object input stream?
    private String fileIdFromLargeContents = null;

    ClientCheckoutObjectHandler(
            HdObjectInputStream objectsIn,
            LocalDb db,
            String branchName) {
        this.objectsIn = objectsIn;
        this.db = db;
        this.rowKeyer = SqliteLocalDbRowKeyer.getInstance();
        this.branchName = branchName;
    }


    void handle() throws IOException {
        Optional<HdObjectId> next;
        while ((next = objectsIn.next()).isPresent()) {

            HdObjectId.ObjectType nextType = next.get().getType();

            switch (nextType) {

                case FOLDER:
                    FolderListing folder = objectsIn.getFolder();
                    LOG.debug("Got folder: %s", folder.getDebugString());
                    db.putFolder(folder);
                    break;


                case FILE:
                    FileEntry file = objectsIn.getFile();
                    LOG.debug("Got file: %s", file.getDebugString());

                    if (file.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE) {
                        if (fileIdFromLargeContents == null) {
                            throw new IllegalStateException("Received large file entry before contents");
                        } else if (!file.getId().equals(fileIdFromLargeContents)) {
                            throw new IllegalStateException(String.format(
                                    "Unexpected large file entry: got id %s, expected %s",
                                    file.getId(), fileIdFromLargeContents));
                        }
                        fileIdFromLargeContents = null;
                    }

                    db.putFileEntry(file);
                    break;


                case LARGE_FILE_CONTENTS:
                    if (fileIdFromLargeContents != null) {
                        throw new IllegalStateException("Unexpected large file");
                    }

                    LargeObjectInputStream contentsStream = objectsIn.getLargeFileContents();
                    fileIdFromLargeContents = contentsStream.getObjectId();
                    LOG.info("Getting large file %s...", fileIdFromLargeContents);
                    db.putLargeFileContents(fileIdFromLargeContents, contentsStream);
                    break;


                default:
                    throw new IllegalStateException("Unexpected type: " + nextType);
            }
        }
    }

}
