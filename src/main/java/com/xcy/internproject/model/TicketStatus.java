package com.xcy.internproject.model;

public enum TicketStatus {
    PENDING,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(TicketStatus targetStatus) {
        return switch (this) {
            case PENDING -> targetStatus == PROCESSING;
            case PROCESSING -> targetStatus == RESOLVED;
            case RESOLVED -> targetStatus == CLOSED;
            case CLOSED -> false;
        };
    }
}
