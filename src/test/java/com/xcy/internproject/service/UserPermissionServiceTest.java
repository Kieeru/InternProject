package com.xcy.internproject.service;

import com.xcy.internproject.cache.permission.UserPermissionCache;
import com.xcy.internproject.cache.permission.UserPermissionCacheReadResult;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.repository.UserPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPermissionServiceTest {

    private CountingUserPermissionRepository repository;
    private InMemoryUserPermissionCache cache;
    private UserPermissionService service;

    @BeforeEach
    void setUp() {
        repository = new CountingUserPermissionRepository();
        cache = new InMemoryUserPermissionCache();
        service = new UserPermissionService(repository, cache);
    }

    @Test
    void loadsSortedDistinctPermissionsFromRepositoryAndCachesThemAfterMiss() {
        repository.permissionsByUser.put(
                7L,
                List.of("ticket:audit", "document:read", "document:read"));

        List<String> permissions = service.getPermissionCodes(7L);

        assertThat(permissions).containsExactly("document:read", "ticket:audit");
        assertThat(cache.values.get(7L))
                .containsExactly("document:read", "ticket:audit");
        assertThat(repository.findCalls).isEqualTo(1);
    }

    @Test
    void returnsCachedPermissionsWithoutAccessingRepositoryAgain() {
        repository.permissionsByUser.put(7L, List.of("document:write"));

        List<String> first = service.getPermissionCodes(7L);
        List<String> second = service.getPermissionCodes(7L);

        assertThat(first).containsExactly("document:write");
        assertThat(second).containsExactly("document:write");
        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(cache.putCalls).isEqualTo(1);
    }

    @Test
    void cachesEmptyPermissionCollectionAndTreatsItAsHit() {
        repository.permissionsByUser.put(8L, List.of());

        List<String> first = service.getPermissionCodes(8L);
        List<String> second = service.getPermissionCodes(8L);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(cache.putCalls).isEqualTo(1);
    }

    @Test
    void fallsBackToRepositoryWhenCacheIsUnavailable() {
        repository.permissionsByUser.put(9L, List.of("document:read"));
        cache.unavailable = true;

        List<String> permissions = service.getPermissionCodes(9L);

        assertThat(permissions).containsExactly("document:read");
        assertThat(repository.findCalls).isEqualTo(1);
    }

    @Test
    void preservesMissingUserSemanticsWhenCacheIsUnavailable() {
        cache.unavailable = true;

        assertThatThrownBy(() -> service.getPermissionCodes(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户（ID：999）不存在");

        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(cache.putCalls).isZero();
    }

    @Test
    void evictsExactUsersPermissionCache() {
        cache.values.put(7L, List.of("document:read"));

        boolean evicted = service.evict(7L);

        assertThat(evicted).isTrue();
        assertThat(cache.evictedUserIds).containsExactly(7L);
        assertThat(cache.values).doesNotContainKey(7L);
    }

    private static final class CountingUserPermissionRepository
            implements UserPermissionRepository {

        private final Map<Long, List<String>> permissionsByUser = new HashMap<>();
        private int findCalls;

        @Override
        public Optional<List<String>> findPermissionCodesByUserId(Long userId) {
            findCalls++;
            return Optional.ofNullable(permissionsByUser.get(userId));
        }
    }

    private static final class InMemoryUserPermissionCache implements UserPermissionCache {

        private final Map<Long, List<String>> values = new HashMap<>();
        private final List<Long> evictedUserIds = new ArrayList<>();
        private int putCalls;
        private boolean unavailable;

        @Override
        public UserPermissionCacheReadResult get(Long userId) {
            if (unavailable) {
                return UserPermissionCacheReadResult.unavailable();
            }
            List<String> permissionCodes = values.get(userId);
            return permissionCodes == null
                    ? UserPermissionCacheReadResult.miss()
                    : UserPermissionCacheReadResult.hit(permissionCodes);
        }

        @Override
        public boolean put(Long userId, List<String> permissionCodes) {
            putCalls++;
            if (unavailable) {
                return false;
            }
            values.put(userId, List.copyOf(permissionCodes));
            return true;
        }

        @Override
        public boolean evict(Long userId) {
            evictedUserIds.add(userId);
            return values.remove(userId) != null;
        }
    }
}
