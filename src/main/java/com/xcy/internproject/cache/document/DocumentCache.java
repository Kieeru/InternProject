package com.xcy.internproject.cache.document;

import com.xcy.internproject.model.Document;

public interface DocumentCache {

    DocumentCacheReadResult get(Long documentId);

    boolean put(Document document);

    boolean putMissing(Long documentId);

    boolean evict(Long documentId);
}
