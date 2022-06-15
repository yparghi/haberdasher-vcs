package com.haberdashervcs.server.frontend;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.user.HdAuthenticator;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.UserAuthToken;
import com.haberdashervcs.common.protobuf.UsersProto;
import com.haberdashervcs.server.datastore.HdDatastore;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;


final class SettingsPageHandler {

    private static final HdLogger LOG = HdLoggers.create(SettingsPageHandler.class);

    private static final DateTimeFormatter dateDisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    private final HdDatastore datastore;
    private final Configuration templateConfig;
    private final HdUserStore userStore;
    private final HdAuthenticator authenticator;

    SettingsPageHandler(
            HdDatastore datastore,
            Configuration templateConfig,
            HdUserStore userStore,
            HdAuthenticator authenticator) {
        this.datastore = datastore;
        this.templateConfig = templateConfig;
        this.userStore = userStore;
        this.authenticator = authenticator;
    }


    void handle(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken token,
            String org,
            String repo,
            Map<String, String[]> params,
            Map<String, Object> pageData)
            throws Exception {

        Template template = templateConfig.getTemplate("settings.ftlh");
        pageData.put("title", "Haberdasher: Settings");
        pageData.put("stylesheetThisPage", "/static/settings.css");
        pageData.put("javascriptIncludesThisPage", ImmutableList.of("/static/settings.js"));

        HdUser user = userStore.getUserById(token.getUserId()).get();
        pageData.put("username", user.getEmail());

        FrontendHttpUtil.putBooleanUrlParam("inviteSent", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("changePlanFailed", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("changePlanSucceeded", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("cancelPlanSucceeded", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("upgradeSuccessful", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("freeTrialEnded", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("paidSubEnded", params, pageData);
        FrontendHttpUtil.putBooleanUrlParam("settingsSaved", params, pageData);

        pageData.put(
                "agreeToProductEmailsCurrently",
                Boolean.parseBoolean(user.getPreferences().getFieldsOrDefault("agreeToProductEmails", "false")));

        Optional<UserAuthToken> existingCliToken = authenticator.getCliTokenForUser(token.getUserId());
        if (existingCliToken.isPresent()) {
            String creationTimeFormatted = String.format(
                    "(CLI Token generated at %s)", formatTime(existingCliToken.get().getCreationTimestampMillis()));
            pageData.put("initialTokenText", creationTimeFormatted);

        } else {
            pageData.put("initialTokenText", "(No CLI token found)");
        }


        if (user.getRole() == HdUser.Role.OWNER || user.getRole() == HdUser.Role.ADMIN) {
            OrgSubscription orgSub = userStore.getSubscription(org).getSub();

            List<OrgUserDisplay> displayUsers = new ArrayList<>();
            for (OrgSubscription.OrgUserEntry orgUser : orgSub.getUsers()) {
                HdUser thisUser = userStore.getUserById(orgUser.getUserId()).get();
                displayUsers.add(new OrgUserDisplay(thisUser.getEmail(), thisUser.getRole()));
            }
            pageData.put("displayUsers", displayUsers);

            pageData.put("allowInvites", orgSub.canInviteNewUsers());

            if (user.getRole() == HdUser.Role.OWNER) {
                BillingDisplay billing = new BillingDisplay(orgSub);
                pageData.put("displayBilling", true);
                pageData.put("billing", billing);
            }
        } else {
            pageData.put("allowInvites", false);
        }

        response.setStatus(HttpStatus.OK_200);
        template.process(pageData, response.getWriter());
    }


    // TODO: Check repo size before changing tiers.
    void showChangePlan(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken userAuthToken,
            String org,
            String repo,
            Map<String, String[]> params,
            Map<String, Object> pageData)
            throws Exception {

        // TODO: Commonize this stuff.
        HdUser user = userStore.getUserById(userAuthToken.getUserId()).get();
        if (user.getRole() != HdUser.Role.OWNER) {
            FrontendHttpUtil.notAuthorized(response, "Invalid permissions");
            return;
        }

        Template template = templateConfig.getTemplate("changePlan.ftlh");
        pageData.put("title", "Haberdasher: Subscription");
        pageData.put("stylesheetThisPage", "/static/settings.css");
        pageData.put("username", user.getEmail());

        response.setStatus(HttpStatus.OK_200);
        template.process(pageData, response.getWriter());
    }


    void doChangePlan(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken userAuthToken,
            String org,
            String repo,
            Map<String, String[]> params, Map<String, Object> pageData)
            throws Exception {

        if (!request.getMethod().equals("POST")) {
            FrontendHttpUtil.methodNotAllowed(response);
            return;
        }

        // TODO: Commonize this stuff. Would it make sense to throw certain exceptions corresponding to certain
        // HTTP errors, like an HdWebNotAuthorizedException or something?
        HdUser user = userStore.getUserById(userAuthToken.getUserId()).get();
        if (user.getRole() != HdUser.Role.OWNER) {
            FrontendHttpUtil.notAuthorized(response, "Invalid permissions");
            return;
        }

        Optional<String> oPlan = FrontendHttpUtil.getFormParam("plan", request, params);
        LOG.info("TEMP: got plan change: %s", oPlan);

        boolean cancelled = false;
        OrgSubscription.BillingPlan newPlan = null;
        switch (oPlan.get()) {
            case "small":
                newPlan = OrgSubscription.BillingPlan.SMALL;
                break;
            case "medium":
                newPlan = OrgSubscription.BillingPlan.MEDIUM;
                break;
            case "large":
                newPlan = OrgSubscription.BillingPlan.LARGE;
                break;
            case "cancel":
                cancelled = true;
                break;
            default:
                FrontendHttpUtil.badRequest(response);
                return;
        }

        if (cancelled == (newPlan != null)) {
            throw new AssertionError(String.format(
                    "Unknown plan change. Cancelled: %s, new plan: %s", cancelled, newPlan));
        }


        OrgSubscription.WithOriginalBytes currentSubWithOriginal = userStore.getSubscription(org);
        OrgSubscription currentSub = currentSubWithOriginal.getSub();

        if (newPlan == currentSub.getBillingPlan()) {
            throw new AssertionError("Change to same plan: " + newPlan);


        } else if (cancelled) {

            HdUserStore.ChangePlanResult result = userStore.cancelBillingPlan(currentSubWithOriginal);
            if (result.getStatus() == HdUserStore.ChangePlanResult.Status.OK) {
                FrontendHttpUtil.redirectToPath(request, response, "/settings?cancelPlanSucceeded=true");
            } else {
                LOG.info("Change plan failed with error message: %s", result.getErrorMessage());
                FrontendHttpUtil.redirectToPath(request, response, "/settings?changePlanFailed=true");
            }
            return;


        } else if (currentSub.getState() == OrgSubscription.State.ON_FREE_TRIAL
                || currentSub.getState() == OrgSubscription.State.ENDED) {
            HdUserStore.InitiateUpgradeResult result = userStore.initiateUpgrade(
                    user, currentSubWithOriginal, newPlan);

            if (result.getStatus() == HdUserStore.InitiateUpgradeResult.Status.OK) {
                String redirectUrl = result.getRedirectUrl();
                response.sendRedirect(redirectUrl);
            } else {
                LOG.info("Upgrade failed with error message: %s", result.getErrorMessage());
                FrontendHttpUtil.redirectToPath(request, response, "/settings?changePlanFailed=true");
            }
            return;


        } else {

            HdUserStore.ChangePlanResult result = userStore.changeBillingPlan(currentSubWithOriginal, newPlan);
            if (result.getStatus() == HdUserStore.ChangePlanResult.Status.OK) {
                FrontendHttpUtil.redirectToPath(request, response, "/settings?changePlanSucceeded=true");
            } else {
                LOG.info("Change plan failed with error message: %s", result.getErrorMessage());
                FrontendHttpUtil.redirectToPath(request, response, "/settings?changePlanFailed=true");
            }
            return;
        }

    }


    void saveUserSettings(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken userAuthToken,
            String org,
            String repo,
            Map<String, String[]> params, Map<String, Object> pageData)
            throws Exception {
        if (!request.getMethod().equals("POST")) {
            FrontendHttpUtil.methodNotAllowed(response);
            return;
        }

        Optional<String> agreeToProductEmails = FrontendHttpUtil.getFormParam("agreeToProductEmails", request, params);
        boolean agrees = ("yes".equals(agreeToProductEmails.orElse(null)));

        HdUser user = userStore.getUserById(userAuthToken.getUserId()).get();
        UsersProto.HdUserPreferences prefs = user.getPreferences();
        UsersProto.HdUserPreferences newPrefs = UsersProto.HdUserPreferences.newBuilder(prefs)
                .putFields("agreeToProductEmails", (agrees ? "true" : "false"))
                .build();

        userStore.updateUserPreferences(user.getUserId(), newPrefs);

        FrontendHttpUtil.redirectToPath(request, response, "/settings?settingsSaved=true");
    }


    void handleNewCliToken(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken userAuthToken,
            String org,
            String repo,
            Map<String, String[]> params, Map<String, Object> pageData)
            throws IOException {
        final String newTokenUuid = UUID.randomUUID().toString();
        UserAuthToken newCliToken = authenticator.createCliToken(userAuthToken.getUserId(), newTokenUuid);
        String out = String.format("{ \"newTokenId\": \"%s\" }", newTokenUuid);
        response.setStatus(HttpStatus.OK_200);
        response.getWriter().write(out);
    }


    public final static class OrgUserDisplay {

        private final String email;
        private final HdUser.Role role;

        private OrgUserDisplay(String email, HdUser.Role role) {
            this.email = email;
            this.role = role;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            switch (role) {
                case ADMIN:
                    return "Administrator";
                case OWNER:
                    return "Owner";
                case AUTHOR:
                    return "Author";
                default:
                    throw new IllegalStateException("Unknown role: " + role);
            }
        }
    }


    public static final class BillingDisplay {

        private final OrgSubscription orgSub;

        private BillingDisplay(OrgSubscription orgSub) {
            this.orgSub = orgSub;
        }

        public String getState() {
            String state;
            switch (orgSub.getState()) {
                case ON_FREE_TRIAL:
                    state = "Free trial";
                    break;
                case PAID:
                    state = "Subscription active";
                    break;
                case GRACE_PERIOD:
                    state = "Subscription ended, grace period in effect";
                    break;
                case ENDED:
                    state = "Subscription cancelled";
                    break;
                default:
                    throw new IllegalStateException("Unknown billing plan: " + orgSub.getState());
            }

            return String.format("%s (%s)", state, getPlanDisplay());
        }

        private String getPlanDisplay() {
            switch (orgSub.getBillingPlan()) {
                case FREE_TRIAL:
                    return "Ending " + formatTime(orgSub.getFreeTrialEndDateMillis());
                case SMALL:
                    return "Small";
                case MEDIUM:
                    return "Medium";
                case LARGE:
                    return "Large";
                default:
                    throw new IllegalStateException("Unknown billing plan: " + orgSub.getBillingPlan());
            }
        }
    }


    private static String formatTime(long millis) {
        ZonedDateTime timeUtc = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
        return dateDisplayFormatter.format(timeUtc) + " UTC";
    }

}
