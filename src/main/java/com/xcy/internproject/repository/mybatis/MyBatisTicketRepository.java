package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;
import com.xcy.internproject.repository.PageResult;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.TicketRepository;
import com.xcy.internproject.repository.mybatis.mapper.TicketMapper;
import com.xcy.internproject.repository.mybatis.record.TicketRecord;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisTicketRepository implements TicketRepository {

    private final TicketMapper ticketMapper;

    public MyBatisTicketRepository(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override
    public Ticket save(String title, String description) {
        TicketRecord record = new TicketRecord();
        record.setTitle(title);
        record.setDescription(description);

        ticketMapper.insert(record);

        return new Ticket(record.getId(), title, description, TicketStatus.PENDING);
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return Optional.ofNullable(ticketMapper.findById(id))
                .map(MyBatisTicketRepository::toTicket);
    }

    @Override
    public Optional<Ticket> findByIdForUpdate(Long id) {
        return Optional.ofNullable(ticketMapper.findByIdForUpdate(id))
                .map(MyBatisTicketRepository::toTicket);
    }

    @Override
    public Ticket updateStatus(Long id, TicketStatus status) {
        int updatedRows = ticketMapper.updateStatus(id, status);
        if (updatedRows != 1) {
            throw new IllegalStateException("工单状态更新失败，受影响行数：" + updatedRows);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalStateException("工单状态更新后无法读取：" + id));
    }

    @Override
    public PageResult<TicketSummary> findPage(TicketPageQuery query) {
        TicketPageQuery escapedQuery = new TicketPageQuery(
                query.status(),
                escapeLikeLiteral(query.keyword()),
                query.offset(),
                query.limit());
        var items = ticketMapper.findPage(escapedQuery).stream()
                .map(record -> new TicketSummary(
                        record.getId(),
                        record.getTitle(),
                        record.getStatus(),
                        record.getCreatedAt()))
                .toList();
        return new PageResult<>(items, ticketMapper.count(escapedQuery));
    }

    private static String escapeLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("=", "==")
                .replace("%", "=%")
                .replace("_", "=_");
    }

    private static Ticket toTicket(TicketRecord record) {
        return new Ticket(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getStatus());
    }
}
