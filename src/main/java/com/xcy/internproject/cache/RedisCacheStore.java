package com.xcy.internproject.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;

import java.time.Duration;

public final class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public CacheReadResult get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return new CacheReadResult(CacheReadState.MISS, null);
            }
            return new CacheReadResult(CacheReadState.HIT, value);
        } catch (DataAccessException exception) {
            return new CacheReadResult(CacheReadState.UNAVAILABLE, null);
        }
    }

    @Override
    public boolean put(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    @Override
    public CacheDeleteResult delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key))
                    ? CacheDeleteResult.DELETED
                    : CacheDeleteResult.NOT_FOUND;
        } catch (DataAccessException exception) {
            return CacheDeleteResult.UNAVAILABLE;
        }
    }
}
