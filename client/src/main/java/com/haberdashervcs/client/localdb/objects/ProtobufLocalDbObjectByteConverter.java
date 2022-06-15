package com.haberdashervcs.client.localdb.objects;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.haberdashervcs.common.protobuf.LocalDbProto;


public final class ProtobufLocalDbObjectByteConverter implements LocalDbObjectByteConverter {

    public static ProtobufLocalDbObjectByteConverter getInstance() {
        return new ProtobufLocalDbObjectByteConverter();
    }


    private ProtobufLocalDbObjectByteConverter() {}

    @Override
    public LocalBranchState branchStateFromBytes(byte[] bytes) throws IOException {
        LocalDbProto.LocalBranchState proto = LocalDbProto.LocalBranchState.parseFrom(bytes);
        return LocalBranchState.of(
                proto.getBranchName(),
                proto.getBaseCommitId(),
                proto.getHeadCommitId(),
                proto.getCurrentlySyncedCommitId());
    }


    @Override
    public LocalRepoState repoStateFromBytes(byte[] bytes) throws IOException {
        LocalDbProto.LocalRepoState proto = LocalDbProto.LocalRepoState.parseFrom(bytes);
        LocalRepoState.State state;
        switch (proto.getState()) {
            case REBASE_IN_PROGRESS:
                return LocalRepoState.forRebaseInProgress(proto.getRebaseCommitBeingIntegrated());
            case NORMAL:
                return LocalRepoState.normal();
            default:
                throw new IllegalStateException("Unknown repo state: " + proto.getState());
        }
    }


    @Override
    public byte[] branchStateToBytes(LocalBranchState branchState) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(branchState.getBranchName()));
        LocalDbProto.LocalBranchState proto = LocalDbProto.LocalBranchState.newBuilder()
                .setBranchName(branchState.getBranchName())
                .setBaseCommitId(branchState.getBaseCommitId())
                .setHeadCommitId(branchState.getHeadCommitId())
                .setCurrentlySyncedCommitId(branchState.getCurrentlySyncedCommitId())
                .build();
        return proto.toByteArray();
    }


    @Override
    public byte[] repoStateToBytes(LocalRepoState repoState) {
        LocalDbProto.LocalRepoState.RepoState state;
        switch (repoState.getState()) {
            case REBASE_IN_PROGRESS:
                state = LocalDbProto.LocalRepoState.RepoState.REBASE_IN_PROGRESS;
                break;
            case NORMAL:
            default:
                state = LocalDbProto.LocalRepoState.RepoState.NORMAL;
                break;
        }

        LocalDbProto.LocalRepoState.Builder protoB = LocalDbProto.LocalRepoState.newBuilder()
                .setState(state);
        if (repoState.getState() == LocalRepoState.State.REBASE_IN_PROGRESS) {
            protoB.setRebaseCommitBeingIntegrated(repoState.getRebaseCommitBeingIntegrated());
        }

        return protoB.build().toByteArray();
    }

}
