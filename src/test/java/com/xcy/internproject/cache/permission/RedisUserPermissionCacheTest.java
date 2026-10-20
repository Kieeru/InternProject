package com.xcy.internproject.cache.permission;

import com.xcy.internproject.cache.CacheDeleteResult;
import com.xcy.internproject.cache.CacheReadResult;
import com.xcy.internproject.cache.CacheReadState;
import com.xcy.internproject.cache.CacheStore;
import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.CacheTtlPolicy;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUserPermissionCacheTest {

    private RecordingCacheStore cacheStore;
    private CacheMetricsRecorder metrics;
    private RedisUserPermissionCache cache;

    @BeforeEach
    void setUp() {
        RedisCacheProperties properties = new RedisCacheProperties();
        properties.setKeyPrefix("intern:test:unit");
        properties.setUserPermissionTtl(Duration.ofMinutes(5));
        properties.setUserPermissionTtlJitter(Duration.ZERO);
        cacheStore = new RecordingCacheStore();
        metrics = new CacheMetricsRecorder();
        cache = new RedisUserPermissionCache(
                cacheStore,
                new CacheKeyFactory(properties),
                new CacheTtlPolicy(properties),
                new ObjectMapper(),
                metrics);
    }

    @Test
    void storesDistinctSortedJsonArrayWithPermissionTtlAndReadsItBack() {
        boolean stored = cache.put(
                7L,
                List.of("ticket:audit", "document:read", "document:read"));
        UserPermissionCacheReadResult result = cache.get(7L);

        assertThat(stored).isTrue();
        assertThat(cacheStore.lastKey)
                .isEqualTo("intern:test:unit:user-permissions:v1:7");
        assertThat(cacheStore.lastValue)
                .isEqualTo("[\"document:read\",\"ticket:audit\"]");
        assertThat(cacheStore.lastTtl).isEqualTo(Duration.ofMinutes(5));
        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.HIT);
        assertThat(result.permissionCodes())
                .containsExactly("document:read", "ticket:audit");
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).hit()).isEqualTo(1);
    }

    @Test
    void treatsCachedEmptyJsonArrayAsHit() {
        assertThat(cache.put(8L, List.of())).isTrue();

        UserPermissionCacheReadResult result = cache.get(8L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.HIT);
        assertThat(result.permissionCodes()).isEmpty();
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).negativeHit()).isEqualTo(1);
    }

    @Test
    void reportsMissWhenPermissionKeyDoesNotExist() {
        UserPermissionCacheReadResult result = cache.get(9L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.MISS);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).miss()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableWhenRedisReadFails() {
        cacheStore.unavailable = true;

        UserPermissionCacheReadResult result = cache.get(10L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void deletesMalformedJsonAndReportsUnavailable() {
        String key = "intern:test:unit:user-permissions:v1:11";
        cacheStore.values.put(key, "不是 JSON 数组");

        UserPermissionCacheReadResult result = cache.get(11L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deletedKeys).containsExactly(key);
        assertThat(cacheStore.values).doesNotContainKey(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void deletesSemanticallyInvalidArrayAndReportsUnavailable() {
        String key = "intern:test:unit:user-permissions:v1:12";
        cacheStore.values.put(key, "[\"document:read\",null]");

        UserPermissionCacheReadResult result = cache.get(12L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deletedKeys).containsExactly(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void deletesJsonNullAndReportsUnavailable() {
        String key = "intern:test:unit:user-permissions:v1:15";
        cacheStore.values.put(key, "null");

        UserPermissionCacheReadResult result = cache.get(15L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deletedKeys).containsExactly(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[1]",
            "[true]",
            "[{}]",
            "[\"document:read\",1]"
    })
    void deletesArraysContainingNonTextValues(String malformedJson) {
        String key = "intern:test:unit:user-permissions:v1:16";
        cacheStore.values.put(key, malformedJson);

        UserPermissionCacheReadResult result = cache.get(16L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deletedKeys).containsExactly(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\"document:read\",\"document:read\"]",
            "[\"ticket:audit\",\"document:read\"]"
    })
    void deletesArraysThatAreNotDistinctAndSorted(String malformedJson) {
        String key = "intern:test:unit:user-permissions:v1:19";
        cacheStore.values.put(key, malformedJson);

        UserPermissionCacheReadResult result = cache.get(19L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.deletedKeys).containsExactly(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void reportsUnavailableWhenDeletingMalformedJsonFails() {
        String key = "intern:test:unit:user-permissions:v1:17";
        cacheStore.values.put(key, "损坏的 JSON");
        cacheStore.deleteUnavailable = true;

        UserPermissionCacheReadResult result = cache.get(17L);

        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.UNAVAILABLE);
        assertThat(cacheStore.values).containsKey(key);
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void reportsFailedEvictionWhenRedisDeleteIsUnavailable() {
        cacheStore.deleteUnavailable = true;

        boolean evicted = cache.evict(18L);

        assertThat(evicted).isFalse();
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(1);
    }

    @Test
    void evictsOnlyTheRequestedUsersPermissionKey() {
        cacheStore.values.put(
                "intern:test:unit:user-permissions:v1:13", "[\"document:read\"]");
        cacheStore.values.put(
                "intern:test:unit:user-permissions:v1:14", "[\"document:write\"]");

        boolean evicted = cache.evict(13L);

        assertThat(evicted).isTrue();
        assertThat(cacheStore.deletedKeys)
                .containsExactly("intern:test:unit:user-permissions:v1:13");
        assertThat(cacheStore.values)
                .containsKey("intern:test:unit:user-permissions:v1:14");
    }

    private static final class RecordingCacheStore implements CacheStore {

        private final Map<String, String> values = new HashMap<>();
        private final java.util.ArrayList<String> deletedKeys = new java.util.ArrayList<>();
        private String lastKey;
        private String lastValue;
        private Duration lastTtl;
        private boolean unavailable;
        private boolean deleteUnavailable;

        @Override
        public CacheReadResult get(String key) {
            if (unavailable) {
                return CacheReadResult.unavailable();
            }
            String value = values.get(key);
            return value == null
                    ? new CacheReadResult(CacheReadState.MISS, null)
                    : new CacheReadResult(CacheReadState.HIT, value);
        }

        @Override
        public boolean put(String key, String value, Duration ttl) {
            if (unavailable) {
                return false;
            }
            lastKey = key;
            lastValue = value;
            lastTtl = ttl;
            values.put(key, value);
            return true;
        }

        @Override
        public CacheDeleteResult delete(String key) {
            if (unavailable || deleteUnavailable) {
                return CacheDeleteResult.UNAVAILABLE;
            }
            deletedKeys.add(key);
            return values.remove(key) == null
                    ? CacheDeleteResult.NOT_FOUND
                    : CacheDeleteResult.DELETED;
        }
    }
}
