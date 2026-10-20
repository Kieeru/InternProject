package com.xcy.internproject.cache.document;

import com.xcy.internproject.cache.CacheReadResult;
import com.xcy.internproject.cache.CacheReadState;
import com.xcy.internproject.cache.CacheDeleteResult;
import com.xcy.internproject.cache.CacheStore;
import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.CacheTtlPolicy;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import com.xcy.internproject.model.Document;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

public final class RedisDocumentCache implements DocumentCache {

    private final CacheStore cacheStore;
    private final CacheKeyFactory keys;
    private final CacheTtlPolicy ttlPolicy;
    private final RedisCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final CacheMetricsRecorder metrics;

    public RedisDocumentCache(
            CacheStore cacheStore,
            CacheKeyFactory keys,
            CacheTtlPolicy ttlPolicy,
            RedisCacheProperties properties,
            ObjectMapper objectMapper,
            CacheMetricsRecorder metrics) {
        this.cacheStore = cacheStore;
        this.keys = keys;
        this.ttlPolicy = ttlPolicy;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public DocumentCacheReadResult get(Long documentId) {
        CacheReadResult result = cacheStore.get(keys.document(documentId));
        if (result.state() == CacheReadState.MISS) {
            metrics.recordMiss(CacheArea.DOCUMENT);
            return DocumentCacheReadResult.miss();
        }
        if (result.state() == CacheReadState.UNAVAILABLE) {
            metrics.recordError(CacheArea.DOCUMENT);
            return DocumentCacheReadResult.unavailable();
        }

        DocumentCachePayload payload;
        try {
            payload = parsePayload(objectMapper.readTree(result.value()));
        } catch (JacksonException exception) {
            return corrupted(documentId);
        }
        if (!isValid(payload, documentId)) {
            return corrupted(documentId);
        }
        if (payload.type() == DocumentCachePayloadType.MISSING) {
            metrics.recordNegativeHit(CacheArea.DOCUMENT);
            return DocumentCacheReadResult.negativeHit();
        }
        metrics.recordHit(CacheArea.DOCUMENT);
        return DocumentCacheReadResult.hit(new Document(
                payload.id(), payload.title(), payload.content()));
    }

    @Override
    public boolean put(Document document) {
        String json;
        try {
            json = objectMapper.writeValueAsString(DocumentCachePayload.found(document));
        } catch (JacksonException exception) {
            metrics.recordError(CacheArea.DOCUMENT);
            return false;
        }
        if (json.getBytes(StandardCharsets.UTF_8).length
                > properties.getMaxDocumentCacheBytes()) {
            metrics.recordBypass(CacheArea.DOCUMENT);
            return false;
        }
        boolean stored = cacheStore.put(
                keys.document(document.getId()), json, ttlPolicy.documentTtl());
        if (!stored) {
            metrics.recordError(CacheArea.DOCUMENT);
        }
        return stored;
    }

    @Override
    public boolean putMissing(Long documentId) {
        String json;
        try {
            json = objectMapper.writeValueAsString(DocumentCachePayload.missing());
        } catch (JacksonException exception) {
            metrics.recordError(CacheArea.DOCUMENT);
            return false;
        }
        boolean stored = cacheStore.put(
                keys.document(documentId), json, ttlPolicy.missingDocumentTtl());
        if (!stored) {
            metrics.recordError(CacheArea.DOCUMENT);
        }
        return stored;
    }

    @Override
    public boolean evict(Long documentId) {
        CacheDeleteResult result = cacheStore.delete(keys.document(documentId));
        if (result == CacheDeleteResult.UNAVAILABLE) {
            metrics.recordError(CacheArea.DOCUMENT);
            return false;
        }
        return true;
    }

    private DocumentCacheReadResult corrupted(Long documentId) {
        cacheStore.delete(keys.document(documentId));
        metrics.recordError(CacheArea.DOCUMENT);
        return DocumentCacheReadResult.unavailable();
    }

    private static boolean isValid(DocumentCachePayload payload, Long documentId) {
        if (payload == null || payload.type() == null) {
            return false;
        }
        if (payload.type() == DocumentCachePayloadType.MISSING) {
            return payload.id() == null
                    && payload.title() == null
                    && payload.content() == null;
        }
        return documentId.equals(payload.id())
                && payload.title() != null
                && payload.content() != null;
    }

    private static DocumentCachePayload parsePayload(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode type = root.get("type");
        if (type == null || !type.isString()) {
            return null;
        }
        if (DocumentCachePayloadType.MISSING.name().equals(type.stringValue())) {
            return isNullOrMissing(root.get("id"))
                    && isNullOrMissing(root.get("title"))
                    && isNullOrMissing(root.get("content"))
                    ? DocumentCachePayload.missing()
                    : null;
        }
        if (!DocumentCachePayloadType.FOUND.name().equals(type.stringValue())) {
            return null;
        }
        JsonNode id = root.get("id");
        JsonNode title = root.get("title");
        JsonNode content = root.get("content");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong()
                || title == null || !title.isString()
                || content == null || !content.isString()) {
            return null;
        }
        return new DocumentCachePayload(
                DocumentCachePayloadType.FOUND,
                id.longValue(),
                title.stringValue(),
                content.stringValue());
    }

    private static boolean isNullOrMissing(JsonNode value) {
        return value == null || value.isNull();
    }

    private enum DocumentCachePayloadType {
        FOUND,
        MISSING
    }

    private record DocumentCachePayload(
            DocumentCachePayloadType type,
            Long id,
            String title,
            String content) {

        private static DocumentCachePayload found(Document document) {
            return new DocumentCachePayload(
                    DocumentCachePayloadType.FOUND,
                    document.getId(),
                    document.getTitle(),
                    document.getContent());
        }

        private static DocumentCachePayload missing() {
            return new DocumentCachePayload(
                    DocumentCachePayloadType.MISSING,
                    null,
                    null,
                    null);
        }
    }
}
