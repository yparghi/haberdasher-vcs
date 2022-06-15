package com.haberdashervcs.common.objects.user;


import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public final class SignupResult {

    public enum Status {
        OK,
        FAILED
    }


    public static SignupResult ofSuccessful() {
        return new SignupResult(Status.OK, ImmutableList.of());
    }

    public static SignupResult ofFailed(List<String> errorMessages) {
        Preconditions.checkState(!errorMessages.isEmpty());
        return new SignupResult(Status.FAILED, errorMessages);
    }


    private final Status status;
    private final List<String> errorMessages;

    private SignupResult(Status status, List<String> errorMessages) {
        this.status = status;
        this.errorMessages = ImmutableList.copyOf(errorMessages);
    }

    public Status getStatus() {
        return status;
    }

    public List<String> getErrorMessages() {
        Preconditions.checkState(status == Status.FAILED);
        return errorMessages;
    }
}
