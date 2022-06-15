package com.haberdashervcs.common.objects.user;

import com.google.common.base.Preconditions;


public final class UserAuthToken {

    public enum Type {
        CLI,
        WEB
    }

    public enum TokenState {
        ACTIVE,
        EXPIRED
    }


    public static UserAuthToken forCli(
            String tokenSha, String userId, String org, long creationTimestamp, TokenState state) {
        return new UserAuthToken(Type.CLI, tokenSha, userId, org, creationTimestamp, state);
    }

    public static UserAuthToken forWeb(
            String tokenSha, String userId, String org, long creationTimestamp, TokenState state) {
        return new UserAuthToken(Type.WEB, tokenSha, userId, org, creationTimestamp, state);
    }


    private final Type type;
    private final String tokenSha;
    private final String userId;
    private final String org;
    private final long creationTimestamp;
    private final TokenState state;

    private UserAuthToken(
            Type type, String tokenSha, String userId, String org, long creationTimestamp, TokenState state) {
        this.type = type;
        this.tokenSha = tokenSha;
        this.userId = userId;
        this.org = org;
        this.creationTimestamp = creationTimestamp;
        this.state = state;
    }

    public Type getType() {
        return type;
    }

    public String getTokenSha() {
        return tokenSha;
    }

    public String getUserId() {
        return userId;
    }

    public String getOrg() {
        return org;
    }

    public long getCreationTimestampMillis() {
        return creationTimestamp;
    }

    public TokenState getState() {
        return state;
    }

    public UserAuthToken expired() {
        Preconditions.checkState(state == TokenState.ACTIVE);
        return new UserAuthToken(type, tokenSha, userId, org, creationTimestamp, TokenState.EXPIRED);
    }
}
