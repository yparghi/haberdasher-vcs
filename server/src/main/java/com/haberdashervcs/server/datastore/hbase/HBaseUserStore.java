package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.user.BCrypter;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.HdUserWithPassword;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.SignupResult;
import com.haberdashervcs.common.objects.user.SignupTaskCreationResult;
import com.haberdashervcs.common.protobuf.TasksProto;
import com.haberdashervcs.common.protobuf.UsersProto;
import com.haberdashervcs.common.rules.HdNameRules;
import com.haberdashervcs.server.user.HdBillingManager;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.CheckAndMutate;
import org.apache.hadoop.hbase.client.CheckAndMutateResult;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


public final class HBaseUserStore implements HdUserStore {

    private static final HdLogger LOG = HdLoggers.create(HBaseUserStore.class);

    // Found online in a couple places, claiming to capture RFC 5322 more or less. This isn't intended to be perfect,
    // only to be a sanity check.
    private static final Pattern EMAIL_REGEX_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$");


    public static HdUserStore of(Connection conn, HdBillingManager billingManager) {
        return new HBaseUserStore(conn, billingManager);
    }


    private final Connection conn;
    private final HBaseRawHelper helper;
    private final HdObjectByteConverter byteConv;
    private final HdBillingManager billingManager;

    private HBaseUserStore(Connection conn, HdBillingManager billingManager) {
        this.conn = conn;
        this.helper = HBaseRawHelper.forConnection(conn);
        this.byteConv = ProtobufObjectByteConverter.getInstance();
        this.billingManager = billingManager;
    }


    @Override
    public void start() throws Exception {}


    @Override
    public Optional<HdUserWithPassword> getUserByEmail(String email) throws IOException {
        final Table usersTable = conn.getTable(TableName.valueOf("Users"));
        // TODO: Should I just use different columns, rather than different column families?
        final String columnFamilyName = "cfEmailToId";
        byte[] rowKey = email.getBytes(StandardCharsets.UTF_8);
        Get get = new Get(rowKey);
        Result result = usersTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        byte[] value = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes("id"));

        String userId = new String(value, StandardCharsets.UTF_8);
        return getUserWithPasswordById(userId);
    }


    @Override
    public Optional<HdUser> getUserById(String userId) throws IOException {
        Optional<HdUserWithPassword> userWithPass = getUserWithPasswordById(userId);
        if (userWithPass.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(userWithPass.get().getUser());
        }
    }


    @Override
    public void updateUserPreferences(String userId, UsersProto.HdUserPreferences newPrefs) throws IOException {
        // TODO: This should be done through byteConv, but that API is designed for signup, separating the HdUser and
        // its proto's bcryptedPassword, so we need to untangle that.
        byte[] userRowKey = userId.getBytes(StandardCharsets.UTF_8);
        final Table usersTable = conn.getTable(TableName.valueOf("Users"));
        final String cfName = "cfIdToUser";
        Get get = new Get(userRowKey);
        Result result = usersTable.get(get);
        Verify.verify(!result.isEmpty(), "Failed to find userId: %s", userId);
        byte[] originalBytes = result.getValue(Bytes.toBytes(cfName), Bytes.toBytes("user"));
        UsersProto.HdUser originalProto = UsersProto.HdUser.parseFrom(originalBytes);

        UsersProto.HdUser updatedProto = UsersProto.HdUser.newBuilder(originalProto)
                .setPreferences(newPrefs)
                .build();

        Put updatedUserPut = new Put(userRowKey);
        updatedUserPut.addColumn(
                Bytes.toBytes("cfIdToUser"),
                Bytes.toBytes("user"),
                updatedProto.toByteArray());

        CheckAndMutate cAndM = CheckAndMutate.newBuilder(userRowKey)
                .ifEquals(Bytes.toBytes(cfName), Bytes.toBytes("user"), originalBytes)
                .build(updatedUserPut);

        CheckAndMutateResult cmResult = usersTable.checkAndMutate(cAndM);
        if (!cmResult.isSuccess()) {
            throw new IOException("Failed to update user preferences (DB error)");
        }
    }


    private Optional<HdUserWithPassword> getUserWithPasswordById(String userId) throws IOException {
        final Table usersTable = conn.getTable(TableName.valueOf("Users"));
        final String columnFamilyName = "cfIdToUser";
        byte[] rowKey = userId.getBytes(StandardCharsets.UTF_8);
        Get get = new Get(rowKey);
        Result result = usersTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        byte[] value = result.getValue(Bytes.toBytes(columnFamilyName), Bytes.toBytes("user"));

        return Optional.of(byteConv.userFromBytes(value));
    }


    @Override
    public SignupTaskCreationResult generateSignupTask(
            String email, String repoName, String passwordUncrypted, Map<String, String> metadata)
            throws IOException {
        List<String> validationErrors = validateSignup(email, repoName, passwordUncrypted);
        if (!validationErrors.isEmpty()) {
            return SignupTaskCreationResult.ofFailed(validationErrors);
        }

        final String bcryptedPassword = BCrypter.bcryptPassword(passwordUncrypted);

        Optional<String> existingTaskToken = getSignupTaskTokenForEmail(email);
        if (existingTaskToken.isPresent()) {
            // If a previous signup was never completed, just overwrite it and send another email.
            TasksProto.SignupTask existingTask = getSignupTask(existingTaskToken.get());

            if (existingTask.getState() != TasksProto.SignupTask.State.NEW) {
                return SignupTaskCreationResult.ofFailed(ImmutableList.of("This email has already signed up."));
            }

            TasksProto.SignupTask replacement = TasksProto.SignupTask.newBuilder(existingTask)
                    .setRepoName(repoName)
                    .setBcryptedPassword(bcryptedPassword)
                    .setCreationTimestamp(System.currentTimeMillis())
                    .clearMetadata()
                    .putAllMetadata(metadata)
                    .build();
            putSignupTask(replacement);
            return SignupTaskCreationResult.ofSuccessful(existingTaskToken.get());
        }


        final String taskToken = UUID.randomUUID().toString();
        TasksProto.SignupTask newTask = TasksProto.SignupTask.newBuilder()
                .setState(TasksProto.SignupTask.State.NEW)
                .setEmail(email)
                .setRepoName(repoName)
                .setOrg(repoName)
                .setBcryptedPassword(bcryptedPassword)
                .setCreationTimestamp(System.currentTimeMillis())
                .setTaskToken(taskToken)
                .setType(TasksProto.SignupTask.Type.NEW_REPO)
                .putAllMetadata(metadata)
                .build();

        // TODO: Figure out how to make this atomically succeed/fail.
        putSignupTaskTokenForEmail(email, taskToken);
        putSignupTask(newTask);
        return SignupTaskCreationResult.ofSuccessful(taskToken);
    }


    private Optional<String> getSignupTaskTokenForEmail(String email) throws IOException {
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        byte[] taskRowKeyByEmail = String.format(":SIGNUP_EMAIL:%s", email).getBytes(StandardCharsets.UTF_8);
        Get getByEmail = new Get(taskRowKeyByEmail);
        Result result = tasksTable.get(getByEmail);

        if (result.isEmpty()) {
            return Optional.empty();
        } else {
            String existingTaskToken = new String(
                    result.getValue(Bytes.toBytes("cfMain"), Bytes.toBytes("taskToken")),
                    StandardCharsets.UTF_8);
            return Optional.of(existingTaskToken);
        }
    }


    private void putSignupTaskTokenForEmail(String email, String taskToken) throws IOException {
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        byte[] taskRowKeyByEmail = String.format(":SIGNUP_EMAIL:%s", email).getBytes(StandardCharsets.UTF_8);
        Put putByEmail = new Put(taskRowKeyByEmail);
        putByEmail.addColumn(
                Bytes.toBytes("cfMain"), Bytes.toBytes("taskToken"), taskToken.getBytes(StandardCharsets.UTF_8));
        tasksTable.put(putByEmail);
    }


    // We assume we've already put the row for email -> task token.
    private void putSignupTask(TasksProto.SignupTask newTask) throws IOException {
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        byte[] taskRowKeyByToken = String.format(":SIGNUP_TASK:%s", newTask.getTaskToken())
                .getBytes(StandardCharsets.UTF_8);

        Put put = new Put(taskRowKeyByToken);
        put.addColumn(
                Bytes.toBytes("cfMain"),
                Bytes.toBytes("task"),
                newTask.toByteArray());
        tasksTable.put(put);
    }


    @Override
    public TasksProto.SignupTask getSignupTask(String signupTaskToken) throws IOException {
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        byte[] taskRowKeyByToken = String.format(":SIGNUP_TASK:%s", signupTaskToken)
                .getBytes(StandardCharsets.UTF_8);

        Get get = new Get(taskRowKeyByToken);
        Result result = tasksTable.get(get);
        if (result.isEmpty()) {
            throw new IllegalStateException("No signup task found");
        }
        byte[] value = result.getValue(
                Bytes.toBytes("cfMain"), Bytes.toBytes("task"));
        return TasksProto.SignupTask.parseFrom(value);
    }


    // TODO! break this up into separate validations that take 'out' as an input?
    private List<String> validateSignup(String email, String repoName, String password) {
        List<String> out = new ArrayList<>();

        Optional<String> emailError = validateEmail(email);
        if (emailError.isPresent()) {
            out.add(emailError.get());
        }

        List<String> repoNameErrors = HdNameRules.validateRepoName(repoName);
        if (!repoNameErrors.isEmpty()) {
            out.addAll(repoNameErrors);
        }

        Optional<String> validatedPasswordError = validatePassword(password);
        if (validatedPasswordError.isPresent()) {
            out.add(validatedPasswordError.get());
        }

        return out;
    }


    // Returns error message, if any.
    private Optional<String> validateEmail(String email) {
        Matcher matcher = EMAIL_REGEX_PATTERN.matcher(email);
        if (!matcher.matches()) {
            return Optional.of("The email address appears invalid.");
        } else {
            return Optional.empty();
        }
    }


    private Optional<String> validatePassword(String password) {
        if (password.length() < 6) {
            return Optional.of("Password must be at least 6 characters long.");
        }

        return Optional.empty();
    }


    @Override
    public SignupResult performInviteAcceptance(
            String inviteSignupTaskToken, String passwordUncrypted, Map<String, String> metadata)
            throws IOException {
        TasksProto.SignupTask signupTask = getSignupTask(inviteSignupTaskToken);
        if (signupTask.getType() != TasksProto.SignupTask.Type.INVITED_AUTHOR) {
            throw new IllegalStateException("Expected invite, got %s" + signupTask);
        } else if (signupTask.getState() != TasksProto.SignupTask.State.NEW) {
            throw new IllegalStateException("Invite is already completed: %s" + signupTask);
        }

        OrgSubscription orgSub = helper.getSubscription(signupTask.getOrg()).getSub();
        if (!orgSub.canInviteNewUsers()) {
            return SignupResult.ofFailed(ImmutableList.of("This repo can't add any more users."));
        }

        List<String> validationErrors = validateSignup(
                signupTask.getEmail(), signupTask.getRepoName(), passwordUncrypted);
        if (!validationErrors.isEmpty()) {
            return SignupResult.ofFailed(validationErrors);
        }

        TasksProto.SignupTask updatedWithPassword = TasksProto.SignupTask.newBuilder(signupTask)
                .setBcryptedPassword(BCrypter.bcryptPassword(passwordUncrypted))
                .putAllMetadata(metadata)
                .build();
        putSignupTask(updatedWithPassword);

        return performSignup(inviteSignupTaskToken);
    }


    @Override
    public SignupResult performSignup(String signupTaskToken) throws IOException {
        TasksProto.SignupTask signupTask = getSignupTask(signupTaskToken);

        String email = signupTask.getEmail();
        String org = signupTask.getOrg();
        String bcryptedPassword = signupTask.getBcryptedPassword();

        String newUserId = UUID.randomUUID().toString();
        HdUser.Role role;
        switch (signupTask.getType()) {
            case NEW_REPO:
                role = HdUser.Role.OWNER;
                break;
            case INVITED_AUTHOR:
                role = HdUser.Role.AUTHOR;
                break;
            default:
                throw new IllegalStateException("Unrecognized signup task type: " + signupTask.getType());
        }

        UsersProto.HdUserPreferences.Builder prefsFromSignup = UsersProto.HdUserPreferences.newBuilder();
        // TODO: Find a place to keep these field names.
        if (Boolean.parseBoolean(signupTask.getMetadataOrDefault("agreeToProductEmails", null))) {
            prefsFromSignup.putFields("agreeToProductEmails", "true");
        }

        HdUser newUser = HdUser.of(newUserId, email, org, role, prefsFromSignup.build());
        createUser(org, newUser, bcryptedPassword);

        TasksProto.SignupTask updatedWithCompletion = TasksProto.SignupTask.newBuilder(signupTask)
                .setState(TasksProto.SignupTask.State.COMPLETED)
                .build();
        putSignupTask(updatedWithCompletion);

        return SignupResult.ofSuccessful();
    }


    @Override
    public OrgSubscription.WithOriginalBytes getSubscription(String org) throws IOException {
        return helper.getSubscription(org);
    }


    @Override
    public void updateSubscription(
            OrgSubscription updated, OrgSubscription.WithOriginalBytes original)
            throws IOException {
        helper.updateOrgSubscription(updated, original);
    }


    private void createUser(String org, HdUser user, String bcryptedPassword) throws IOException {
        final Table usersTable = conn.getTable(TableName.valueOf("Users"));

        byte[] emailRowKey = user.getEmail().getBytes(StandardCharsets.UTF_8);
        Put emailPut = new Put(emailRowKey);
        emailPut.addColumn(
                Bytes.toBytes("cfEmailToId"),
                Bytes.toBytes("id"),
                user.getUserId().getBytes(StandardCharsets.UTF_8));
        putIfNotExists(emailRowKey, "cfEmailToId", "id", emailPut, usersTable);


        byte[] userRowKey = user.getUserId().getBytes(StandardCharsets.UTF_8);
        Put userPut = new Put(userRowKey);
        userPut.addColumn(
                Bytes.toBytes("cfIdToUser"),
                Bytes.toBytes("user"),
                byteConv.userToBytes(user, bcryptedPassword));
        putIfNotExists(userRowKey, "cfIdToUser", "user", userPut, usersTable);


        OrgSubscription.WithOriginalBytes orgSubOriginal = helper.getSubscription(org);
        List<OrgSubscription.OrgUserEntry> updatedOrgUsers = new ArrayList<>(orgSubOriginal.getSub().getUsers());
        OrgSubscription.OrgUserEntry newOrgUser = OrgSubscription.OrgUserEntry.of(
                org, user.getUserId(), OrgSubscription.OrgUserEntry.State.ACTIVE);
        updatedOrgUsers.add(newOrgUser);
        OrgSubscription updatedOrgSub = OrgSubscription.of(
                org,
                orgSubOriginal.getSub().getState(),
                updatedOrgUsers,
                orgSubOriginal.getSub().getBillingPlan(),
                orgSubOriginal.getSub().getBillingState());

        updateSubscription(updatedOrgSub, orgSubOriginal);

        try {
            if (updatedOrgSub.getBillingPlan() != OrgSubscription.BillingPlan.FREE_TRIAL) {
                ChangePlanResult updateSeatsResult = billingManager.updateSubscriptionSeats(updatedOrgSub);
                if (updateSeatsResult.getStatus() == ChangePlanResult.Status.FAILED) {
                    LOG.error(
                            "Failed to update subscription seats!\n- Error: %s\n- Orgsub: %s",
                            updateSeatsResult.getErrorMessage(), updatedOrgSub);
                }
            }
        } catch (Exception seatEx) {
            throw new IOException(seatEx);
        }
    }


    @Override
    public Optional<String> generatePasswordResetRequest(String userEmail) throws IOException {
        Optional<HdUserWithPassword> userOpt = getUserByEmail(userEmail);
        if (!userOpt.isPresent()) {
            LOG.info("Reset password request for unknown email: %s", userEmail);
            return Optional.empty();
        }

        final String resetToken = UUID.randomUUID().toString();
        TasksProto.ResetPasswordTask resetTask = TasksProto.ResetPasswordTask.newBuilder()
                .setResetToken(resetToken)
                .setUserId(userOpt.get().getUser().getUserId())
                .setState(TasksProto.ResetPasswordTask.State.NEW)
                .build();

        putPasswordResetTask(resetTask);

        return Optional.of(resetToken);
    }


    @Override
    public void performPasswordReset(String resetToken, String newPassword) throws IOException {
        Optional<String> validatePasswordError = validatePassword(newPassword);
        if (validatePasswordError.isPresent()) {
            throw new IllegalArgumentException(validatePasswordError.get());
        }

        LOG.info("Resetting password for token: %s", resetToken);
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        final Table usersTable = conn.getTable(TableName.valueOf("Users"));
        byte[] rowKey = String.format("RESETPASSWORD:%s", resetToken).getBytes(StandardCharsets.UTF_8);

        Get get = new Get(rowKey);
        Result result = tasksTable.get(get);
        if (result.isEmpty()) {
            throw new IllegalStateException("No password reset request found");
        }
        byte[] value = result.getValue(
                Bytes.toBytes("cfMain"), Bytes.toBytes("task"));

        TasksProto.ResetPasswordTask task = TasksProto.ResetPasswordTask.parseFrom(value);
        if (task.getState() != TasksProto.ResetPasswordTask.State.NEW) {
            throw new IllegalStateException("Reset password task is already completed");
        }

        HdUser hdUser = getUserById(task.getUserId()).get();
        String bcryptedPassword = BCrypter.bcryptPassword(newPassword);
        byte[] withNewPasswordRowKey = hdUser.getUserId().getBytes(StandardCharsets.UTF_8);
        Put withNewPasswordPut = new Put(withNewPasswordRowKey);
        withNewPasswordPut.addColumn(
                Bytes.toBytes("cfIdToUser"),
                Bytes.toBytes("user"),
                byteConv.userToBytes(hdUser, bcryptedPassword));
        usersTable.put(withNewPasswordPut);

        TasksProto.ResetPasswordTask completedTask = TasksProto.ResetPasswordTask.newBuilder(task)
                .setState(TasksProto.ResetPasswordTask.State.COMPLETED)
                .build();
        putPasswordResetTask(completedTask);
    }


    private void putPasswordResetTask(TasksProto.ResetPasswordTask resetTask) throws IOException {
        final Table tasksTable = conn.getTable(TableName.valueOf("Tasks"));
        byte[] rowKey = String.format("RESETPASSWORD:%s", resetTask.getResetToken()).getBytes(StandardCharsets.UTF_8);
        Put taskPut = new Put(rowKey);
        taskPut.addColumn(
                Bytes.toBytes("cfMain"),
                Bytes.toBytes("task"),
                resetTask.toByteArray());
        tasksTable.put(taskPut);
    }


    // TODO: Simplify this to create the Put inline, given bytes? Put it somewhere common?
    private void putIfNotExists(
            byte[] rowKey, String cfName, String columnName, Put put, Table table) throws IOException {
        CheckAndMutate cAndM = CheckAndMutate.newBuilder(rowKey)
                .ifNotExists(Bytes.toBytes(cfName), Bytes.toBytes(columnName))
                .build(put);

        CheckAndMutateResult result = table.checkAndMutate(cAndM);
        if (!result.isSuccess()) {
            throw new IOException("CheckAndMutate failed");
        }
    }


    @Override
    public InvitationResult inviteUser(String org, String repo, String email) throws IOException {

        Optional<HdUserWithPassword> existingUser = getUserByEmail(email);
        if (existingUser.isPresent()) {
            return InvitationResult.alreadyExists();
        }

        Optional<String> emailError = validateEmail(email);
        if (emailError.isPresent()) {
            return InvitationResult.failed(emailError.get());
        }


        Optional<String> existingTaskToken = getSignupTaskTokenForEmail(email);
        // It's okay to overwrite a signup if it's not part of an invitation, and it's not completed.
        if (existingTaskToken.isPresent()) {
            TasksProto.SignupTask existingTask = getSignupTask(existingTaskToken.get());
            if (existingTask.getType() == TasksProto.SignupTask.Type.INVITED_AUTHOR) {
                if (existingTask.getOrg().equals(org)) {
                    return InvitationResult.shouldSend(existingTaskToken.get());
                } else {
                    return InvitationResult.alreadyExists();
                }

            } else if (existingTask.getState() == TasksProto.SignupTask.State.COMPLETED) {
                return InvitationResult.alreadyExists();
            }
        }


        String newTaskToken = UUID.randomUUID().toString();
        TasksProto.SignupTask newTask = TasksProto.SignupTask.newBuilder()
                .setState(TasksProto.SignupTask.State.NEW)
                .setEmail(email)
                .setOrg(org)
                .setRepoName(repo)
                .setCreationTimestamp(System.currentTimeMillis())
                .setTaskToken(newTaskToken)
                .setType(TasksProto.SignupTask.Type.INVITED_AUTHOR)
                .build();

        putSignupTaskTokenForEmail(email, newTaskToken);
        putSignupTask(newTask);
        return InvitationResult.shouldSend(newTaskToken);
    }


    @Override
    public InitiateUpgradeResult initiateUpgrade(
            HdUser upgradingUser, OrgSubscription.WithOriginalBytes orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception {
        Preconditions.checkArgument(upgradingUser.getRole() == HdUser.Role.OWNER);

        InitiateUpgradeResult result = billingManager.initiateUpgrade(upgradingUser, orgSub.getSub(), newPlan);

        if (result.getStatus() == InitiateUpgradeResult.Status.OK) {
            OrgSubscription updatedSub = OrgSubscription.of(
                    orgSub.getSub().getOrg(),
                    orgSub.getSub().getState(),
                    orgSub.getSub().getUsers(),
                    orgSub.getSub().getBillingPlan(),
                    result.getUpdatedBillingState());
            updateSubscription(updatedSub, orgSub);
        }

        return result;
    }


    @Override
    public ChangePlanResult changeBillingPlan(
            OrgSubscription.WithOriginalBytes orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception {
        ChangePlanResult result = billingManager.changeBillingPlan(orgSub.getSub(), newPlan);
        if (result.getStatus() == ChangePlanResult.Status.OK) {
            OrgSubscription newSub = OrgSubscription.of(
                    orgSub.getSub().getOrg(),
                    orgSub.getSub().getState(),
                    orgSub.getSub().getUsers(),
                    newPlan,
                    orgSub.getSub().getBillingState());
            updateSubscription(newSub, orgSub);
        }

        return result;
    }


    @Override
    public ChangePlanResult cancelBillingPlan(
            OrgSubscription.WithOriginalBytes orgSub)
            throws Exception {
        ChangePlanResult result = billingManager.cancelBillingPlan(orgSub.getSub());

        if (result.getStatus() == ChangePlanResult.Status.OK) {
            OrgSubscription newSub = OrgSubscription.of(
                    orgSub.getSub().getOrg(),
                    OrgSubscription.State.ENDED,
                    orgSub.getSub().getUsers(),
                    orgSub.getSub().getBillingPlan(),
                    orgSub.getSub().getBillingState());
            updateSubscription(newSub, orgSub);
        }

        return result;
    }

}
