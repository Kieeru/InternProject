package com.xcy.internproject.cache.ticket;

public interface TicketDuplicateGuard {

    TicketDuplicateGuardAttempt tryAcquire(String title, String description);

    boolean release(String fingerprint, String token);
}
