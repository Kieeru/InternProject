package com.xcy.internproject.cache.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlPolicyTest {

    @Test
    void returnsBaseTtlsWhenJitterIsZero() {
        RedisCacheProperties properties = new RedisCacheProperties();
        CacheTtlPolicy policy = new CacheTtlPolicy(properties, upperExclusive -> 0L);

        assertThat(policy.documentTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.missingDocumentTtl()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.userPermissionTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void includesConfiguredMaximumJitterInTtlRange() {
        RedisCacheProperties properties = new RedisCacheProperties();
        CacheTtlPolicy policy = new CacheTtlPolicy(
                properties,
                upperExclusive -> upperExclusive - 1L);

        assertThat(policy.documentTtl()).isEqualTo(Duration.ofMinutes(12));
        assertThat(policy.missingDocumentTtl()).isEqualTo(Duration.ofSeconds(75));
        assertThat(policy.userPermissionTtl()).isEqualTo(Duration.ofMinutes(6));
    }
}
