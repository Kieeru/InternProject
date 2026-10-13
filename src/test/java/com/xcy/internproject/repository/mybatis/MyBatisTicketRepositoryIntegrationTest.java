package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

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

    private Long insertTicket(String title, String description, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO tickets (title, description, deleted) VALUES (?, ?, ?)",
                title, description, deleted ? 1 : 0);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tickets WHERE title = ? ORDER BY id DESC LIMIT 1",
                Long.class, title);
    }
}
