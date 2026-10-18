package com.xcy.internproject.cache.document;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.service.event.DocumentCacheInvalidationRequested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class DocumentCacheEvictionListenerTest {

    @Test
    void cacheUnavailabilityDoesNotTurnCommittedDatabaseWriteIntoFailure() {
        DocumentCache unavailableCache = new DocumentCache() {
            @Override
            public DocumentCacheReadResult get(Long documentId) {
                return DocumentCacheReadResult.unavailable();
            }

            @Override
            public boolean put(Document document) {
                return false;
            }

            @Override
            public boolean putMissing(Long documentId) {
                return false;
            }

            @Override
            public boolean evict(Long documentId) {
                return false;
            }
        };
        DocumentCacheEvictionListener listener = new DocumentCacheEvictionListener(unavailableCache);

        assertThatCode(() -> listener.evict(new DocumentCacheInvalidationRequested(7L)))
                .doesNotThrowAnyException();
    }
}
