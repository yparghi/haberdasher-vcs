package com.haberdashervcs.common.objects.user;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;


public final class SignupTaskCreationResult {

    public static SignupTaskCreationResult ofSuccessful(String taskToken) {
        Preconditions.checkNotNull(taskToken);
        return new SignupTaskCreationResult(Status.OK, taskToken, ImmutableList.of());
    }

    public static SignupTaskCreationResult ofFailed(List<String> errors) {
        Preconditions.checkArgument(!errors.isEmpty());
        return new SignupTaskCreationResult(Status.FAILED, null, errors);
    }


    public enum Status {
        OK,
        FAILED
    }


    private final Status status;
    private final Optional<String> taskToken;
    private final List<String> errorMessages;

    private SignupTaskCreationResult(Status status, @Nullable String taskToken, List<String> errorMessages) {
        this.status = status;
        this.taskToken = Optional.ofNullable(taskToken);
        this.errorMessages = errorMessages;
    }

    public Status getStatus() {
        return status;
    }

    public String getTaskToken() {
        Preconditions.checkState(status == Status.OK);
        return taskToken.get();
    }

    public List<String> getErrorMessages() {
        Preconditions.checkState(status == Status.FAILED);
        return errorMessages;
    }
}
