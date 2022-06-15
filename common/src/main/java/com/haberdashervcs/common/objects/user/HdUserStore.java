package com.haberdashervcs.common.objects.user;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.protobuf.ReposProto;
import com.haberdashervcs.common.protobuf.TasksProto;
import com.haberdashervcs.common.protobuf.UsersProto;


public interface HdUserStore {

    void start() throws Exception;

    Optional<HdUserWithPassword> getUserByEmail(String email) throws IOException;
    Optional<HdUser> getUserById(String userId) throws IOException;
    void updateUserPreferences(String userId, UsersProto.HdUserPreferences newPrefs) throws IOException;


    //////// Signup

    enum SignupType {
        NEW_REPO,
        INVITED_AUTHOR
    }

    SignupTaskCreationResult generateSignupTask(
            String email, String repoName, String password, Map<String, String> metadata)
            throws IOException;

    TasksProto.SignupTask getSignupTask(String signupTaskToken) throws IOException;

    SignupResult performSignup(String signupTaskToken) throws IOException;

    SignupResult performInviteAcceptance(
            String inviteSignupTaskToken, String uncryptedPassword, Map<String, String> metadata)
            throws IOException;


    //////// Subscriptions

    OrgSubscription.WithOriginalBytes getSubscription(String org) throws IOException;

    void updateSubscription(OrgSubscription updated, OrgSubscription.WithOriginalBytes original) throws IOException;


    final class InitiateUpgradeResult {

        public static InitiateUpgradeResult successful(
                String redirectUrl, ReposProto.BillingState updatedBillingState) {
            Preconditions.checkNotNull(redirectUrl);
            Preconditions.checkNotNull(updatedBillingState);
            return new InitiateUpgradeResult(Status.OK, redirectUrl, null, updatedBillingState);
        }

        public static InitiateUpgradeResult failed(String errorMessage) {
            Preconditions.checkNotNull(errorMessage);
            return new InitiateUpgradeResult(Status.FAILED, null, errorMessage, null);
        }

        public enum Status {
            OK,
            FAILED
        }

        private final Status status;
        private final @Nullable String redirectUrl;
        private final @Nullable String errorMessage;
        private final @Nullable ReposProto.BillingState updatedBillingState;

        private InitiateUpgradeResult(
                Status status,
                String redirectUrl,
                String errorMessage,
                ReposProto.BillingState updatedBillingState) {
            this.status = status;
            this.redirectUrl = redirectUrl;
            this.errorMessage = errorMessage;
            this.updatedBillingState = updatedBillingState;
        }

        public Status getStatus() {
            return status;
        }

        public String getRedirectUrl() {
            Preconditions.checkState(status == Status.OK && redirectUrl != null);
            return redirectUrl;
        }

        public String getErrorMessage() {
            Preconditions.checkState(status == Status.FAILED && errorMessage != null);
            return errorMessage;
        }

        public ReposProto.BillingState getUpdatedBillingState() {
            Preconditions.checkState(status == Status.OK && updatedBillingState != null);
            return updatedBillingState;
        }
    }


    // If this ever needs to update the orgsub or the BillingState, we could add that like in InitiateUpgradeResult.
    final class ChangePlanResult {

        public static ChangePlanResult successful() {
            return new ChangePlanResult(Status.OK, null);
        }

        public static ChangePlanResult failed(String errorMessage) {
            Preconditions.checkNotNull(errorMessage);
            return new ChangePlanResult(Status.FAILED, errorMessage);
        }

        public enum Status {
            OK,
            FAILED
        }

        private final Status status;
        private final @Nullable String errorMessage;

        private ChangePlanResult(Status status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public Status getStatus() {
            return status;
        }

        public String getErrorMessage() {
            Preconditions.checkState(status == Status.FAILED);
            return errorMessage;
        }
    }


    InitiateUpgradeResult initiateUpgrade(
            HdUser upgradingUser, OrgSubscription.WithOriginalBytes orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception;

    ChangePlanResult changeBillingPlan(
            OrgSubscription.WithOriginalBytes orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception;

    ChangePlanResult cancelBillingPlan(
            OrgSubscription.WithOriginalBytes orgSub)
            throws Exception;


    //////// Password reset

    Optional<String> generatePasswordResetRequest(String userEmail) throws IOException;
    void performPasswordReset(String resetToken, String newPassword) throws IOException;


    //////// Invitations

    final class InvitationResult {

        public enum Status {
            OK,
            FAILED
        }

        public static InvitationResult alreadyExists() {
            // We don't want to leak the existence of this or that email/account.
            return new InvitationResult(Status.OK, "OK", null);
        }

        public static InvitationResult failed(String message) {
            return new InvitationResult(Status.FAILED, message, null);
        }

        public static InvitationResult shouldSend(String taskToken) {
            return new InvitationResult(Status.OK, "OK", taskToken);
        }


        private final Status status;
        private final String message;
        private @Nullable String taskTokenToSend;

        private InvitationResult(Status status, String message, @Nullable String taskTokenToSend) {
            this.status = status;
            this.message = message;
            this.taskTokenToSend = taskTokenToSend;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public Optional<String> getTaskTokenToSend() {
            return Optional.ofNullable(taskTokenToSend);
        }
    }

    InvitationResult inviteUser(String org, String repo, String email) throws IOException;

}
