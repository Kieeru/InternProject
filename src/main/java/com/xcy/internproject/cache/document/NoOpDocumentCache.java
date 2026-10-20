package com.xcy.internproject.cache.document;

import com.xcy.internproject.model.Document;
import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;

public final class NoOpDocumentCache implements DocumentCache {

    private final CacheMetricsRecorder metrics;

    public NoOpDocumentCache(CacheMetricsRecorder metrics) {
        this.metrics = metrics;
    }

    @Override
    public DocumentCacheReadResult get(Long documentId) {
        metrics.recordBypass(CacheArea.DOCUMENT);
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
}
