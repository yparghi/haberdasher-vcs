package com.haberdashervcs.common.io;

import java.io.IOException;
import java.io.InputStream;

import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.protobuf.ServerProto;


public interface HdObjectOutputStream {

    void writeFolder(String folderId, FolderListing folder) throws IOException;

    void writeFile(String fileId, FileEntry file) throws IOException;

    void writeCommit(String commitId, CommitEntry commit) throws IOException;

    void writeLargeFileContents(FileEntry entry, InputStream contents, int sizeInBytes) throws IOException;

    void writeMergeResult(MergeResult mergeResult) throws IOException;

    void writePushSpec(ServerProto.PushSpec pushSpec) throws IOException;
}
