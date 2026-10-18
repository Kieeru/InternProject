package com.xcy.internproject.cache.document;

public final class NoOpDocumentRebuildLock implements DocumentRebuildLock {

    @Override
    public DocumentRebuildLockAttempt tryAcquire(Long documentId) {
        return DocumentRebuildLockAttempt.unavailable();
    }

    @Override
    public boolean release(Long documentId, String token) {
        return false;
    }
}
