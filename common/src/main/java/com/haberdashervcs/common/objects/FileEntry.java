package com.haberdashervcs.common.objects;

import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;


public class FileEntry {

    public static FileEntry forFullContents(String id, RandomAccessBytes fullContents, StorageType storageType) {
        return new FileEntry(id, ContentsType.FULL, fullContents, null, storageType);
    }

    public static FileEntry forDiffGit(String id, RandomAccessBytes diffContents, String baseEntryId, StorageType storageType) {
        return new FileEntry(id, ContentsType.DIFF_GIT, diffContents, baseEntryId, storageType);
    }

    public static FileEntry forFullContents(byte[] bytes) {
        throw new UnsupportedOperationException("TEMP for the build, but toss this");
    }


    public enum ContentsType {
        FULL,
        DIFF_GIT
    }

    public enum StorageType {
        DATASTORE,
        LARGE_FILE_STORE
    }


    private final String id;
    private final ContentsType contentsType;
    private final RandomAccessBytes entryContents;
    private final @Nullable String baseEntryId;
    private final StorageType storageType;

    private FileEntry(
            String id,
            ContentsType contentsType,
            RandomAccessBytes contents,
            @Nullable String baseEntryId,
            StorageType storageType) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
        this.id = id;
        this.contentsType = Preconditions.checkNotNull(contentsType);
        this.entryContents = contents;
        this.baseEntryId = baseEntryId;
        this.storageType = storageType;
    }

    public String getId() {
        return id;
    }

    // TODO: Consider storing even small file contents separate from the entry.
    public RandomAccessBytes getEntryContents() {
        return entryContents;
    }

    public ContentsType getContentsType() {
        return contentsType;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public Optional<String> getBaseEntryId() {
        return Optional.ofNullable(baseEntryId);
    }

    public String getDebugString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("contentsType", contentsType)
                .add("baseEntryId", baseEntryId)
                .add("storageType", storageType)
                .toString();
    }
}
