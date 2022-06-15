package com.haberdashervcs.common.logging;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.common.base.Throwables;


class DefaultFileAndConsoleLogger implements HdLogger {

    private final Class clazz;
    private final LoggingFile logFile;
    private final DateTimeFormatter dateDisplayFormatter;

    DefaultFileAndConsoleLogger(Class clazz, LoggingFile logFile) {
        this.clazz = clazz;
        this.logFile = logFile;
        this.dateDisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public void debug(String fmt, Object... args) {
        String msg = String.format(fmt, args);
        fileLog("DEBUG", msg);
        //consoleLog(msg);  // TEMP!
    }

    @Override
    public void info(String fmt, Object... args) {
        String msg = String.format(fmt, args);
        fileLog("INFO", msg);
        consoleLog(msg);
    }

    @Override
    public void warn(String fmt, Object... args) {
        String msg = String.format(fmt, args);
        fileLog("WARN", msg);
        consoleLog(msg);
    }

    @Override
    public void error(String fmt, Object... args) {
        String msg = String.format(fmt, args);
        fileLog("ERROR", msg);
        consoleLog(msg);
    }

    @Override
    public void exception(Throwable ex, String fmt, Object... args) {
        String msg = String.format(fmt, args);
        String stack = Throwables.getStackTraceAsString(ex);

        consoleLog(msg + ":\n" + stack);
        fileLog("ERROR", msg + "\n" + stack);
    }

    private void fileLog(String level, String msg) {
        ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        String wholeMsg = String.format(
                "%s [%s] %s %s",
                this.dateDisplayFormatter.format(nowUtc),
                this.clazz.getSimpleName(),
                level,
                msg);
        logFile.writeLine(wholeMsg);
    }

    private void consoleLog(String msg) {
        System.out.println(msg);
    }
}
