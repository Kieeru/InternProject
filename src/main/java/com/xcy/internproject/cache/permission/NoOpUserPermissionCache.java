package com.xcy.internproject.cache.permission;

import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;

import java.util.List;

public final class NoOpUserPermissionCache implements UserPermissionCache {

    private final CacheMetricsRecorder metrics;

    public NoOpUserPermissionCache(CacheMetricsRecorder metrics) {
        this.metrics = metrics;
    }

    @Override
    public UserPermissionCacheReadResult get(Long userId) {
        metrics.recordBypass(CacheArea.USER_PERMISSIONS);
        return UserPermissionCacheReadResult.unavailable();
    }

    @Override
    public boolean put(Long userId, List<String> permissionCodes) {
        return false;
    }

    @Override
    public boolean evict(Long userId) {
        return false;
    }
}
