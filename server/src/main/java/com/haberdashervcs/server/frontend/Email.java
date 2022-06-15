package com.haberdashervcs.server.frontend;

import com.google.common.base.MoreObjects;


public final class Email {

    private static final String FROM = "hello@haberdashervcs.com";


    public static Email of(String to, String subject, String body) {
        return new Email(to, subject, body);
    }


    private final String to;
    private final String subject;
    private final String body;

    private Email(String to, String subject, String body) {
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    public String getFrom() {
        return FROM;
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return debug(body.substring(0, 20) + "...");
    }

    public String debugStringWithFullBody() {
        return debug(body);
    }

    private String debug(String bodyContentsString) {
        return MoreObjects.toStringHelper(this)
                .add("from", FROM)
                .add("to", to)
                .add("subject", subject)
                .add("body", bodyContentsString)
                .toString();
    }
}
