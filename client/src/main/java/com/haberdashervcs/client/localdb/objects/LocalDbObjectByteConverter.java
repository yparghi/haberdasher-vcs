package com.haberdashervcs.client.localdb.objects;

import java.io.IOException;


public interface LocalDbObjectByteConverter {

    LocalBranchState branchStateFromBytes(byte[] bytes) throws IOException;
    LocalRepoState repoStateFromBytes(byte[] bytes) throws IOException;

    byte[] branchStateToBytes(LocalBranchState branchState);
    byte[] repoStateToBytes(LocalRepoState repoState);
}
