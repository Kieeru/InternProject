package com.xcy.internproject.cache.document;

import com.xcy.internproject.model.Document;

public record DocumentCacheReadResult(DocumentCacheReadState state, Document document) {

    public static DocumentCacheReadResult hit(Document document) {
        return new DocumentCacheReadResult(DocumentCacheReadState.HIT, document);
    }

    public static DocumentCacheReadResult negativeHit() {
        return new DocumentCacheReadResult(DocumentCacheReadState.NEGATIVE_HIT, null);
    }

    public static DocumentCacheReadResult miss() {
        return new DocumentCacheReadResult(DocumentCacheReadState.MISS, null);
    }

    public static DocumentCacheReadResult unavailable() {
        return new DocumentCacheReadResult(DocumentCacheReadState.UNAVAILABLE, null);
    }
}
