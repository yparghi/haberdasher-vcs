package com.haberdashervcs.common.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.HdConstants;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.protobuf.ServerProto;


// TODO! TESTS!!!
public final class ProtobufObjectInputStream implements HdObjectInputStream {

    private static final HdLogger LOG = HdLoggers.create(ProtobufObjectInputStream.class);


    public static ProtobufObjectInputStream forInputStream(InputStream in) {
        return new ProtobufObjectInputStream(in);
    }


    private final InputStream in;
    private final HdObjectByteConverter fromBytes;
    private final byte[] idBuf;

    private int bytesInIdBuf = 0;
    private int bytesNextObj = -1;
    private HdObjectId receivedId = null;
    private LargeObjectInputStream pendingLargeStream = null;

    private ProtobufObjectInputStream(InputStream in) {
        this.in = new BufferedInputStream(in, 4096);
        this.fromBytes = ProtobufObjectByteConverter.getInstance();
        this.idBuf = new byte[256];
    }


    @Override
    public Optional<HdObjectId> next() throws IOException {
        Preconditions.checkState(bytesNextObj == -1);
        Preconditions.checkState(bytesInIdBuf == 0);
        checkPendingLargeStream();

        while (true) {
            int thisByte = in.read();

            if (thisByte == -1) {
                if (bytesInIdBuf != 0) {
                    throw new IOException("Unexpected EOF in object stream");
                } else {
                    return Optional.empty();
                }

            } else if (thisByte == '\n') {
                setIdFromBytes();
                return Optional.of(receivedId);

            } else {
                idBuf[bytesInIdBuf] = (byte)thisByte;
                ++bytesInIdBuf;

                if (bytesInIdBuf >= idBuf.length) {
                    //String s = new String(idBuf, StandardCharsets.UTF_8);  TEMP!
                    throw new IOException("Id line was longer than expected!");
                }
            }
        }
    }


    private void checkPendingLargeStream() {
        if (pendingLargeStream == null) {
            return;
        }

        if (!pendingLargeStream.isClosed()) {
            throw new IllegalStateException("Pending large object stream wasn't closed.");
        }

        pendingLargeStream = null;
    }


    @Override
    public FolderListing getFolder() throws IOException {
        Preconditions.checkState(receivedId != null && receivedId.getType() == HdObjectId.ObjectType.FOLDER);
        byte[] buf = readExactly(bytesNextObj);
        resetObjectState();
        return fromBytes.folderFromBytes(buf);
    }


    @Override
    public FileEntry getFile() throws IOException {
        Preconditions.checkState(receivedId != null && receivedId.getType() == HdObjectId.ObjectType.FILE);
        byte[] buf = readExactly(bytesNextObj);
        resetObjectState();
        return fromBytes.fileFromBytes(buf);
    }


    @Override
    public CommitEntry getCommit() throws IOException {
        Preconditions.checkState(receivedId != null && receivedId.getType() == HdObjectId.ObjectType.COMMIT);
        byte[] buf = readExactly(bytesNextObj);
        resetObjectState();
        return fromBytes.commitFromBytes(buf);
    }


    @Override
    public LargeObjectInputStream getLargeFileContents() throws IOException {
        Preconditions.checkState(
                receivedId != null && receivedId.getType() == HdObjectId.ObjectType.LARGE_FILE_CONTENTS);
        LOG.debug("Large file stream: id: %s, bytes: %d", receivedId.getId(), bytesNextObj);
        LargeObjectInputStream portion = new LargeObjectInputStream(receivedId.getId(), in, bytesNextObj);
        resetObjectState();
        return portion;
    }


    @Override
    public MergeResult getMergeResult() throws IOException {
        Preconditions.checkState(receivedId != null && receivedId.getType() == HdObjectId.ObjectType.MERGE_RESULT);
        byte[] buf = readExactly(bytesNextObj);
        resetObjectState();
        return fromBytes.mergeResultFromBytes(buf);
    }


    @Override
    public ServerProto.PushSpec getPushSpec() throws IOException {
        Preconditions.checkState(receivedId != null && receivedId.getType() == HdObjectId.ObjectType.PUSH_SPEC);
        byte[] buf = readExactly(bytesNextObj);
        ServerProto.PushSpec proto = ServerProto.PushSpec.parseFrom(buf);
        resetObjectState();
        return proto;
    }


    private void resetObjectState() {
        bytesNextObj = -1;
        receivedId = null;
    }


    private byte[] readExactly(int numBytes) throws IOException {
        byte[] buf = new byte[numBytes];
        int bytesReadTotal = 0;
        while (bytesReadTotal < numBytes) {
            int bytesThisTime = in.read(buf, bytesReadTotal, buf.length - bytesReadTotal);
            if (bytesThisTime == -1) {
                throw new IOException("Unexpected EOF reading " + numBytes + " bytes");
            }
            bytesReadTotal += bytesThisTime;
        }
        return buf;
    }


    private void setIdFromBytes() throws IOException {
        Preconditions.checkState(receivedId == null);

        String idString = new String(idBuf, 0, bytesInIdBuf, StandardCharsets.UTF_8);
        LOG.debug("Got id string: %s", idString);
        bytesInIdBuf = 0;
        String[] parts = idString.split(":", 3);
        if (parts.length != 3) {
            throw new IOException("Object id string isn't of the form <type>:<id>:<num bytes>");
        }

        HdObjectId.ObjectType objectType = HdObjectId.ObjectType.valueOf(parts[0]);
        String objectId = parts[1];
        bytesNextObj = Integer.parseInt(parts[2]);

        if ((bytesNextObj > 2 * HdConstants.LARGE_FILE_SIZE_THRESHOLD_BYTES)
                && objectType != HdObjectId.ObjectType.LARGE_FILE_CONTENTS) {
            throw new IllegalStateException(String.format(
                    "Unexpectedly large object: %d bytes for object type %s", bytesNextObj, objectType));
        }

        this.receivedId = new HdObjectId(objectType, objectId);
    }


}
