package com.xcy.internproject.cache.ticket;

public final class NoOpTicketDuplicateGuard implements TicketDuplicateGuard {

    @Override
    public TicketDuplicateGuardAttempt tryAcquire(String title, String description) {
        return TicketDuplicateGuardAttempt.unavailable();
    }

    @Override
    public boolean release(String fingerprint, String token) {
        return false;
    }
}
