package com.haberdashervcs.server.operations.checkout;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;


public final class CheckoutResult {

    public enum Status {
        OK,
        FAILED
    }


    public static CheckoutResult failed(Exception error) {
        String message;
        if (error.getMessage() == null) {
            message = error.getClass().getSimpleName();
        } else {
            message = error.getMessage();
        }
        return new CheckoutResult(Status.FAILED, message);
    }


    public static CheckoutResult ok() {
        return new CheckoutResult(Status.OK, null);
    }


    private final Status status;
    private final @Nullable String errorMessage;

    private CheckoutResult(Status status, @Nullable String errorMessage) {
        Preconditions.checkArgument((status == Status.OK) == (errorMessage == null));
        this.status = Preconditions.checkNotNull(status);
        this.errorMessage = errorMessage;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        Preconditions.checkState(status == Status.FAILED);
        Preconditions.checkState(errorMessage != null);
        return errorMessage;
    }
}
