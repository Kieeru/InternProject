package com.xcy.internproject.cache;

import java.time.Duration;

public interface CacheStore {

    CacheReadResult get(String key);

    boolean put(String key, String value, Duration ttl);

    CacheDeleteResult delete(String key);
}
