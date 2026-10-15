package com.xcy.internproject.repository;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;

import java.util.Optional;

public interface TicketRepository {

    Ticket save(String title, String description);

    Optional<Ticket> findById(Long id);

    Optional<Ticket> findByIdForUpdate(Long id);

    Ticket updateStatus(Long id, TicketStatus status);

    PageResult<TicketSummary> findPage(TicketPageQuery query);
}
