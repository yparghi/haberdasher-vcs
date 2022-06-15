package com.haberdashervcs.server.frontend;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public final class StubEmailSender implements EmailSender {

    private static final HdLogger LOG = HdLoggers.create(StubEmailSender.class);


    private static final StubEmailSender INSTANCE = new StubEmailSender();

    public static StubEmailSender getInstance() {
        return INSTANCE;
    }


    private StubEmailSender() {}

    @Override
    public Result send(Email email) throws Exception {
        LOG.info("Stub-sending email: %s", email.debugStringWithFullBody());
        return Result.OK;
    }
}
