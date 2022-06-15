package com.haberdashervcs.client.talker;

import java.util.List;
import java.util.Optional;

import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.push.PushObjectSet;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.common.protobuf.ServerProto;


public interface ServerTalker {

    Optional<BranchEntry> getBranch(String branchName) throws Exception;


    ServerCheckoutSpec queryForCheckout(
            String branchName,
            long commitId,
            CheckoutPathSet allPaths)
            throws Exception;

    void checkout(
            String branchName,
            CheckoutPathSet allPaths,
            long commitId,
            LocalDb db,
            ClientCheckoutSpec clientSpec)
            throws Exception;


    // TODO! Use an object / byteConv.
    ServerProto.PushQueryResponse queryForPush(
            LocalBranchState localBranch,
            PushObjectSet pushQuery)
            throws Exception;

    void push(
            LocalBranchState localBranch,
            PushObjectSet objectsToPush,
            LocalDb db)
            throws Exception;


    List<CommitEntry> log(String branchName, String path, long atCommitId) throws Exception;


    MergeResult merge(LocalBranchState branch) throws Exception;
}
