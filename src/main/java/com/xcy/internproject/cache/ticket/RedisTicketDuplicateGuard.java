package com.xcy.internproject.cache.ticket;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.config.RedisCacheProperties;
import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedisTicketDuplicateGuard implements TicketDuplicateGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTicketDuplicateGuard.class);
    private static final String UNAVAILABLE_MESSAGE =
            "Redis 工单防重不可用，当前请求按 fail-open 继续写入 MySQL";

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
    private final TicketRequestFingerprint fingerprint;
    private final CacheMetricsRecorder metrics;
    private final AtomicBoolean degradationWarningEmitted = new AtomicBoolean();

    public RedisTicketDuplicateGuard(
            StringRedisTemplate redisTemplate,
            CacheKeyFactory keys,
            RedisCacheProperties properties,
            TicketRequestFingerprint fingerprint,
            CacheMetricsRecorder metrics) {
        this.redisTemplate = redisTemplate;
        this.keys = keys;
        this.properties = properties;
        this.fingerprint = fingerprint;
        this.metrics = metrics;
    }

    @Override
    public TicketDuplicateGuardAttempt tryAcquire(String title, String description) {
        String requestFingerprint = fingerprint.calculate(title, description);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    keys.ticketDuplicate(requestFingerprint),
                    token,
                    properties.getTicketDuplicateWindow());
            if (Boolean.TRUE.equals(acquired)) {
                degradationWarningEmitted.set(false);
                return TicketDuplicateGuardAttempt.acquired(requestFingerprint, token);
            }
            if (Boolean.FALSE.equals(acquired)) {
                degradationWarningEmitted.set(false);
                return TicketDuplicateGuardAttempt.duplicate();
            }
            return unavailable();
        } catch (DataAccessException exception) {
            return unavailable();
        }
    }

    @Override
    public boolean release(String requestFingerprint, String token) {
        try {
            Long deleted = redisTemplate.execute(
                    COMPARE_AND_DELETE,
                    List.of(keys.ticketDuplicate(requestFingerprint)),
                    token);
            return Long.valueOf(1L).equals(deleted);
        } catch (DataAccessException exception) {
            metrics.recordError(CacheArea.TICKET_DUPLICATE);
            return false;
        }
    }

    private TicketDuplicateGuardAttempt unavailable() {
        metrics.recordError(CacheArea.TICKET_DUPLICATE);
        if (degradationWarningEmitted.compareAndSet(false, true)) {
            LOGGER.warn(UNAVAILABLE_MESSAGE);
        }
        return TicketDuplicateGuardAttempt.unavailable();
    }
}
