package com.haberdashervcs.common.objects;


import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public final class MergeResult {

    public static MergeResult of (ResultType resultType, String message, long newCommitIdOnMain) {
        return new MergeResult(resultType, message, newCommitIdOnMain);
    }

    public enum ResultType {
        SUCCESSFUL,
        FAILED
    }

    private final String message;
    private final ResultType resultType;
    private final long newCommitIdOnMain;

    private MergeResult(ResultType resultType, String message, long newCommitIdOnMain) {
        Preconditions.checkState(resultType != ResultType.SUCCESSFUL ^ newCommitIdOnMain > 0);
        this.resultType = resultType;
        this.message = message;
        this.newCommitIdOnMain = newCommitIdOnMain;
    }

    public String getMessage() {
        return message;
    }

    public ResultType getResultType() {
        return resultType;
    }

    public long getNewCommitIdOnMain() {
        return newCommitIdOnMain;
    }

    public String getDisplayString() {
        return MoreObjects.toStringHelper(this)
                .add("resultType", resultType)
                .add("message", message)
                .toString();
    }
}
