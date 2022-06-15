package com.haberdashervcs.common.exceptions;


/**
 * A more or less expected error in the course of normal HD operation.
 */
public class HdNormalError extends RuntimeException {

    public HdNormalError(String message) {
        super(message);
    }
}
