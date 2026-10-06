package com.xcy.internproject.repository;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;

import java.util.Optional;

public interface TicketRepository {

    Ticket save(String title, String description);

    Optional<Ticket> findById(Long id);

    Ticket updateStatus(Long id, TicketStatus status);
}
