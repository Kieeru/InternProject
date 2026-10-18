package com.xcy.internproject.cache.metrics;

public record CacheMetricsSnapshot(
        long hit,
        long negativeHit,
        long miss,
        long error,
        long bypass,
        long lookup,
        double hitRate) {
}
