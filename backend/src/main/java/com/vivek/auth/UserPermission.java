package com.vivek.auth;

/**
 * Fine-grained permissions assigned in addition to a base Role.
 * Stored as a comma-separated string in the users table.
 */
public enum UserPermission {
    /** Can VIEW columns marked as sensitive (value shown). */
    SENSITIVE_DATA_RO,
    /** Can VIEW and UPDATE columns marked as sensitive. */
    SENSITIVE_DATA_RW;

    public String toAuthority() {
        return name();
    }
}
