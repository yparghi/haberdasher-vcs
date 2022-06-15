package com.haberdashervcs.server.datastore;

import java.io.IOException;
import java.util.Optional;

import com.haberdashervcs.common.io.HdObjectInputStream;
import com.haberdashervcs.common.io.HdObjectOutputStream;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.protobuf.ServerProto;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.operations.checkout.CheckoutResult;


public interface HdDatastore {

    void createRepo(
            String org,
            String repo)
            throws Exception;


    ServerCheckoutSpec computeCheckout(
            String org,
            String repo,
            String branchName,
            long commitId,
            CheckoutPathSet paths)
            throws Exception;


    CheckoutResult doCheckout(
            String org,
            String repo,
            String branchName,
            long commitId,
            CheckoutPathSet paths,
            ClientCheckoutSpec clientSpec,
            HdObjectOutputStream objectsOut)
            throws IOException;


    // TODO! Use an object/byteConv
    ServerProto.PushQueryResponse handlePushQuery(
            ServerProto.PushQuery pushQuery,
            OrgSubscription orgSub)
            throws Exception;

    void writeObjectsFromPush(
            String userId,
            HdObjectInputStream objectsIn,
            OrgSubscription orgSub)
            throws Exception;


    Optional<BranchEntry> getBranch(
            String org,
            String repo,
            String branchName);

    MergeResult merge(
            String org,
            String repo,
            String branchName,
            long headCommitId)
            throws IOException;

    Optional<RepoBrowser> getBrowser(
            String org,
            String repo)
            throws IOException;

    HdLargeFileStore getLargeFileStore();

}
