package com.haberdashervcs.client.localdb;

import com.haberdashervcs.common.objects.CommitEntry;


public interface LocalDbRowKeyer {

    String forFile(String fileId);

    String forFolder(String branchName, String path, long commitId);

    String forCommit(CommitEntry newCommit);
}
