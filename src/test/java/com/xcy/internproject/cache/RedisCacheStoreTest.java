package com.xcy.internproject.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCacheStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisCacheStore cacheStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheStore = new RedisCacheStore(redisTemplate);
    }

    @Test
    void reportsUnavailableWhenRedisConnectionFails() {
        when(valueOperations.get("document:1"))
                .thenThrow(new RedisConnectionFailureException("Redis 连接失败"));

        CacheReadResult result = cacheStore.get("document:1");

        assertThat(result.state()).isEqualTo(CacheReadState.UNAVAILABLE);
        assertThat(result.value()).isNull();
    }

    @Test
    void reportsUnavailableWhenRedisCommandTimesOut() {
        when(valueOperations.get("document:2"))
                .thenThrow(new QueryTimeoutException("Redis 命令超时"));

        CacheReadResult result = cacheStore.get("document:2");

        assertThat(result.state()).isEqualTo(CacheReadState.UNAVAILABLE);
        assertThat(result.value()).isNull();
    }

    @Test
    void returnsFalseWhenRedisWriteOrDeleteFails() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis 写入失败"))
                .when(valueOperations).set("document:3", "{}", Duration.ofMinutes(1));
        when(redisTemplate.delete("document:3"))
                .thenThrow(new QueryTimeoutException("Redis 删除超时"));

        assertThat(cacheStore.put("document:3", "{}", Duration.ofMinutes(1))).isFalse();
        assertThat(cacheStore.delete("document:3")).isEqualTo(CacheDeleteResult.UNAVAILABLE);
    }
}
