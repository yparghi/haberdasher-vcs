package com.haberdashervcs.common.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;


final class LoggingFile {

    private final FileWriter logFile;

    LoggingFile(Path logPath) throws IOException {
        logFile = new FileWriter(logPath.toFile(), true /* append */);
    }

    synchronized void writeLine(String line) {
        try {
            logFile.append(line + "\n");
            logFile.flush();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
