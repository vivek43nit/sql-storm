package com.vivek.auth;

public enum Role {
    ADMIN,
    READ_WRITE,
    READ_ONLY;

    public String toAuthority() {
        return "ROLE_" + name();
    }
}
