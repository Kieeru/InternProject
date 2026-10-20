package com.xcy.internproject.service;

import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import com.xcy.internproject.dto.CacheStatisticsResponse;
import org.springframework.stereotype.Service;

@Service
public class CacheStatisticsService {

    private final CacheMetricsRecorder metrics;

    public CacheStatisticsService(CacheMetricsRecorder metrics) {
        this.metrics = metrics;
    }

    public CacheStatisticsResponse getStatistics() {
        return new CacheStatisticsResponse(
                metrics.snapshot(CacheArea.DOCUMENT),
                metrics.snapshot(CacheArea.USER_PERMISSIONS));
    }
}
