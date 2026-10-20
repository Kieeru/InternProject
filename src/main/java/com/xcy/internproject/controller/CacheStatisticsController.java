package com.xcy.internproject.controller;

import com.xcy.internproject.dto.ApiResponse;
import com.xcy.internproject.dto.CacheStatisticsResponse;
import com.xcy.internproject.service.CacheStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheStatisticsController {

    private final CacheStatisticsService cacheStatisticsService;

    public CacheStatisticsController(CacheStatisticsService cacheStatisticsService) {
        this.cacheStatisticsService = cacheStatisticsService;
    }

    @GetMapping("/cache-statistics")
    public ApiResponse<CacheStatisticsResponse> getStatistics() {
        return ApiResponse.success(cacheStatisticsService.getStatistics());
    }
}
