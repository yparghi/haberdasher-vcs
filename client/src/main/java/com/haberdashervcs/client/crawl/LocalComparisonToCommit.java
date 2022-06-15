package com.haberdashervcs.client.crawl;

import java.nio.file.Path;
import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.common.objects.FolderListing;


public final class LocalComparisonToCommit {

    final String name;
    private final LocalDb db;

    // TODO! Should I also store the commit's FolderListing as commitParentFolder?
    //     If I do, maybe this whole thing should be refactored for immutability with factory
    //     methods and field asserts.
    @Nullable FolderListing.Entry entryInCommit = null;
    @Nullable Path pathInLocalRepo = null;

    LocalComparisonToCommit(String name, LocalDb db) {
        this.name = name;
        this.db = db;
    }


    public String getName() {
        return name;
    }

    @Nullable
    public FolderListing.Entry getEntryInCommit() {
        return entryInCommit;
    }

    @Nullable
    public Path getPathInLocalRepo() {
        return pathInLocalRepo;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("entryInCommit", entryInCommit)
                .add("pathInLocalRepo", pathInLocalRepo)
                .toString();
    }

}
