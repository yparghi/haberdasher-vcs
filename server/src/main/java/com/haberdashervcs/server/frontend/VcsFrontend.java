package com.haberdashervcs.server.frontend;


/**
 * Handles CLI push/pull operations. This is the server the command-line client talks to.
 */
public interface VcsFrontend {

    void startInBackground() throws Exception;
}
