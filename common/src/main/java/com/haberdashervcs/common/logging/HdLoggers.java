package com.haberdashervcs.common.logging;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


public final class HdLoggers {

    private static Path logPath = null;
    private static LoggingFile logFile = null;

    private static synchronized void init() {
        if (logPath != null) {
            return;
        }

        String logPathStr = System.getProperty("haberdasher.logging.path");
        if (logPathStr == null) {
            logPath = Paths.get(".", "hd.log");
        } else {
            logPath = Paths.get(logPathStr);
        }

        try {
            logFile = new LoggingFile(logPath);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    public static Path getLogPath() {
        init();
        return logPath;
    }


    public static HdLogger create(Class myClass) {
        init();
        return new DefaultFileAndConsoleLogger(myClass, logFile);
    }


    private HdLoggers() {
        throw new UnsupportedOperationException();
    }

}
