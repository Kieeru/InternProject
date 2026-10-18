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
