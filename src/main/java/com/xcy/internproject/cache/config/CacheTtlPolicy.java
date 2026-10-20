package com.xcy.internproject.cache.config;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;

public final class CacheTtlPolicy {

    private final RedisCacheProperties properties;
    private final LongUnaryOperator jitterMillisPicker;

    public CacheTtlPolicy(RedisCacheProperties properties) {
        this(properties, upperExclusive -> ThreadLocalRandom.current().nextLong(upperExclusive));
    }

    CacheTtlPolicy(
            RedisCacheProperties properties,
            LongUnaryOperator jitterMillisPicker) {
        this.properties = properties;
        this.jitterMillisPicker = jitterMillisPicker;
    }

    public Duration documentTtl() {
        return withJitter(properties.getDocumentTtl(), properties.getDocumentTtlJitter());
    }

    public Duration missingDocumentTtl() {
        return withJitter(
                properties.getMissingDocumentTtl(),
                properties.getMissingDocumentTtlJitter());
    }

    public Duration userPermissionTtl() {
        return withJitter(
                properties.getUserPermissionTtl(),
                properties.getUserPermissionTtlJitter());
    }

    private Duration withJitter(Duration base, Duration maximumJitter) {
        long maximumMillis = maximumJitter.toMillis();
        if (maximumMillis == 0L) {
            return base;
        }
        long jitterMillis = jitterMillisPicker.applyAsLong(maximumMillis + 1L);
        return base.plusMillis(jitterMillis);
    }
}
