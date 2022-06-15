package com.haberdashervcs.client.commands;

import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


final class HelpCommand implements Command {

    private static final HdLogger LOG = HdLoggers.create(HelpCommand.class);


    HelpCommand() {}

    // TODO: List commands.
    @Override
    public void perform() throws Exception {
        LOG.info("Usage: hd <command>");
        LOG.info("For help, see the Getting Started guide at: https://www.haberdashervcs.com/docs");
    }
}
