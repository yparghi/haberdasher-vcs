package com.haberdashervcs.common;


public final class HdConstants {

    private HdConstants() {
        throw new UnsupportedOperationException();
    }


    /**
     * The maximum number of diff entries that can be "stacked" on a full FileEntry, before a new full entry should
     * be written. This is to prevent diff resolution from having to search back through too many file entries.
     */
    // This number can be increased, but NEVER DECREASED. Policies that consider large files, for example, must still
    // have a minimum of 10, for already written DB records.
    public static final int MAX_DIFF_SEARCH = 10;


    /////// Sizes

    private static final int MEGABYTE = 1024 * 1024;
    public static final int MAX_FILE_SIZE_BYTES = 1024 * MEGABYTE;
    public static final String MAX_FILE_SIZE_FOR_DISPLAY = "1GB";

    public static final int LARGE_FILE_SIZE_THRESHOLD_BYTES = 1 * MEGABYTE;

}
