package com.xcy.internproject.cache.document;

public interface DocumentRebuildLock {

    DocumentRebuildLockAttempt tryAcquire(Long documentId);

    boolean release(Long documentId, String token);
}
