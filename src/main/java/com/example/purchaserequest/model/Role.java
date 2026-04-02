package com.example.purchaserequest.model;

public enum Role {
    ROLE_USER("一般ユーザー"),
    ROLE_APPROVER("承認者");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
