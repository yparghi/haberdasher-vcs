package com.haberdashervcs.common.objects;


public final class FolderWithPath {

    public static FolderWithPath forPathAndListing(String path, FolderListing listing) {
        return new FolderWithPath(path, listing);
    }


    private final String path;
    private final FolderListing listing;

    private FolderWithPath(String path, FolderListing listing) {
        this.path = path;
        this.listing = listing;
    }

    public String getPath() {
        return path;
    }

    public FolderListing getListing() {
        return listing;
    }
}
