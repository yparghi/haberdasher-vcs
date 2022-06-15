package com.haberdashervcs.client.commands;

import com.haberdashervcs.client.ClientVersionNumber;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


final class VersionCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(VersionCommand.class);


    @Override
    public void perform() throws Exception {
        LOG.info("Haberdasher version: %s", ClientVersionNumber.getVersion());
    }

}
