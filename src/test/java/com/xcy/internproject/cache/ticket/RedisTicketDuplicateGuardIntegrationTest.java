package com.xcy.internproject.cache.ticket;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class RedisTicketDuplicateGuardIntegrationTest {

    private final Set<String> exactKeys = new HashSet<>();

    @Autowired
    private TicketDuplicateGuard duplicateGuard;

    @Autowired
    private TicketRequestFingerprint fingerprint;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanExactKeys() {
        exactKeys.forEach(redisTemplate::delete);
    }

    @Test
    void acquiresSameContentOnceWithOwnerTokenAndFixedWindow() {
        String suffix = UUID.randomUUID().toString();
        String title = "重复提交-" + suffix;
        String description = "相同描述-" + suffix;
        String key = track(title, description);

        TicketDuplicateGuardAttempt first = duplicateGuard.tryAcquire(title, description);
        TicketDuplicateGuardAttempt second = duplicateGuard.tryAcquire(title, description);
        Long ttlSeconds = redisTemplate.getExpire(key);

        assertThat(first.state()).isEqualTo(TicketDuplicateGuardState.ACQUIRED);
        assertThat(first.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(first.token()).isNotBlank();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(first.token());
        assertThat(ttlSeconds).isBetween(8L, 10L);
        assertThat(key).doesNotContain(title).doesNotContain(description);
        assertThat(second.state()).isEqualTo(TicketDuplicateGuardState.DUPLICATE);
        assertThat(second.token()).isNull();
    }

    @Test
    void allowsDifferentContentToAcquireIndependently() {
        String suffix = UUID.randomUUID().toString();
        String title = "内容不同-" + suffix;
        String firstDescription = "描述一-" + suffix;
        String secondDescription = "描述二-" + suffix;
        track(title, firstDescription);
        track(title, secondDescription);

        assertThat(duplicateGuard.tryAcquire(title, firstDescription).state())
                .isEqualTo(TicketDuplicateGuardState.ACQUIRED);
        assertThat(duplicateGuard.tryAcquire(title, secondDescription).state())
                .isEqualTo(TicketDuplicateGuardState.ACQUIRED);
    }

    @Test
    void allowsContentWithAmbiguousNullCharacterPositionsToAcquireIndependently() {
        track("a", "\u0000b");
        track("a\u0000", "b");

        assertThat(duplicateGuard.tryAcquire("a", "\u0000b").state())
                .isEqualTo(TicketDuplicateGuardState.ACQUIRED);
        assertThat(duplicateGuard.tryAcquire("a\u0000", "b").state())
                .isEqualTo(TicketDuplicateGuardState.ACQUIRED);
    }

    @Test
    void releasesOnlyWhenFingerprintAndTokenBothBelongToOwner() {
        String suffix = UUID.randomUUID().toString();
        String title = "保存失败-" + suffix;
        String description = "需要安全释放-" + suffix;
        track(title, description);
        TicketDuplicateGuardAttempt acquired = duplicateGuard.tryAcquire(title, description);

        assertThat(duplicateGuard.release(acquired.fingerprint(), "not-the-owner")).isFalse();
        assertThat(duplicateGuard.tryAcquire(title, description).state())
                .isEqualTo(TicketDuplicateGuardState.DUPLICATE);

        assertThat(duplicateGuard.release(acquired.fingerprint(), acquired.token())).isTrue();
        assertThat(duplicateGuard.tryAcquire(title, description).state())
                .isEqualTo(TicketDuplicateGuardState.ACQUIRED);
    }

    private String track(String title, String description) {
        String key = keys.ticketDuplicate(fingerprint.calculate(title, description));
        exactKeys.add(key);
        return key;
    }
}
