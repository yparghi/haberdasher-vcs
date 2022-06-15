package com.haberdashervcs.common.objects.user;


public final class HdUserWithPassword {

    private final HdUser user;
    private final String bcryptedPassword;

    public HdUserWithPassword(HdUser user, String bcryptedPassword) {
        this.user = user;
        this.bcryptedPassword = bcryptedPassword;
    }

    public HdUser getUser() {
        return user;
    }

    public String getBcryptedPassword() {
        return bcryptedPassword;
    }
}
