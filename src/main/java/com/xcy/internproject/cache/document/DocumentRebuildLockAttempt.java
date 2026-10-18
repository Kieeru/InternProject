package com.xcy.internproject.cache.document;

public record DocumentRebuildLockAttempt(
        DocumentRebuildLockState state,
        String token) {

    public static DocumentRebuildLockAttempt acquired(String token) {
        return new DocumentRebuildLockAttempt(DocumentRebuildLockState.ACQUIRED, token);
    }

    public static DocumentRebuildLockAttempt contended() {
        return new DocumentRebuildLockAttempt(DocumentRebuildLockState.CONTENDED, null);
    }

    public static DocumentRebuildLockAttempt unavailable() {
        return new DocumentRebuildLockAttempt(DocumentRebuildLockState.UNAVAILABLE, null);
    }
}
