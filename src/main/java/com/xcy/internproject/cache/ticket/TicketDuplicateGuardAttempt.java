package com.xcy.internproject.cache.ticket;

public record TicketDuplicateGuardAttempt(
        TicketDuplicateGuardState state,
        String fingerprint,
        String token) {

    public static TicketDuplicateGuardAttempt acquired(String fingerprint, String token) {
        return new TicketDuplicateGuardAttempt(
                TicketDuplicateGuardState.ACQUIRED, fingerprint, token);
    }

    public static TicketDuplicateGuardAttempt duplicate() {
        return new TicketDuplicateGuardAttempt(
                TicketDuplicateGuardState.DUPLICATE, null, null);
    }

    public static TicketDuplicateGuardAttempt unavailable() {
        return new TicketDuplicateGuardAttempt(
                TicketDuplicateGuardState.UNAVAILABLE, null, null);
    }
}
