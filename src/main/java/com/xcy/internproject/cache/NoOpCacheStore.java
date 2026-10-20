package com.xcy.internproject.cache;

import java.time.Duration;

public final class NoOpCacheStore implements CacheStore {

    @Override
    public CacheReadResult get(String key) {
        return CacheReadResult.unavailable();
    }

    @Override
    public boolean put(String key, String value, Duration ttl) {
        return false;
    }

    @Override
    public CacheDeleteResult delete(String key) {
        return CacheDeleteResult.UNAVAILABLE;
    }
}
