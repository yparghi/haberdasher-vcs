package com.haberdashervcs.client.localdb;

import java.util.Optional;

import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.objects.FileEntry;


public final class FileEntryWithPatchedContents {

    public static FileEntryWithPatchedContents of(FileEntry entry, RandomAccessBytes contents) {
        return new FileEntryWithPatchedContents(entry, contents);
    }


    private final FileEntry entry;
    private final RandomAccessBytes contents;

    private FileEntryWithPatchedContents(FileEntry entry, RandomAccessBytes contents) {
        this.entry = entry;
        this.contents = contents;
    }


    public String getId() {
        return entry.getId();
    }

    public RandomAccessBytes getContents() {
        return contents;
    }

    public FileEntry.ContentsType getContentsType() {
        return entry.getContentsType();
    }

    public FileEntry.StorageType getStorageType() {
        return entry.getStorageType();
    }

    public Optional<String> getBaseEntryId() {
        return entry.getBaseEntryId();
    }

    public FileEntry getEntry() {
        return entry;
    }

}
