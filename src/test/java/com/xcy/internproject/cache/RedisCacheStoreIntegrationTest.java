package com.xcy.internproject.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class RedisCacheStoreIntegrationTest {

    private final String key = "intern:test:day2:" + UUID.randomUUID();

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanExactTestKey() {
        redisTemplate.delete(key);
    }

    @Test
    void roundTripsStringAndAppliesTtl() {
        boolean stored = cacheStore.put(key, "{\"title\":\"Redis 往返\"}", Duration.ofSeconds(30));

        CacheReadResult result = cacheStore.get(key);
        Long ttlSeconds = redisTemplate.getExpire(key);

        assertThat(stored).isTrue();
        assertThat(result.state()).isEqualTo(CacheReadState.HIT);
        assertThat(result.value()).isEqualTo("{\"title\":\"Redis 往返\"}");
        assertThat(ttlSeconds).isBetween(1L, 30L);
    }
}
