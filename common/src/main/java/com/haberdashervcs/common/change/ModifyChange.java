package com.haberdashervcs.common.change;

import com.haberdashervcs.common.objects.FileEntry;


public final class ModifyChange {

    public static ModifyChange forContents(String id, FileEntry file) {
        return new ModifyChange(id, file);
    }


    private final String id;
    private final FileEntry file;

    private ModifyChange(String id, FileEntry file) {
        this.id = id;
        this.file = file;
    }

    public String getId() {
        return id;
    }

    public FileEntry getFile() {
        return file;
    }
}
