package com.xcy.internproject.controller;

import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CacheStatisticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheMetricsRecorder metrics;

    @Test
    void returnsAggregateMetricsWithoutLeakingCacheDetails() throws Exception {
        metrics.recordHit(CacheArea.DOCUMENT);
        metrics.recordNegativeHit(CacheArea.DOCUMENT);
        metrics.recordMiss(CacheArea.DOCUMENT);
        metrics.recordError(CacheArea.DOCUMENT);
        metrics.recordBypass(CacheArea.DOCUMENT);
        metrics.recordMiss(CacheArea.USER_PERMISSIONS);

        MvcResult result = mockMvc.perform(get("/cache-statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.document.hit").value(1))
                .andExpect(jsonPath("$.data.document.negativeHit").value(1))
                .andExpect(jsonPath("$.data.document.miss").value(1))
                .andExpect(jsonPath("$.data.document.error").value(1))
                .andExpect(jsonPath("$.data.document.bypass").value(1))
                .andExpect(jsonPath("$.data.document.lookup").value(3))
                .andExpect(jsonPath("$.data.document.hitRate").value(2.0d / 3.0d))
                .andExpect(jsonPath("$.data.userPermissions.miss").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("intern:")
                .doesNotContain("document:v1")
                .doesNotContain("127.0.0.1")
                .doesNotContain("缓存正文");
    }
}
