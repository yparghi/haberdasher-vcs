package com.haberdashervcs.server.frontend;


public interface EmailSender {

    enum Result {
        OK,
        FAILED
    }

    Result send(Email email) throws Exception;
}
