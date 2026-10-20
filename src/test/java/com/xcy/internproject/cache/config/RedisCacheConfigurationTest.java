package com.xcy.internproject.cache.config;

import com.xcy.internproject.cache.CacheStore;
import com.xcy.internproject.cache.NoOpCacheStore;
import com.xcy.internproject.cache.RedisCacheStore;
import com.xcy.internproject.cache.document.DocumentCache;
import com.xcy.internproject.cache.document.DocumentRebuildLock;
import com.xcy.internproject.cache.document.NoOpDocumentCache;
import com.xcy.internproject.cache.document.NoOpDocumentRebuildLock;
import com.xcy.internproject.cache.document.RedisDocumentCache;
import com.xcy.internproject.cache.document.RedisDocumentRebuildLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheInfrastructureConfiguration.class);

    @Test
    void bindsDefaultsAndProvidesDisabledCacheFallback() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RedisCacheProperties.class);
            assertThat(context).hasSingleBean(CacheStore.class);
            assertThat(context.getBean(CacheStore.class)).isInstanceOf(NoOpCacheStore.class);
            assertThat(context).hasSingleBean(DocumentCache.class);
            assertThat(context.getBean(DocumentCache.class)).isInstanceOf(NoOpDocumentCache.class);
            assertThat(context).hasSingleBean(DocumentRebuildLock.class);
            assertThat(context.getBean(DocumentRebuildLock.class))
                    .isInstanceOf(NoOpDocumentRebuildLock.class);

            RedisCacheProperties properties = context.getBean(RedisCacheProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getKeyPrefix()).isEqualTo("intern:local");
            assertThat(properties.getDocumentTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.getDocumentTtlJitter()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.getMissingDocumentTtl()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.getMissingDocumentTtlJitter()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.getUserPermissionTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getUserPermissionTtlJitter()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.getDocumentRebuildLockTtl()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getDocumentRebuildWaitTimeout()).isEqualTo(Duration.ofMillis(300));
            assertThat(properties.getDocumentRebuildPollInterval()).isEqualTo(Duration.ofMillis(20));
            assertThat(properties.getTicketDuplicateWindow()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.getMaxDocumentCacheBytes()).isEqualTo(512L * 1024L);
        });
    }

    @Test
    void bindsCustomPrefixAndBuildsVersionedKeys() {
        contextRunner
                .withPropertyValues("app.cache.redis.key-prefix=intern:test:run-42")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CacheKeyFactory keys = context.getBean(CacheKeyFactory.class);

                    assertThat(keys.document(7L))
                            .isEqualTo("intern:test:run-42:document:v1:7");
                    assertThat(keys.documentRebuildLock(7L))
                            .isEqualTo("intern:test:run-42:document-rebuild-lock:v1:7");
                    assertThat(keys.userPermissions(9L))
                            .isEqualTo("intern:test:run-42:user-permissions:v1:9");
                    assertThat(keys.ticketDuplicate("abc123"))
                            .isEqualTo("intern:test:run-42:ticket-dedupe:v1:abc123");
                });
    }

    @Test
    void startsWithRedisFeatureEnabledWithoutOpeningAConnection() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("app.cache.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CacheStore.class)).isInstanceOf(RedisCacheStore.class);
                    assertThat(context.getBean(DocumentCache.class))
                            .isInstanceOf(RedisDocumentCache.class);
                    assertThat(context.getBean(DocumentRebuildLock.class))
                            .isInstanceOf(RedisDocumentRebuildLock.class);
                    assertThat(context.getBean(RedisCacheProperties.class).isEnabled()).isTrue();
                });
    }

    @Test
    void loadsIsolatedRedisIntegrationTestProfile() {
        contextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("spring.profiles.active=redis-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    RedisCacheProperties properties = context.getBean(RedisCacheProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getKeyPrefix()).isEqualTo("intern:test");
                    assertThat(context.getEnvironment().getProperty("spring.data.redis.database"))
                            .isEqualTo("15");
                });
    }
}
