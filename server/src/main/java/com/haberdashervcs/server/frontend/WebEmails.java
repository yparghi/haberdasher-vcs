package com.haberdashervcs.server.frontend;

import jakarta.servlet.http.HttpServletRequest;


final class WebEmails {

    private WebEmails() {
        throw new UnsupportedOperationException();
    }



    private static final String PASSWORD_RESET_BODY = ""
            + "<p>This email contains a link to reset your Haberdasher password.</p>"
            + "<p>If you didn't request a password reset, you can email us at support@haberdashervcs.com with questions, or just reply to this email.</p>"
            + "<p>Reset your password by visiting this link:</p>"
            + "<p><a href=\"%s\">%s</a></p>"
            + "<p>Thanks,</p>"
            + "<p>The Haberdasher Team</p>"
            ;

    static Email forPasswordResetRequest(HttpServletRequest request, String toEmailAddress, String resetToken) {
        String url = String.format(
                "%s/resetPassword?resetToken=%s",
                FrontendHttpUtil.getHostUrl(request), FrontendHttpUtil.urlEnc(resetToken));
        return Email.of(
                toEmailAddress,
                "Haberdasher Password Reset",
                String.format(PASSWORD_RESET_BODY, url, url));
    }



    private static final String SIGNUP_TASK_BODY = ""
            + "<p>This email contains a link to create your Haberdasher account and repository.</p>"
            + "<p>If you didn't attempt to sign up, you can email us at support@haberdashervcs.com with questions, or just reply to this email.</p>"
            + "<p>Complete your sign-up by visiting this link:</p>"
            + "<p><a href=\"%s\">%s</a></p>"
            + "<p>Thanks,</p>"
            + "<p>The Haberdasher Team</p>"
            ;

    static Email forSignupRequest(HttpServletRequest request, String toEmailAddress, String taskToken) {
        String url = String.format(
                "%s/signup?signupToken=%s",
                FrontendHttpUtil.getHostUrl(request), FrontendHttpUtil.urlEnc(taskToken));
        return Email.of(
                toEmailAddress,
                "Your sign-up link for Haberdasher",
                String.format(SIGNUP_TASK_BODY, url, url));
    }



    private static final String INVITE_BODY = ""
            + "<p>You've been invited to join the Haberdasher repository <b>%s</b>.</p>"
            + "<p>If you didn't expect this invitation, you can ignore it.</p>"
            + "<p>Complete your sign-up by visiting this link:</p>"
            + "<p><a href=\"%s\">%s</a></p>"
            + "<p>Thanks,</p>"
            + "<p>The Haberdasher Team</p>"
            ;

    static Email forSignupInvite(HttpServletRequest request, String toEmail, String repo, String taskToken) {
        String url = String.format(
                "%s/acceptInvitation?token=%s",
                FrontendHttpUtil.getHostUrl(request), FrontendHttpUtil.urlEnc(taskToken));
        return Email.of(
                toEmail,
                "You've been invited to join the Haberdasher repo " + repo,
                String.format(INVITE_BODY, repo, url, url));
    }


    private static final String DEMO_REQUEST_BODY = ""
            + "<p>Name: %s</p>"
            + "<p>Email: %s</p>"
            + "<p>Company: %s</p>"
            + "<p>Interest: %s</p>"
            ;

    static Email forDemoRequest(String name, String email, String company, String interest) {
        return Email.of(
                "yash@haberdashervcs.com",
                "Demo requested",
                String.format(
                        DEMO_REQUEST_BODY,
                        FrontendHttpUtil.htmlEnc(name),
                        FrontendHttpUtil.htmlEnc(email),
                        FrontendHttpUtil.htmlEnc(company),
                        FrontendHttpUtil.htmlEnc(interest)));
    }

}
