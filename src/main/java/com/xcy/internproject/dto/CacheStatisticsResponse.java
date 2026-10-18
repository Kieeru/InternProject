package com.xcy.internproject.dto;

import com.xcy.internproject.cache.metrics.CacheMetricsSnapshot;

public record CacheStatisticsResponse(
        CacheMetricsSnapshot document,
        CacheMetricsSnapshot userPermissions) {
}
