package com.haberdashervcs.common.objects.user;


public final class AuthResult {

    public static AuthResult of(Type type, String message) {
        return new AuthResult(type, message);
    }


    public enum Type {
        PERMITTED,
        FORBIDDEN,
        AUTH_EXPIRED
    }

    private final Type type;
    private final String message;

    private AuthResult(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
