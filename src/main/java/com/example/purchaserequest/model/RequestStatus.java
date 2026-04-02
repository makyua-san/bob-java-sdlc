package com.example.purchaserequest.model;

public enum RequestStatus {
    DRAFT("下書き"),
    SUBMITTED("申請済み"),
    APPROVED("承認済み"),
    REJECTED("却下");

    private final String displayName;

    RequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
