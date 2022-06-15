package com.haberdashervcs.common.objects;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Preconditions;


/**
 * Represents an folder path in the repo, like "/" or "/subfolder/"
 */
public final class HdFolderPath
        implements Comparable<HdFolderPath> {


    public static HdFolderPath fromFolderListingFormat(String folderListingPath) {
        return new HdFolderPath(folderListingPath);
    }

    public static HdFolderPath parentPathForFile(String filePath) {
        Preconditions.checkArgument(filePath.startsWith("/"), "Invalid path: " + filePath);
        return new HdFolderPath(filePath.substring(0, filePath.lastIndexOf("/") + 1));
    }


    private final String withTrailingSlash;

    private HdFolderPath(String withTrailingSlash) {
        Preconditions.checkArgument(withTrailingSlash.startsWith("/"), "Invalid path: " + withTrailingSlash);
        Preconditions.checkArgument(withTrailingSlash.endsWith("/"), "Invalid path: " + withTrailingSlash);
        this.withTrailingSlash = withTrailingSlash;
    }


    public String forFolderListing() {
        return withTrailingSlash;
    }


    public Path toLocalPathFromRoot(Path root) {
        String relPathStr = withTrailingSlash.substring(1);
        // Paths.get("") is NOT equal to Paths.get(".").
        if (relPathStr.equals("")) {
            relPathStr = ".";
        }
        Path relPath = Paths.get(relPathStr);
        return root.relativize(relPath);
    }


    public HdFolderPath joinWithSubfolder(String name) {
        Preconditions.checkArgument(!name.endsWith("/"));
        return new HdFolderPath(withTrailingSlash + name + "/");
    }


    public String filePathForName(String fileName) {
        Preconditions.checkArgument(!fileName.contains("/"));
        return withTrailingSlash + fileName;
    }


    @Override
    public String toString() {
        return withTrailingSlash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HdFolderPath that = (HdFolderPath) o;
        return withTrailingSlash.equals(that.withTrailingSlash);
    }

    @Override
    public int hashCode() {
        return withTrailingSlash.hashCode();
    }

    // This is really only used for display purposes.
    @Override
    public int compareTo(HdFolderPath o) {
        return withTrailingSlash.compareTo(o.withTrailingSlash);
    }

}
