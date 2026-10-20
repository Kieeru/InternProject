package com.xcy.internproject.controller;

import com.xcy.internproject.cache.config.CacheKeyFactory;
import com.xcy.internproject.cache.ticket.TicketRequestFingerprint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "redis-test"})
@Sql("/db/cleanup.sql")
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class TicketDuplicateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRequestFingerprint fingerprint;

    @Autowired
    private CacheKeyFactory keys;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String exactKey;

    @AfterEach
    void cleanExactKey() {
        if (exactKey != null) {
            redisTemplate.delete(exactKey);
        }
    }

    @Test
    void returnsConflictForSameTicketInsideDuplicateWindow() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String title = "HTTP 重复-" + suffix;
        String description = "相同请求-" + suffix;
        exactKey = keys.ticketDuplicate(fingerprint.calculate(title, description));
        String body = """
                {
                  "title": "%s",
                  "description": "%s"
                }
                """.formatted(title, description);

        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SUBMISSION"))
                .andExpect(jsonPath("$.message")
                        .value("相同工单已在短时间内提交，请勿重复提交"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
