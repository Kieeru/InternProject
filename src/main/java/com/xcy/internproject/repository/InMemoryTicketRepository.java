package com.xcy.internproject.repository;

import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.TicketSummary;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

public class InMemoryTicketRepository implements TicketRepository {

    private final ConcurrentMap<Long, StoredTicket> tickets = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    @Override
    public Ticket save(String title, String description) {
        long id = idSequence.incrementAndGet();
        Ticket ticket = new Ticket(id, title, description, TicketStatus.PENDING);
        tickets.put(id, new StoredTicket(ticket, LocalDateTime.now()));
        return ticket;
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return Optional.ofNullable(tickets.get(id)).map(StoredTicket::ticket);
    }

    @Override
    public Optional<Ticket> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Ticket updateStatus(Long id, TicketStatus status) {
        StoredTicket updated = tickets.computeIfPresent(id, (key, current) ->
                new StoredTicket(
                        new Ticket(
                                current.ticket().getId(),
                                current.ticket().getTitle(),
                                current.ticket().getDescription(),
                                status),
                        current.createdAt()));
        return updated == null ? null : updated.ticket();
    }

    @Override
    public PageResult<TicketSummary> findPage(TicketPageQuery query) {
        var matchingTickets = tickets.values().stream()
                .filter(stored -> query.status() == null
                        || stored.ticket().getStatus() == query.status())
                .filter(stored -> query.keyword() == null
                        || query.keyword().isEmpty()
                        || stored.ticket().getTitle().contains(query.keyword()))
                .sorted(Comparator.comparing(StoredTicket::createdAt)
                        .thenComparing(stored -> stored.ticket().getId())
                        .reversed())
                .toList();
        var items = matchingTickets.stream()
                .skip(query.offset())
                .limit(query.limit())
                .map(stored -> new TicketSummary(
                        stored.ticket().getId(),
                        stored.ticket().getTitle(),
                        stored.ticket().getStatus(),
                        stored.createdAt()))
                .toList();
        return new PageResult<>(items, matchingTickets.size());
    }

    private record StoredTicket(Ticket ticket, LocalDateTime createdAt) {
    }
}
