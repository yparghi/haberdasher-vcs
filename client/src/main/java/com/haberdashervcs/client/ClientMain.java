package com.haberdashervcs.client;

import com.google.common.base.Throwables;
import com.haberdashervcs.client.commands.Command;
import com.haberdashervcs.client.commands.Commands;
import com.haberdashervcs.common.exceptions.HdNormalError;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


public class ClientMain {

    private static final HdLogger LOG = HdLoggers.create(ClientMain.class);


    public static void main(String[] args) {
        try {
            Command command = Commands.parseFromArgs(args);
            command.perform();

        } catch (HdNormalError normalError) {
            LOG.info("%s", normalError.getMessage());
            System.exit(1);

        } catch (Throwable ex) {
            LOG.info("Error: %s\n\nThe full stack trace is in the log at: %s", ex, HdLoggers.getLogPath());

            String stackTrace = Throwables.getStackTraceAsString(ex);
            LOG.debug("Uncaught exception %s\n%s", ex, stackTrace);
            System.exit(1);
        }
    }
}
