package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/db/cleanup.sql")
class MyBatisTicketRepositoryIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesTicketWithPendingStatus() {
        Ticket saved = ticketRepository.save("无法登录", "用户无法登录系统");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tickets WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("PENDING");
    }

    @Test
    void findsActiveTicketById() {
        Long ticketId = insertTicket("数据库工单", "从数据库直接插入的工单", false);

        Optional<Ticket> found = ticketRepository.findById(ticketId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ticketId);
        assertThat(found.get().getTitle()).isEqualTo("数据库工单");
        assertThat(found.get().getDescription()).isEqualTo("从数据库直接插入的工单");
        assertThat(found.get().getStatus()).isEqualTo(TicketStatus.PENDING);
    }

    @Test
    void updatesTicketStatus() {
        Ticket saved = ticketRepository.save("状态更新", "需要进入处理中的工单");

        Ticket updated = ticketRepository.updateStatus(saved.getId(), TicketStatus.PROCESSING);

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tickets WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("PROCESSING");
    }

    @Test
    void doesNotFindLogicallyDeletedTicket() {
        Long ticketId = insertTicket("逻辑删除", "这条工单将被标记删除", false);
        assertThat(ticketRepository.findById(ticketId)).isPresent();

        int updatedRows = jdbcTemplate.update(
                "UPDATE tickets SET deleted = 1 WHERE id = ?", ticketId);
        assertThat(updatedRows).isEqualTo(1);

        assertThat(ticketRepository.findById(ticketId)).isEmpty();
    }

    @Test
    void filtersTicketsByStatus() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long pendingId = insertTicket("待处理工单", "待处理", TicketStatus.PENDING, createdAt, false);
        insertTicket("处理中工单", "处理中", TicketStatus.PROCESSING, createdAt.plusMinutes(1), false);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(TicketStatus.PENDING, null, 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(pendingId);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void filtersTicketsByTitleKeyword() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long matchId = insertTicket("生产环境无法登录", "登录失败", TicketStatus.PENDING, createdAt, false);
        insertTicket("文档上传失败", "上传失败", TicketStatus.PENDING, createdAt.plusMinutes(1), false);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(null, "无法登录", 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(matchId);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void treatsLikeWildcardsInTicketKeywordAsLiteralText() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long literalMatch = insertTicket(
                "进度50%_已完成=确认",
                "字面量筛选",
                TicketStatus.PENDING,
                createdAt,
                false);
        insertTicket(
                "进度500X已完成=确认",
                "不应匹配",
                TicketStatus.PENDING,
                createdAt.plusMinutes(1),
                false);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(null, "50%_已完成=", 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(literalMatch);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void combinesTicketStatusAndKeywordFilters() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long matchId = insertTicket("登录问题待处理", "待处理", TicketStatus.PENDING, createdAt, false);
        insertTicket("登录问题处理中", "处理中", TicketStatus.PROCESSING, createdAt.plusMinutes(1), false);
        insertTicket("上传问题待处理", "待处理", TicketStatus.PENDING, createdAt.plusMinutes(2), false);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(TicketStatus.PENDING, "登录问题", 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(matchId);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void ordersTicketsWithSameCreatedTimeByIdDescending() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long firstId = insertTicket("同时间工单一", "一", TicketStatus.PENDING, createdAt, false);
        Long secondId = insertTicket("同时间工单二", "二", TicketStatus.PENDING, createdAt, false);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(null, null, 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(secondId, firstId);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void excludesLogicallyDeletedTicketsFromItemsAndTotal() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 13, 10, 0);
        Long activeId = insertTicket("有效工单", "有效", TicketStatus.PENDING, createdAt, false);
        insertTicket("已删除工单", "删除", TicketStatus.PENDING, createdAt.plusMinutes(1), true);

        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(null, null, 0, 20));

        assertThat(result.getItems())
                .extracting(TicketSummary::id)
                .containsExactly(activeId);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void returnsEmptyTicketPageWithZeroTotal() {
        PageResult<TicketSummary> result = ticketRepository.findPage(
                new TicketPageQuery(TicketStatus.CLOSED, "不存在", 0, 20));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    private Long insertTicket(String title, String description, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO tickets (title, description, deleted) VALUES (?, ?, ?)",
                title, description, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tickets WHERE title = ? ORDER BY id DESC LIMIT 1",
                Long.class, title);
    }

    private Long insertTicket(
            String title,
            String description,
            TicketStatus status,
            LocalDateTime createdAt,
            boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO tickets (title, description, status, created_at, deleted) VALUES (?, ?, ?, ?, ?)",
                title, description, status.name(), createdAt, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tickets WHERE title = ? ORDER BY id DESC LIMIT 1",
                Long.class, title);
    }
}
