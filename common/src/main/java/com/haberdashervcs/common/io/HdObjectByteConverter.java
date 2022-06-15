package com.haberdashervcs.common.io;

import java.io.IOException;

import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.MergeLock;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.objects.RepoEntry;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserWithPassword;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.UserAuthToken;


// TODO: Move server-centric conversions into a server object converter, so that other server classes in common (like
// HdUser) can be moved into the server module.
public interface HdObjectByteConverter {

    byte[] fileToBytes(FileEntry file) throws IOException;
    FileEntry fileFromBytes(byte[] fileBytes) throws IOException;

    byte[] folderToBytes(FolderListing folder) throws IOException;
    FolderListing folderFromBytes(byte[] folderBytes) throws IOException;

    byte[] commitToBytes(CommitEntry commit) throws IOException;
    CommitEntry commitFromBytes(byte[] commitBytes) throws IOException;

    byte[] mergeLockToBytes(MergeLock mergeLock) throws IOException;
    MergeLock mergeLockFromBytes(byte[] mergeLockBytes) throws IOException;

    byte[] branchToBytes(BranchEntry branch) throws IOException;
    BranchEntry branchFromBytes(byte[] branchBytes) throws IOException;

    byte[] userToBytes(HdUser user, String bcryptedPassword) throws IOException;
    HdUserWithPassword userFromBytes(byte[] bytes) throws IOException;

    byte[] userAuthTokenToBytes(UserAuthToken token) throws IOException;
    UserAuthToken userAuthTokenFromBytes(byte[] bytes) throws IOException;

    byte[] mergeResultToBytes(MergeResult mergeResult) throws IOException;
    MergeResult mergeResultFromBytes(byte[] bytes) throws IOException;

    byte[] repoEntryToBytes(RepoEntry repo) throws IOException;
    RepoEntry repoEntryFromBytes(byte[] bytes) throws IOException;

    byte[] serverCheckoutSpecToBytes(ServerCheckoutSpec spec) throws IOException;
    ServerCheckoutSpec serverCheckoutSpecFromBytes(byte[] bytes) throws IOException;

    byte[] clientCheckoutSpecToBytes(ClientCheckoutSpec spec) throws IOException;
    ClientCheckoutSpec clientCheckoutSpecFromBytes(byte[] bytes) throws IOException;

    byte[] subscriptionToBytes(OrgSubscription sub) throws IOException;
    OrgSubscription subscriptionFromBytes(byte[] rowValue) throws IOException;
}
