package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.util.ArrayList;

import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.diff.git.PatchedViewRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.server.browser.FileBrowser;
import com.haberdashervcs.server.datastore.HdLargeFileStore;


final class HBaseFileBrowser implements FileBrowser {

    private static final HdLogger LOG = HdLoggers.create(HBaseFileBrowser.class);


    static HBaseFileBrowser forFile(
            FileEntry file, HdLargeFileStore largeFileStore, HBaseRowKeyer rowKeyer, HBaseRawHelper helper) {
        return new HBaseFileBrowser(file, largeFileStore, rowKeyer, helper);
    }


    private final FileEntry file;
    private final HdLargeFileStore largeFileStore;
    private final HBaseRowKeyer rowKeyer;
    private final HBaseRawHelper helper;

    private HBaseFileBrowser(
            FileEntry file, HdLargeFileStore largeFileStore, HBaseRowKeyer rowKeyer, HBaseRawHelper helper) {
        this.file = file;
        this.largeFileStore = largeFileStore;
        this.rowKeyer = rowKeyer;
        this.helper = helper;
    }


    @Override
    public RandomAccessBytes getWholeContents() throws IOException {

        if (file.getContentsType() == FileEntry.ContentsType.FULL) {
            return getEntryContents(file);
        }

        FileEntry currentEntry = file;
        ArrayList<RandomAccessBytes> diffs = new ArrayList<>();
        // We search N+1 times, because we allow N diffs, plus 1 full entry as the base.
        for (int i = 0; i < HdConstants.MAX_DIFF_SEARCH + 1; ++i) {

            RandomAccessBytes entryContents = getEntryContents(currentEntry);

            if (currentEntry.getContentsType() == FileEntry.ContentsType.FULL) {
                PatchedViewRandomAccessBytes patchedContents = PatchedViewRandomAccessBytes.build(
                        entryContents, diffs);
                return patchedContents;

            } else if (currentEntry.getContentsType() == FileEntry.ContentsType.DIFF_GIT) {
                diffs.add(0, entryContents);
                currentEntry = helper.getFile(rowKeyer.forFile(currentEntry.getBaseEntryId().get()));

            } else {
                throw new UnsupportedOperationException("Unknown contents type: " + file.getContentsType());
            }
        }

        throw new IllegalStateException("Couldn't resolve a diff after " + HdConstants.MAX_DIFF_SEARCH + " entries");
    }


    private RandomAccessBytes getEntryContents(FileEntry entry) throws IOException {
        if (entry.getStorageType() == FileEntry.StorageType.DATASTORE) {
            return entry.getEntryContents();
        } else {
            return largeFileStore.getFileRab(
                    rowKeyer.getOrg(), rowKeyer.getRepo(), entry.getId());
        }
    }

}
