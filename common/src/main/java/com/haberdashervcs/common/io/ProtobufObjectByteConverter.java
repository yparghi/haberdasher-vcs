package com.haberdashervcs.common.io;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.haberdashervcs.common.io.rab.ByteArrayRandomAccessBytes;
import com.haberdashervcs.common.io.rab.RandomAccessBytes;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
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
import com.haberdashervcs.common.protobuf.BranchesProto;
import com.haberdashervcs.common.protobuf.CommitsProto;
import com.haberdashervcs.common.protobuf.FilesProto;
import com.haberdashervcs.common.protobuf.FoldersProto;
import com.haberdashervcs.common.protobuf.MergesProto;
import com.haberdashervcs.common.protobuf.ReposProto;
import com.haberdashervcs.common.protobuf.ServerProto;
import com.haberdashervcs.common.protobuf.UsersProto;


public final class ProtobufObjectByteConverter implements HdObjectByteConverter {

    private static final HdLogger LOG = HdLoggers.create(ProtobufObjectByteConverter.class);


    private static final ProtobufObjectByteConverter INSTANCE = new ProtobufObjectByteConverter();

    public static ProtobufObjectByteConverter getInstance() {
        return INSTANCE;
    }


    private ProtobufObjectByteConverter() {
    }

    @Override
    public byte[] fileToBytes(FileEntry file) {
        FilesProto.FileEntry.Builder proto = FilesProto.FileEntry.newBuilder();

        proto.setId(file.getId());

        byte[] contents = RandomAccessBytes.toByteArray(file.getEntryContents());
        proto.setContents(ByteString.copyFrom(contents));

        final FilesProto.FileEntry.ContentsType contentsType;
        switch (file.getContentsType()) {
            case DIFF_GIT:
                contentsType = FilesProto.FileEntry.ContentsType.DIFF_GIT;
                break;
            case FULL:
                contentsType = FilesProto.FileEntry.ContentsType.FULL;
                break;
            default:
                throw new IllegalArgumentException("Unknown contents type: " + file.getContentsType());
        }
        proto.setContentsType(contentsType);

        if (file.getBaseEntryId().isPresent()) {
            proto.setDiffBaseEntryId(file.getBaseEntryId().get());
        }

        FilesProto.FileEntry.StorageType storageType =
                (file.getStorageType() == FileEntry.StorageType.LARGE_FILE_STORE)
                        ? FilesProto.FileEntry.StorageType.LARGE_FILE_STORE
                        : FilesProto.FileEntry.StorageType.DATASTORE;
        proto.setStorageType(storageType);

        return proto.build().toByteArray();
    }


    @Override
    public byte[] folderToBytes(FolderListing folder) {
        FoldersProto.FolderListing.Builder proto = FoldersProto.FolderListing.newBuilder();
        proto.setPath(folder.getPath());
        proto.setBranch(folder.getBranch());
        proto.setCommitId(folder.getCommitId());
        if (folder.getMergeLockId().isPresent()) {
            proto.setMergeLockId(folder.getMergeLockId().get());
        }
        for (FolderListing.Entry entry : folder.getEntries()) {
            FoldersProto.FolderListingEntry.Builder entryProto = FoldersProto.FolderListingEntry.newBuilder();
            entryProto.setName(entry.getName());
            entryProto.setId(entry.getId());
            entryProto.setType(
                    (entry.getType() == FolderListing.Entry.Type.FILE)
                            ? FoldersProto.FolderListingEntry.Type.FILE : FoldersProto.FolderListingEntry.Type.FOLDER);
            proto.addEntries(entryProto.build());
        }
        return proto.build().toByteArray();
    }


    @Override
    public byte[] commitToBytes(CommitEntry commit) {
        CommitsProto.CommitEntry.Builder proto = CommitsProto.CommitEntry.newBuilder();
        proto.setBranchName(commit.getBranchName());
        proto.setCommitId(commit.getCommitId());
        proto.setAuthor(commit.getAuthorUserId());
        proto.setMessage(commit.getMessage());
        proto.addAllChangedPaths(
                commit.getChangedPaths().stream()
                        .map(pojoChange -> changedPathToProto(pojoChange))
                        .collect(Collectors.toList()));
        if (commit.getIntegration().isPresent()) {
            proto.setIntegrationFromMain(commit.getIntegration().get());
        }
        return proto.build().toByteArray();
    }


    private CommitsProto.CommitChangedPath changedPathToProto(CommitEntry.CommitChangedPath pojo) {
        CommitsProto.CommitChangedPath.ChangeType pType;
        switch (pojo.getChangeType()) {
            case ADD:
                pType = CommitsProto.CommitChangedPath.ChangeType.ADD;
                break;
            case DELETE:
                pType = CommitsProto.CommitChangedPath.ChangeType.DELETE;
                break;
            case DIFF:
            default:
                pType = CommitsProto.CommitChangedPath.ChangeType.MODIFY;
                break;
        }

        CommitsProto.CommitChangedPath.Builder out = CommitsProto.CommitChangedPath.newBuilder()
                .setPath(pojo.getPath())
                .setChangeType(pType);
        if (pojo.getPreviousId().isPresent()) {
            out.setPreviousId(pojo.getPreviousId().get());
        }
        if (pojo.getThisId().isPresent()) {
            out.setThisId(pojo.getThisId().get());
        }
        return out.build();
    }


    @Override
    public FileEntry fileFromBytes(byte[] fileBytes) throws IOException {
        FilesProto.FileEntry proto = FilesProto.FileEntry.parseFrom(fileBytes);
        FileEntry.StorageType storageType =
                (proto.getStorageType() == FilesProto.FileEntry.StorageType.LARGE_FILE_STORE)
                        ? FileEntry.StorageType.LARGE_FILE_STORE
                        : FileEntry.StorageType.DATASTORE;

        RandomAccessBytes contents = ByteArrayRandomAccessBytes.of(proto.getContents().toByteArray());
        if (proto.getContentsType() == FilesProto.FileEntry.ContentsType.FULL) {
            return FileEntry.forFullContents(proto.getId(), contents, storageType);

        } else if (proto.getContentsType() == FilesProto.FileEntry.ContentsType.DIFF_GIT) {
            return FileEntry.forDiffGit(
                    proto.getId(), contents, proto.getDiffBaseEntryId(), storageType);

        } else {
            throw new IllegalArgumentException("Unknown contents type: " + proto.getContentsType());
        }
    }

    @Override
    public FolderListing folderFromBytes(byte[] folderBytes) throws IOException {
        FoldersProto.FolderListing proto = FoldersProto.FolderListing.parseFrom(folderBytes);
        ImmutableList.Builder<FolderListing.Entry> entries = ImmutableList.builder();

        for (FoldersProto.FolderListingEntry protoEntry : proto.getEntriesList()) {
            if (protoEntry.getType() == FoldersProto.FolderListingEntry.Type.FILE) {
                entries.add(FolderListing.Entry.forFile(protoEntry.getName(), protoEntry.getId()));
            } else {
                entries.add(FolderListing.Entry.forSubFolder(protoEntry.getName()));
            }
        }

        if (proto.getMergeLockId().isEmpty()) {
            return FolderListing.withoutMergeLock(
                    entries.build(), proto.getPath(), proto.getBranch(), proto.getCommitId());
        } else {
            return FolderListing.withMergeLock(
                    entries.build(), proto.getPath(), proto.getBranch(), proto.getCommitId(), proto.getMergeLockId());
        }
    }


    @Override
    public CommitEntry commitFromBytes(byte[] commitBytes) throws IOException {
        CommitsProto.CommitEntry proto = CommitsProto.CommitEntry.parseFrom(commitBytes);

        CommitEntry pojoCommit = CommitEntry.of(
                proto.getBranchName(),
                proto.getCommitId(),
                proto.getAuthor(),
                proto.getMessage(),
                proto.getChangedPathsList().stream()
                        .map(protoChange -> changedPathFromProto(protoChange))
                        .collect(Collectors.toList()));

        if (proto.hasIntegrationFromMain()) {
            return pojoCommit.withIntegration(proto.getIntegrationFromMain());
        } else {
            return pojoCommit;
        }
    }


    private CommitEntry.CommitChangedPath changedPathFromProto(CommitsProto.CommitChangedPath proto) {
        CommitEntry.CommitChangedPath.ChangeType pojoType;
        switch (proto.getChangeType()) {
            case ADD:
                return CommitEntry.CommitChangedPath.added(proto.getPath(), proto.getThisId());
            case DELETE:
                return CommitEntry.CommitChangedPath.deleted(proto.getPath(), proto.getPreviousId());
            case MODIFY:
                return CommitEntry.CommitChangedPath.diff(
                        proto.getPath(), proto.getPreviousId(), proto.getThisId());
            default:
                throw new IllegalArgumentException("Unknown change type: " + proto.getChangeType());
        }
    }


    @Override
    public byte[] mergeLockToBytes(MergeLock mergeLock) throws IOException {
        MergesProto.MergeLock.Builder out = MergesProto.MergeLock.newBuilder();
        out.setId(mergeLock.getId());
        out.setBranchName(mergeLock.getBranchName());
        final MergesProto.MergeLock.State state;
        switch (mergeLock.getState()) {
            case COMPLETED:
                state = MergesProto.MergeLock.State.COMPLETED;
                break;
            case FAILED:
                state = MergesProto.MergeLock.State.FAILED;
                break;
            case IN_PROGRESS:
                state = MergesProto.MergeLock.State.IN_PROGRESS;
                break;
            default:
                throw new IllegalArgumentException("Unknown merge state: " + mergeLock.getState());
        }
        out.setState(state);
        out.setTimestampMillis(mergeLock.getTimestampMillis());
        return out.build().toByteArray();
    }

    @Override
    public MergeLock mergeLockFromBytes(byte[] mergeLockBytes) throws IOException {
        MergesProto.MergeLock proto = MergesProto.MergeLock.parseFrom(mergeLockBytes);
        final MergeLock.State state;
        switch (proto.getState()) {
            case COMPLETED:
                state = MergeLock.State.COMPLETED;
                break;
            case IN_PROGRESS:
                state = MergeLock.State.IN_PROGRESS;
                break;
            case FAILED:
                state = MergeLock.State.FAILED;
                break;
            default:
                throw new IllegalArgumentException("Unknown merge state: " + proto.getState());
        }
        return MergeLock.of(proto.getId(), proto.getBranchName(), state, proto.getTimestampMillis());
    }

    @Override
    public BranchEntry branchFromBytes(byte[] branchBytes) throws IOException {
        BranchesProto.BranchEntry proto = BranchesProto.BranchEntry.parseFrom(branchBytes);
        return BranchEntry.of(proto.getName(), proto.getBaseCommitId(), proto.getHeadCommitId());
    }

    @Override
    public byte[] branchToBytes(BranchEntry branch) throws IOException {
        BranchesProto.BranchEntry out = BranchesProto.BranchEntry.newBuilder()
                .setName(branch.getName())
                .setBaseCommitId(branch.getBaseCommitId())
                .setHeadCommitId(branch.getHeadCommitId())
                .build();
        return out.toByteArray();
    }


    @Override
    public byte[] userToBytes(HdUser user, String bcryptedPassword) throws IOException {
        UsersProto.HdUser.Role role;
        switch (user.getRole()) {

            case AUTHOR:
                role = UsersProto.HdUser.Role.AUTHOR;
                break;
            case ADMIN:
                role = UsersProto.HdUser.Role.ADMIN;
                break;
            case OWNER:
                role = UsersProto.HdUser.Role.OWNER;
                break;
            default:
                throw new IllegalStateException("Unknown role from user pojo: " + user.getRole());
        }

        return UsersProto.HdUser.newBuilder()
                .setUserId(user.getUserId())
                .setEmail(user.getEmail())
                .setOrg(user.getOrg())
                .setBcryptedPassword(bcryptedPassword)
                .setRole(role)
                .setPreferences(user.getPreferences())
                .build()
                .toByteArray();
    }


    @Override
    public byte[] userAuthTokenToBytes(UserAuthToken token) throws IOException {
        UsersProto.UserAuthToken.Type protoType = (
                token.getType() == UserAuthToken.Type.WEB
                        ? UsersProto.UserAuthToken.Type.WEB
                        : UsersProto.UserAuthToken.Type.CLI);

        UsersProto.UserAuthToken.TokenState protoState = (
                (token.getState() == UserAuthToken.TokenState.ACTIVE)
                        ? UsersProto.UserAuthToken.TokenState.ACTIVE
                        : UsersProto.UserAuthToken.TokenState.EXPIRED);

        return UsersProto.UserAuthToken.newBuilder()
                .setType(protoType)
                .setTokenSha(token.getTokenSha())
                .setUserId(token.getUserId())
                .setOrg(token.getOrg())
                .setCreationTimestamp(token.getCreationTimestampMillis())
                .setState(protoState)
                .build()
                .toByteArray();
    }


    @Override
    public byte[] mergeResultToBytes(MergeResult mergeResult) throws IOException {
        ServerProto.MergeResult.ResultType type;
        switch (mergeResult.getResultType()) {
            case SUCCESSFUL:
                type = ServerProto.MergeResult.ResultType.SUCCESSFUL;
                break;
            case FAILED:
                type = ServerProto.MergeResult.ResultType.FAILED;
                break;
            default:
                throw new IOException("Unexpected type: " + mergeResult.getResultType());
        }
        return ServerProto.MergeResult.newBuilder()
                .setType(type)
                .setMessage(mergeResult.getMessage())
                .setNewCommitIdOnMain(mergeResult.getNewCommitIdOnMain())
                .build()
                .toByteArray();
    }


    @Override
    public byte[] repoEntryToBytes(RepoEntry repo) throws IOException {
        return ReposProto.RepoEntry.newBuilder()
                .setOrg(repo.getOrg())
                .setRepoName(repo.getRepoName())
                .build()
                .toByteArray();
    }


    @Override
    public HdUserWithPassword userFromBytes(byte[] bytes) throws IOException {
        UsersProto.HdUser proto = UsersProto.HdUser.parseFrom(bytes);
        HdUser.Role role;
        switch (proto.getRole()) {
            case AUTHOR:
                role = HdUser.Role.AUTHOR;
                break;
            case ADMIN:
                role = HdUser.Role.ADMIN;
                break;
            case OWNER:
                role = HdUser.Role.OWNER;
                break;
            case UNRECOGNIZED:
            default:
                throw new IllegalStateException("Unknown user role from proto: " + proto.getRole());
        }

        HdUser user = HdUser.of(
                proto.getUserId(), proto.getEmail(), proto.getOrg(), role, proto.getPreferences());
        String password = proto.getBcryptedPassword();
        return new HdUserWithPassword(user, password);
    }


    @Override
    public UserAuthToken userAuthTokenFromBytes(byte[] bytes) throws IOException {
        UsersProto.UserAuthToken proto = UsersProto.UserAuthToken.parseFrom(bytes);
        final UserAuthToken.TokenState state =
                (proto.getState() == UsersProto.UserAuthToken.TokenState.ACTIVE)
                        ? UserAuthToken.TokenState.ACTIVE : UserAuthToken.TokenState.EXPIRED;

        if (proto.getType() == UsersProto.UserAuthToken.Type.WEB) {
            return UserAuthToken.forWeb(
                    proto.getTokenSha(), proto.getUserId(), proto.getOrg(), proto.getCreationTimestamp(), state);

        } else if (proto.getType() == UsersProto.UserAuthToken.Type.CLI) {
            return UserAuthToken.forCli(
                    proto.getTokenSha(), proto.getUserId(), proto.getOrg(), proto.getCreationTimestamp(), state);

        } else {
            throw new IllegalArgumentException();
        }
    }


    @Override
    public MergeResult mergeResultFromBytes(byte[] bytes) throws IOException {
        ServerProto.MergeResult proto = ServerProto.MergeResult.parseFrom(bytes);
        MergeResult.ResultType type;
        switch (proto.getType()) {
            case SUCCESSFUL:
                type = MergeResult.ResultType.SUCCESSFUL;
                break;
            case FAILED:
                type = MergeResult.ResultType.FAILED;
                break;
            default:
                throw new IllegalStateException("Unknown type in MergeResult proto");
        }

        return MergeResult.of(type, proto.getMessage(), proto.getNewCommitIdOnMain());
    }


    @Override
    public RepoEntry repoEntryFromBytes(byte[] bytes) throws IOException {
        ReposProto.RepoEntry proto = ReposProto.RepoEntry.parseFrom(bytes);
        return RepoEntry.of(proto.getOrg(), proto.getRepoName());
    }


    @Override
    public byte[] serverCheckoutSpecToBytes(ServerCheckoutSpec spec) throws IOException {
        return ServerProto.ServerCheckoutSpec.newBuilder()
                .addAllAllFileIdsFromServer(spec.getAllFileIdsFromServer())
                .build()
                .toByteArray();
    }


    @Override
    public ServerCheckoutSpec serverCheckoutSpecFromBytes(byte[] bytes) throws IOException {
        ServerProto.ServerCheckoutSpec proto = ServerProto.ServerCheckoutSpec.parseFrom(bytes);
        return ServerCheckoutSpec.withServerFileIds(proto.getAllFileIdsFromServerList());
    }


    @Override
    public byte[] clientCheckoutSpecToBytes(ClientCheckoutSpec spec) throws IOException {
        return ServerProto.ClientCheckoutSpec.newBuilder()
                .addAllFileIdsClientNeeds(spec.getFileIdsClientNeeds())
                .build()
                .toByteArray();
    }


    @Override
    public ClientCheckoutSpec clientCheckoutSpecFromBytes(byte[] bytes) throws IOException {
        ServerProto.ClientCheckoutSpec proto = ServerProto.ClientCheckoutSpec.parseFrom(bytes);
        return ClientCheckoutSpec.withFilesClientNeeds(proto.getFileIdsClientNeedsList());
    }


    @Override
    public byte[] subscriptionToBytes(OrgSubscription sub) throws IOException {
        ReposProto.OrgSubscription.State state;
        switch (sub.getState()) {
            case ON_FREE_TRIAL:
                state = ReposProto.OrgSubscription.State.FREE_TRIAL;
                break;
            case PAID:
                state = ReposProto.OrgSubscription.State.PAID;
                break;
            case GRACE_PERIOD:
                state = ReposProto.OrgSubscription.State.GRACE_PERIOD;
                break;
            case ENDED:
                state = ReposProto.OrgSubscription.State.ENDED;
                break;
            default:
                throw new IllegalStateException("Unrecognized subscription state from pojo: " + sub.getState());
        }

        ReposProto.OrgSubscription.BillingPlan billingPlan;
        switch (sub.getBillingPlan()) {
            case FREE_TRIAL:
                billingPlan = ReposProto.OrgSubscription.BillingPlan.FREE_TRIAL_PLAN;
                break;
            case SMALL:
                billingPlan = ReposProto.OrgSubscription.BillingPlan.SMALL;
                break;
            case MEDIUM:
                billingPlan = ReposProto.OrgSubscription.BillingPlan.MEDIUM;
                break;
            case LARGE:
                billingPlan = ReposProto.OrgSubscription.BillingPlan.LARGE;
                break;
            default:
                throw new IllegalStateException("Unknown pojo billing plan: " + sub.getBillingPlan());
        }

        List<ReposProto.OrgUserEntry> protoUsers = sub.getUsers().stream()
                .map(pojoUser -> protoOrgUserFromPojo(pojoUser))
                .collect(Collectors.toList());

        return ReposProto.OrgSubscription.newBuilder()
                .setOrg(sub.getOrg())
                .setState(state)
                .addAllUsers(protoUsers)
                .setBillingPlan(billingPlan)
                .setBillingState(sub.getBillingState())
                .build()
                .toByteArray();
    }


    @Override
    public OrgSubscription subscriptionFromBytes(byte[] bytes) throws IOException {
        ReposProto.OrgSubscription proto = ReposProto.OrgSubscription.parseFrom(bytes);
        OrgSubscription.State state;
        switch (proto.getState()) {
            case FREE_TRIAL:
                state = OrgSubscription.State.ON_FREE_TRIAL;
                break;
            case PAID:
                state = OrgSubscription.State.PAID;
                break;
            case GRACE_PERIOD:
                state = OrgSubscription.State.GRACE_PERIOD;
                break;
            case ENDED:
                state = OrgSubscription.State.ENDED;
                break;
            case UNRECOGNIZED:
            default:
                throw new IllegalStateException("Unrecognized subscription state from proto: " + proto.getState());
        }


        OrgSubscription.BillingPlan billingPlan;
        switch (proto.getBillingPlan()) {
            case FREE_TRIAL_PLAN:
                billingPlan = OrgSubscription.BillingPlan.FREE_TRIAL;
                break;
            case SMALL:
                billingPlan = OrgSubscription.BillingPlan.SMALL;
                break;
            case MEDIUM:
                billingPlan = OrgSubscription.BillingPlan.MEDIUM;
                break;
            case LARGE:
                billingPlan = OrgSubscription.BillingPlan.LARGE;
                break;
            default:
                throw new IllegalStateException("Unknown proto billing plan: " + proto.getBillingPlan());
        }

        List<OrgSubscription.OrgUserEntry> pojoOrgUsers = proto.getUsersList()
                .stream()
                .map(protoUser -> protoOrgUserToPojo(protoUser))
                .collect(Collectors.toList());

        return OrgSubscription.of(
                proto.getOrg(), state, pojoOrgUsers, billingPlan, proto.getBillingState());
    }


    private OrgSubscription.OrgUserEntry protoOrgUserToPojo(ReposProto.OrgUserEntry protoOrgUser) {
        OrgSubscription.OrgUserEntry.State state;
        switch (protoOrgUser.getState()) {
            case ACTIVE:
                state = OrgSubscription.OrgUserEntry.State.ACTIVE;
                break;
            case REMOVED:
                state = OrgSubscription.OrgUserEntry.State.REMOVED;
                break;
            default:
                throw new IllegalStateException("Bad state: " + protoOrgUser.getState());
        }

        return OrgSubscription.OrgUserEntry.of(
                protoOrgUser.getOrg(), protoOrgUser.getUserId(), state);
    }


    private ReposProto.OrgUserEntry protoOrgUserFromPojo(OrgSubscription.OrgUserEntry pojoOrgUser) {
        ReposProto.OrgUserEntry.State protoState;
        switch (pojoOrgUser.getState()) {
            case ACTIVE:
                protoState = ReposProto.OrgUserEntry.State.ACTIVE;
                break;
            case REMOVED:
                protoState = ReposProto.OrgUserEntry.State.REMOVED;
                break;
            default:
                throw new IllegalStateException("Unknown org user state: " + pojoOrgUser.getState());
        }

        return ReposProto.OrgUserEntry.newBuilder()
                .setOrg(pojoOrgUser.getOrg())
                .setUserId(pojoOrgUser.getUserId())
                .setState(protoState)
                .build();
    }

}
