package com.xcy.internproject.repository;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

@Repository
public class InMemoryTicketRepository implements TicketRepository {

    private final ConcurrentMap<Long, Ticket> tickets = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    @Override
    public Ticket save(String title, String description) {
        long id = idSequence.incrementAndGet();
        Ticket ticket = new Ticket(id, title, description, TicketStatus.PENDING);
        tickets.put(id, ticket);
        return ticket;
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return Optional.ofNullable(tickets.get(id));
    }

    @Override
    public Ticket updateStatus(Long id, TicketStatus status) {
        return tickets.computeIfPresent(id, (key, current) ->
                new Ticket(current.getId(), current.getTitle(), current.getDescription(), status));
    }
}
