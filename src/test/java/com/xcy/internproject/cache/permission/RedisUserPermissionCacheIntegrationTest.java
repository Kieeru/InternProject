package com.xcy.internproject.cache.permission;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.repository.UserPermissionRepository;
import com.xcy.internproject.service.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "redis-test"})
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class RedisUserPermissionCacheIntegrationTest {

    private final long userId = ThreadLocalRandom.current()
            .nextLong(1_000_000L, Long.MAX_VALUE);

    @Autowired
    private UserPermissionCache cache;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanExactPermissionKey() {
        redisTemplate.delete(keys.userPermissions(userId));
    }

    @Test
    void roundTripsDistinctSortedJsonArrayWithConfiguredTtlRange() {
        assertThat(cache.put(
                userId,
                List.of("ticket:audit", "document:read", "document:read")))
                .isTrue();

        UserPermissionCacheReadResult result = cache.get(userId);
        String rawJson = redisTemplate.opsForValue().get(keys.userPermissions(userId));
        Long ttlSeconds = redisTemplate.getExpire(keys.userPermissions(userId));

        assertThat(rawJson).isEqualTo("[\"document:read\",\"ticket:audit\"]");
        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.HIT);
        assertThat(result.permissionCodes())
                .containsExactly("document:read", "ticket:audit");
        assertThat(ttlSeconds).isBetween(299L, 360L);
    }

    @Test
    void roundTripsEmptyPermissionArrayAsCacheHit() {
        assertThat(cache.put(userId, List.of())).isTrue();

        UserPermissionCacheReadResult result = cache.get(userId);

        assertThat(redisTemplate.opsForValue().get(keys.userPermissions(userId)))
                .isEqualTo("[]");
        assertThat(result.state()).isEqualTo(UserPermissionCacheReadState.HIT);
        assertThat(result.permissionCodes()).isEmpty();
    }

    @Test
    void secondServiceReadUsesRedisWithoutCallingRepositoryAgain() {
        CountingUserPermissionRepository repository =
                new CountingUserPermissionRepository(List.of("document:read"));
        UserPermissionService service = new UserPermissionService(repository, cache);

        List<String> first = service.getPermissionCodes(userId);
        List<String> second = service.getPermissionCodes(userId);

        assertThat(first).containsExactly("document:read");
        assertThat(second).containsExactly("document:read");
        assertThat(repository.findCalls).isEqualTo(1);
    }

    @Test
    void malformedJsonFallsBackToRepositoryAndIsReplaced() {
        redisTemplate.opsForValue().set(keys.userPermissions(userId), "损坏的 JSON");
        CountingUserPermissionRepository repository =
                new CountingUserPermissionRepository(List.of("document:read"));
        UserPermissionService service = new UserPermissionService(repository, cache);

        List<String> permissionCodes = service.getPermissionCodes(userId);

        assertThat(permissionCodes).containsExactly("document:read");
        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(keys.userPermissions(userId)))
                .isEqualTo("[\"document:read\"]");
    }

    @Test
    void evictsOnlyTheRequestedPermissionKey() {
        long otherUserId = userId + 1L;
        try {
            assertThat(cache.put(userId, List.of("document:read"))).isTrue();
            assertThat(cache.put(otherUserId, List.of("document:write"))).isTrue();

            assertThat(cache.evict(userId)).isTrue();

            assertThat(redisTemplate.hasKey(keys.userPermissions(userId))).isFalse();
            assertThat(redisTemplate.hasKey(keys.userPermissions(otherUserId))).isTrue();
        } finally {
            redisTemplate.delete(keys.userPermissions(otherUserId));
        }
    }

    private static final class CountingUserPermissionRepository
            implements UserPermissionRepository {

        private final List<String> permissionCodes;
        private int findCalls;

        private CountingUserPermissionRepository(List<String> permissionCodes) {
            this.permissionCodes = permissionCodes;
        }

        @Override
        public Optional<List<String>> findPermissionCodesByUserId(Long ignoredUserId) {
            findCalls++;
            return Optional.of(permissionCodes);
        }
    }
}
