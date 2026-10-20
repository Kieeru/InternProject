package com.xcy.internproject.cache.document;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

public final class RedisDocumentRebuildLock implements DocumentRebuildLock {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE =
            new DefaultRedisScript<>("""
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyFactory keys;
    private final RedisCacheProperties properties;

    public RedisDocumentRebuildLock(
            StringRedisTemplate redisTemplate,
            CacheKeyFactory keys,
            RedisCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keys = keys;
        this.properties = properties;
    }

    @Override
    public DocumentRebuildLockAttempt tryAcquire(Long documentId) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    keys.documentRebuildLock(documentId),
                    token,
                    properties.getDocumentRebuildLockTtl());
            if (Boolean.TRUE.equals(acquired)) {
                return DocumentRebuildLockAttempt.acquired(token);
            }
            return acquired == null
                    ? DocumentRebuildLockAttempt.unavailable()
                    : DocumentRebuildLockAttempt.contended();
        } catch (DataAccessException exception) {
            return DocumentRebuildLockAttempt.unavailable();
        }
    }

    @Override
    public boolean release(Long documentId, String token) {
        try {
            Long deleted = redisTemplate.execute(
                    COMPARE_AND_DELETE,
                    List.of(keys.documentRebuildLock(documentId)),
                    token);
            return Long.valueOf(1L).equals(deleted);
        } catch (DataAccessException exception) {
            return false;
        }
    }
}
