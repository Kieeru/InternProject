package com.xcy.internproject.cache.document;

import com.xcy.internproject.service.event.DocumentCacheInvalidationRequested;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentCacheEvictionListener {

    private final DocumentCache documentCache;

    public DocumentCacheEvictionListener(DocumentCache documentCache) {
        this.documentCache = documentCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(DocumentCacheInvalidationRequested event) {
        documentCache.evict(event.documentId());
    }
}
