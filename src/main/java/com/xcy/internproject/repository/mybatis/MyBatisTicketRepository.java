package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
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

    private static Ticket toTicket(TicketRecord record) {
        return new Ticket(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getStatus());
    }
}
