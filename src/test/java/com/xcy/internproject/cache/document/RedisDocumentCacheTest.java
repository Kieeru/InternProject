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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDocumentCacheTest {

    private RedisCacheProperties properties;
    private RecordingCacheStore cacheStore;
    private RedisDocumentCache documentCache;
    private CacheMetricsRecorder metrics;

    @BeforeEach
    void setUp() {
        properties = new RedisCacheProperties();
        properties.setKeyPrefix("intern:test:unit");
        properties.setDocumentTtl(Duration.ofMinutes(10));
        properties.setDocumentTtlJitter(Duration.ZERO);
        cacheStore = new RecordingCacheStore();
        metrics = new CacheMetricsRecorder();
        documentCache = new RedisDocumentCache(
                cacheStore,
                new CacheKeyFactory(properties),
                new CacheTtlPolicy(properties),
                properties,
                new ObjectMapper(),
                metrics);
    }

    @Test
    void storesVersionedJsonAndReadsDocumentBack() {
        Document document = new Document(7L, "Redis JSON", "中文正文\n第二行");

        boolean stored = documentCache.put(document);
        DocumentCacheReadResult result = documentCache.get(document.getId());

        assertThat(stored).isTrue();
        assertThat(cacheStore.lastKey).isEqualTo("intern:test:unit:document:v1:7");
        assertThat(cacheStore.lastValue).contains("\"type\":\"FOUND\"");
        assertThat(cacheStore.lastTtl).isEqualTo(Duration.ofMinutes(10));
        assertThat(result.state()).isEqualTo(DocumentCacheReadState.HIT);
        assertThat(result.document().getId()).isEqualTo(7L);
        assertThat(result.document().getTitle()).isEqualTo("Redis JSON");
        assertThat(result.document().getContent()).isEqualTo("中文正文\n第二行");
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).hit()).isEqualTo(1);
    }

    @Test
    void reportsMissWithoutWriting() {
        DocumentCacheReadResult result = documentCache.get(8L);

        assertThat(result.state()).isEqualTo(DocumentCacheReadState.MISS);
        assertThat(result.document()).isNull();
        assertThat(cacheStore.putCalls).isZero();
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).miss()).isEqualTo(1);
    }

    @Test
    void storesMissingMarkerWithShortTtlAndReadsNegativeHit() {
        properties.setMissingDocumentTtl(Duration.ofMinutes(1));
        properties.setMissingDocumentTtlJitter(Duration.ZERO);

        boolean stored = documentCache.putMissing(404L);
        DocumentCacheReadResult result = documentCache.get(404L);

        assertThat(stored).isTrue();
        assertThat(cacheStore.lastKey).isEqualTo("intern:test:unit:document:v1:404");
        assertThat(cacheStore.lastValue).contains("\"type\":\"MISSING\"");
        assertThat(cacheStore.lastTtl).isEqualTo(Duration.ofMinutes(1));
        assertThat(result.state()).isEqualTo(DocumentCacheReadState.NEGATIVE_HIT);
        assertThat(result.document()).isNull();
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).negativeHit()).isEqualTo(1);
    }

    @Test
    void removesCorruptedJsonAndReportsUnavailable() {
        cacheStore.values.put("intern:test:unit:document:v1:15", "不是合法 JSON");

        DocumentCacheReadResult result = documentCache.get(15L);

        assertThat(result.state()).isEqualTo(DocumentCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deleteCalls).isEqualTo(1);
        assertThat(cacheStore.values).doesNotContainKey("intern:test:unit:document:v1:15");
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).error()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableEvenWhenDeletingCorruptedJsonFails() {
        cacheStore.values.put("intern:test:unit:document:v1:16", "{\"type\":");
        cacheStore.deleteSucceeds = false;

        DocumentCacheReadResult result = documentCache.get(16L);

        assertThat(result.state()).isEqualTo(DocumentCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deleteCalls).isEqualTo(1);
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).error()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "{}",
            "{\"type\":\"FOUND\",\"id\":99,\"title\":\"标题\",\"content\":\"正文\"}",
            "{\"type\":\"FOUND\",\"id\":17,\"title\":null,\"content\":\"正文\"}",
            "{\"type\":\"MISSING\",\"id\":17,\"title\":null,\"content\":null}"
    })
    void rejectsSyntacticallyValidButSemanticallyInvalidPayload(String json) {
        cacheStore.values.put("intern:test:unit:document:v1:17", json);

        DocumentCacheReadResult result = documentCache.get(17L);

        assertThat(result.state()).isEqualTo(DocumentCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deleteCalls).isEqualTo(1);
        assertThat(cacheStore.values).doesNotContainKey("intern:test:unit:document:v1:17");
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).error()).isEqualTo(1);
    }

    @Test
    void bypassesDocumentLargerThanConfiguredLimit() {
        properties.setMaxDocumentCacheBytes(80L);
        Document oversized = new Document(9L, "大文档", "正".repeat(100));

        boolean stored = documentCache.put(oversized);

        assertThat(stored).isFalse();
        assertThat(cacheStore.putCalls).isZero();
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).bypass()).isEqualTo(1);
    }

    @Test
    void recordsErrorWhenCacheWriteIsUnavailable() {
        cacheStore.putSucceeds = false;

        boolean stored = documentCache.put(new Document(18L, "写入失败", "仍不影响数据库结果"));

        assertThat(stored).isFalse();
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).error()).isEqualTo(1);
    }

    @Test
    void recordsErrorWhenCacheEvictionIsUnavailable() {
        cacheStore.deleteSucceeds = false;

        boolean evicted = documentCache.evict(19L);

        assertThat(evicted).isFalse();
        assertThat(cacheStore.lastKey).isEqualTo("intern:test:unit:document:v1:19");
        assertThat(metrics.snapshot(CacheArea.DOCUMENT).error()).isEqualTo(1);
    }

    @Test
    void noOpDocumentCacheReportsUnavailable() {
        CacheMetricsRecorder noOpMetrics = new CacheMetricsRecorder();
        DocumentCache cache = new NoOpDocumentCache(noOpMetrics);

        DocumentCacheReadResult result = cache.get(1L);

        assertThat(result.state()).isEqualTo(DocumentCacheReadState.UNAVAILABLE);
        assertThat(cache.put(new Document(1L, "标题", "正文"))).isFalse();
        assertThat(cache.putMissing(2L)).isFalse();
        assertThat(cache.evict(1L)).isFalse();
        assertThat(noOpMetrics.snapshot(CacheArea.DOCUMENT).bypass()).isEqualTo(1);
    }

    private static final class RecordingCacheStore implements CacheStore {

        private final Map<String, String> values = new HashMap<>();
        private String lastKey;
        private String lastValue;
        private Duration lastTtl;
        private int putCalls;
        private int deleteCalls;
        private boolean deleteSucceeds = true;
        private boolean putSucceeds = true;

        @Override
        public CacheReadResult get(String key) {
            String value = values.get(key);
            return value == null
                    ? new CacheReadResult(CacheReadState.MISS, null)
                    : new CacheReadResult(CacheReadState.HIT, value);
        }

        @Override
        public boolean put(String key, String value, Duration ttl) {
            lastKey = key;
            lastValue = value;
            lastTtl = ttl;
            putCalls++;
            if (!putSucceeds) {
                return false;
            }
            values.put(key, value);
            return value.getBytes(StandardCharsets.UTF_8).length > 0;
        }

        @Override
        public CacheDeleteResult delete(String key) {
            lastKey = key;
            deleteCalls++;
            if (!deleteSucceeds) {
                return CacheDeleteResult.UNAVAILABLE;
            }
            return values.remove(key) != null
                    ? CacheDeleteResult.DELETED
                    : CacheDeleteResult.NOT_FOUND;
        }
    }
}
