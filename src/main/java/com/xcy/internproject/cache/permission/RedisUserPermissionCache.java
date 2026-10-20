package com.xcy.internproject.cache.permission;

import com.xcy.internproject.cache.CacheDeleteResult;
import com.xcy.internproject.cache.CacheReadResult;
import com.xcy.internproject.cache.CacheReadState;
import com.xcy.internproject.cache.CacheStore;
import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.CacheTtlPolicy;
import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class RedisUserPermissionCache implements UserPermissionCache {

    private final CacheStore cacheStore;
    private final CacheKeyFactory keys;
    private final CacheTtlPolicy ttlPolicy;
    private final ObjectMapper objectMapper;
    private final CacheMetricsRecorder metrics;

    public RedisUserPermissionCache(
            CacheStore cacheStore,
            CacheKeyFactory keys,
            CacheTtlPolicy ttlPolicy,
            ObjectMapper objectMapper,
            CacheMetricsRecorder metrics) {
        this.cacheStore = cacheStore;
        this.keys = keys;
        this.ttlPolicy = ttlPolicy;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public UserPermissionCacheReadResult get(Long userId) {
        CacheReadResult result = cacheStore.get(keys.userPermissions(userId));
        if (result.state() == CacheReadState.MISS) {
            metrics.recordMiss(CacheArea.USER_PERMISSIONS);
            return UserPermissionCacheReadResult.miss();
        }
        if (result.state() == CacheReadState.UNAVAILABLE) {
            metrics.recordError(CacheArea.USER_PERMISSIONS);
            return UserPermissionCacheReadResult.unavailable();
        }

        List<String> permissionCodes;
        try {
            JsonNode root = objectMapper.readTree(result.value());
            if (root == null || !root.isArray()) {
                return corrupted(userId);
            }
            permissionCodes = new ArrayList<>(root.size());
            for (JsonNode element : root) {
                if (!element.isString()) {
                    return corrupted(userId);
                }
                permissionCodes.add(element.stringValue());
            }
        } catch (JacksonException exception) {
            return corrupted(userId);
        }
        if (!isDistinctAndSorted(permissionCodes)) {
            return corrupted(userId);
        }

        if (permissionCodes.isEmpty()) {
            metrics.recordNegativeHit(CacheArea.USER_PERMISSIONS);
        } else {
            metrics.recordHit(CacheArea.USER_PERMISSIONS);
        }
        return UserPermissionCacheReadResult.hit(permissionCodes);
    }

    @Override
    public boolean put(Long userId, List<String> permissionCodes) {
        List<String> normalized = normalize(permissionCodes);
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (JacksonException exception) {
            metrics.recordError(CacheArea.USER_PERMISSIONS);
            return false;
        }
        boolean stored = cacheStore.put(
                keys.userPermissions(userId), json, ttlPolicy.userPermissionTtl());
        if (!stored) {
            metrics.recordError(CacheArea.USER_PERMISSIONS);
        }
        return stored;
    }

    @Override
    public boolean evict(Long userId) {
        CacheDeleteResult result = cacheStore.delete(keys.userPermissions(userId));
        if (result == CacheDeleteResult.UNAVAILABLE) {
            metrics.recordError(CacheArea.USER_PERMISSIONS);
            return false;
        }
        return true;
    }

    private UserPermissionCacheReadResult corrupted(Long userId) {
        cacheStore.delete(keys.userPermissions(userId));
        metrics.recordError(CacheArea.USER_PERMISSIONS);
        return UserPermissionCacheReadResult.unavailable();
    }

    private static boolean isDistinctAndSorted(List<String> permissionCodes) {
        return permissionCodes.stream().noneMatch(code -> code == null)
                && permissionCodes.equals(normalize(permissionCodes));
    }

    private static List<String> normalize(List<String> permissionCodes) {
        return List.copyOf(new TreeSet<>(permissionCodes));
    }
}
