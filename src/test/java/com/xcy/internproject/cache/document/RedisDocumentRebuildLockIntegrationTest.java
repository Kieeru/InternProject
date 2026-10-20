package com.xcy.internproject.cache.document;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class RedisDocumentRebuildLockIntegrationTest {

    private final long documentId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);

    @Autowired
    private DocumentRebuildLock rebuildLock;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanExactLockKey() {
        redisTemplate.delete(keys.documentRebuildLock(documentId));
    }

    @Test
    void acquiresWithTtlAndOnlyOwnerTokenCanRelease() {
        DocumentRebuildLockAttempt first = rebuildLock.tryAcquire(documentId);
        DocumentRebuildLockAttempt second = rebuildLock.tryAcquire(documentId);
        Long ttlSeconds = redisTemplate.getExpire(keys.documentRebuildLock(documentId));

        assertThat(first.state()).isEqualTo(DocumentRebuildLockState.ACQUIRED);
        assertThat(first.token()).isNotBlank();
        assertThat(second.state()).isEqualTo(DocumentRebuildLockState.CONTENDED);
        assertThat(ttlSeconds).isBetween(1L, 3L);

        assertThat(rebuildLock.release(documentId, "not-the-owner")).isFalse();
        assertThat(rebuildLock.tryAcquire(documentId).state())
                .isEqualTo(DocumentRebuildLockState.CONTENDED);

        assertThat(rebuildLock.release(documentId, first.token())).isTrue();
        DocumentRebuildLockAttempt afterRelease = rebuildLock.tryAcquire(documentId);
        assertThat(afterRelease.state()).isEqualTo(DocumentRebuildLockState.ACQUIRED);
        rebuildLock.release(documentId, afterRelease.token());
    }
}
