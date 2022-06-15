package com.haberdashervcs.common.logging;

public interface HdLogger {

    void debug(String fmt, Object... args);

    void info(String fmt, Object... args);

    void warn(String fmt, Object... args);

    void error(String fmt, Object... args);

    void exception(Throwable ex, String fmt, Object... args);
}
