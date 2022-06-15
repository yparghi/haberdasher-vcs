package com.haberdashervcs.common.change;


public final class DeleteChange {

    public static DeleteChange forFile(String fileId) {
        return new DeleteChange(fileId);
    }


    private final String fileId;

    private DeleteChange(String fileId) {
        this.fileId = fileId;
    }

    public String getFileId() {
        return fileId;
    }
}
