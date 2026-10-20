package com.xcy.internproject.service;

import com.xcy.internproject.cache.metrics.CacheArea;
import com.xcy.internproject.cache.metrics.CacheMetricsRecorder;
import com.xcy.internproject.cache.ticket.TicketDuplicateGuard;
import com.xcy.internproject.model.Ticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms"
})
@ActiveProfiles({"test", "redis-test"})
@Sql("/db/cleanup.sql")
@ExtendWith(OutputCaptureExtension.class)
class UserPermissionRedisFailureIntegrationTest {

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketDuplicateGuard ticketDuplicateGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheMetricsRecorder metrics;

    @Test
    void returnsMysqlPermissionsWhenRedisConnectionIsUnavailable(CapturedOutput output) {
        jdbcTemplate.update(
                "INSERT INTO users (username, email) VALUES (?, ?)",
                "redis-down-user", "redis-down-user@example.com");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                "redis-down-user");
        jdbcTemplate.update(
                "INSERT INTO roles (code, name) VALUES (?, ?)",
                "REDIS_DOWN_ROLE", "Redis 故障测试角色");
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ?", Long.class, "REDIS_DOWN_ROLE");
        jdbcTemplate.update(
                "INSERT INTO permissions (code, name) VALUES (?, ?)",
                "document:read", "读取文档");
        Long permissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM permissions WHERE code = ?",
                Long.class,
                "document:read");
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        jdbcTemplate.update(
                "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);

        List<String> permissionCodes = userPermissionService.getPermissionCodes(userId);

        assertThat(permissionCodes).containsExactly("document:read");
        assertThat(metrics.snapshot(CacheArea.USER_PERMISSIONS).error()).isEqualTo(2);

        Ticket created = ticketService.createTicket(
                "Redis 故障仍创建工单",
                "短窗口防重降级但 MySQL 写入继续");
        assertThat(ticketService.getTicket(created.getId()))
                .satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(created.getId());
                    assertThat(found.getTitle()).isEqualTo("Redis 故障仍创建工单");
                    assertThat(found.getDescription()).isEqualTo("短窗口防重降级但 MySQL 写入继续");
                });
        assertThat(metrics.snapshot(CacheArea.TICKET_DUPLICATE).error()).isEqualTo(1);
        assertThat(output.toString())
                .contains("Redis 工单防重不可用，当前请求按 fail-open 继续写入 MySQL")
                .doesNotContain(
                        "Redis 故障仍创建工单",
                        "短窗口防重降级但 MySQL 写入继续",
                        "RedisConnectionFailureException");

        assertThat(ticketDuplicateGuard.tryAcquire("不应出现在日志中的标题", "不应出现在日志中的正文").state())
                .isEqualTo(com.xcy.internproject.cache.ticket.TicketDuplicateGuardState.UNAVAILABLE);
        assertThat(metrics.snapshot(CacheArea.TICKET_DUPLICATE).error()).isEqualTo(2);
        assertThat(output.toString())
                .containsOnlyOnce("Redis 工单防重不可用，当前请求按 fail-open 继续写入 MySQL")
                .doesNotContain("不应出现在日志中的标题", "不应出现在日志中的正文");

        assertThat(ticketDuplicateGuard.release("missing-fingerprint", "missing-token")).isFalse();
        assertThat(metrics.snapshot(CacheArea.TICKET_DUPLICATE).error()).isEqualTo(3);
    }
}
