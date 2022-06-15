package com.haberdashervcs.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.protobuf.ServerProto;


public final class ProtobufObjectOutputStream implements HdObjectOutputStream {

    public static ProtobufObjectOutputStream forOutputStream(OutputStream out) {
        return new ProtobufObjectOutputStream(out);
    }


    private final OutputStream out;
    private final HdObjectByteConverter byteConv;

    private ProtobufObjectOutputStream(OutputStream out) {
        this.out = out;
        this.byteConv = ProtobufObjectByteConverter.getInstance();
    }


    @Override
    public void writeFolder(String folderId, FolderListing folder) throws IOException {
        byte[] converted = byteConv.folderToBytes(folder);
        write(new HdObjectId(HdObjectId.ObjectType.FOLDER, folderId), converted);
    }


    @Override
    public void writeFile(String fileId, FileEntry file) throws IOException {
        byte[] converted = byteConv.fileToBytes(file);
        write(new HdObjectId(HdObjectId.ObjectType.FILE, fileId), converted);
    }


    @Override
    public void writeCommit(String commitId, CommitEntry commit) throws IOException {
        byte[] converted = byteConv.commitToBytes(commit);
        write(new HdObjectId(HdObjectId.ObjectType.COMMIT, commitId), converted);
    }


    @Override
    public void writeLargeFileContents(
            FileEntry entry, InputStream contents, int sizeInBytes)
            throws IOException {
        if (sizeInBytes > HdConstants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException(String.format(
                    "Large file is too large. Given: %d. Max: %s",
                    sizeInBytes, HdConstants.MAX_FILE_SIZE_FOR_DISPLAY));
        }

        // Write the contents before the entry, because the entry officially registers the file in the database. This
        // way, if writing the contents fails, the entry won't be in the database.
        out.write(idToString(
                new HdObjectId(HdObjectId.ObjectType.LARGE_FILE_CONTENTS, entry.getId()),
                sizeInBytes));
        out.flush();
        contents.transferTo(out);
        out.flush();

        writeFile(entry.getId(), entry);
    }


    @Override
    public void writeMergeResult(MergeResult mergeResult) throws IOException {
        byte[] converted = byteConv.mergeResultToBytes(mergeResult);
        write(new HdObjectId(HdObjectId.ObjectType.MERGE_RESULT, "TODO toss ids?"), converted);
    }


    @Override
    public void writePushSpec(ServerProto.PushSpec pushSpec) throws IOException {
        byte[] converted = pushSpec.toByteArray();
        write(new HdObjectId(HdObjectId.ObjectType.PUSH_SPEC, "TODO toss ids?"), converted);
    }


    private byte[] idToString(HdObjectId id, int numBytes) {
        return String.format("%s:%s:%d\n", id.getType(), id.getId(), numBytes)
                .getBytes(StandardCharsets.UTF_8);
    }


    private void write(HdObjectId objectId, byte[] bytes) throws IOException {
        out.write(idToString(objectId, bytes.length));
        out.flush();
        out.write(bytes);
        out.flush();
    }
}
