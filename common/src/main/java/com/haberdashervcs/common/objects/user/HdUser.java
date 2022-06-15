package com.haberdashervcs.common.objects.user;

import com.google.common.base.Preconditions;
import com.haberdashervcs.common.protobuf.UsersProto;


public final class HdUser {

    public static HdUser of(
            String userId, String email, String org, Role role, UsersProto.HdUserPreferences preferences) {
        return new HdUser(userId, email, org, role, preferences);
    }


    public enum Role {
        AUTHOR,
        ADMIN,
        OWNER
    }


    private final String userId;
    private final String email;
    private final String org;
    private final Role role;
    private final UsersProto.HdUserPreferences preferences;

    private HdUser(String userId, String email, String org, Role role, UsersProto.HdUserPreferences preferences) {
        this.userId = userId;
        this.email = email;
        this.org = org;
        this.role = role;
        this.preferences = Preconditions.checkNotNull(preferences);
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getOrg() {
        return org;
    }

    public Role getRole() {
        return role;
    }

    public UsersProto.HdUserPreferences getPreferences() {
        return preferences;
    }

}
