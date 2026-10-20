package com.xcy.internproject.cache.config;

import com.xcy.internproject.cache.CacheStore;
import com.xcy.internproject.cache.NoOpCacheStore;
import com.xcy.internproject.cache.RedisCacheStore;
import com.xcy.internproject.cache.document.DocumentCache;
import com.xcy.internproject.cache.document.NoOpDocumentCache;
import com.xcy.internproject.cache.document.DocumentRebuildLock;
import com.xcy.internproject.cache.document.NoOpDocumentRebuildLock;
import com.xcy.internproject.cache.document.RedisDocumentRebuildLock;
import com.xcy.internproject.cache.document.RedisDocumentCache;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import com.xcy.internproject.cache.permission.NoOpUserPermissionCache;
import com.xcy.internproject.cache.permission.RedisUserPermissionCache;
import com.xcy.internproject.cache.permission.UserPermissionCache;
import com.xcy.internproject.cache.ticket.NoOpTicketDuplicateGuard;
import com.xcy.internproject.cache.ticket.RedisTicketDuplicateGuard;
import com.xcy.internproject.cache.ticket.TicketDuplicateGuard;
import com.xcy.internproject.cache.ticket.TicketRequestFingerprint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisCacheProperties.class)
public class CacheInfrastructureConfiguration {

    @Bean
    CacheMetricsRecorder cacheMetricsRecorder() {
        return new CacheMetricsRecorder();
    }

    @Bean
    CacheKeyFactory cacheKeyFactory(RedisCacheProperties properties) {
        return new CacheKeyFactory(properties);
    }

    @Bean
    CacheTtlPolicy cacheTtlPolicy(RedisCacheProperties properties) {
        return new CacheTtlPolicy(properties);
    }

    @Bean
    TicketRequestFingerprint ticketRequestFingerprint() {
        return new TicketRequestFingerprint();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    CacheStore noOpCacheStore() {
        return new NoOpCacheStore();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "true")
    CacheStore redisCacheStore(StringRedisTemplate redisTemplate) {
        return new RedisCacheStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    TicketDuplicateGuard noOpTicketDuplicateGuard() {
        return new NoOpTicketDuplicateGuard();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "true")
    TicketDuplicateGuard redisTicketDuplicateGuard(
            StringRedisTemplate redisTemplate,
            CacheKeyFactory keys,
            RedisCacheProperties properties,
            TicketRequestFingerprint fingerprint,
            CacheMetricsRecorder metrics) {
        return new RedisTicketDuplicateGuard(
                redisTemplate, keys, properties, fingerprint, metrics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    DocumentCache noOpDocumentCache(CacheMetricsRecorder metrics) {
        return new NoOpDocumentCache(metrics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "true")
    DocumentCache redisDocumentCache(
            CacheStore cacheStore,
            CacheKeyFactory keys,
            CacheTtlPolicy ttlPolicy,
            RedisCacheProperties properties,
            ObjectMapper objectMapper,
            CacheMetricsRecorder metrics) {
        return new RedisDocumentCache(
                cacheStore, keys, ttlPolicy, properties, objectMapper, metrics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    UserPermissionCache noOpUserPermissionCache(CacheMetricsRecorder metrics) {
        return new NoOpUserPermissionCache(metrics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "true")
    UserPermissionCache redisUserPermissionCache(
            CacheStore cacheStore,
            CacheKeyFactory keys,
            CacheTtlPolicy ttlPolicy,
            ObjectMapper objectMapper,
            CacheMetricsRecorder metrics) {
        return new RedisUserPermissionCache(
                cacheStore, keys, ttlPolicy, objectMapper, metrics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    DocumentRebuildLock noOpDocumentRebuildLock() {
        return new NoOpDocumentRebuildLock();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.cache.redis",
            name = "enabled",
            havingValue = "true")
    DocumentRebuildLock redisDocumentRebuildLock(
            StringRedisTemplate redisTemplate,
            CacheKeyFactory keys,
            RedisCacheProperties properties) {
        return new RedisDocumentRebuildLock(redisTemplate, keys, properties);
    }
}
