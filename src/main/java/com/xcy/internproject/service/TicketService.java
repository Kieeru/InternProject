package com.xcy.internproject.service;

import com.xcy.internproject.dto.PageResponse;
import com.xcy.internproject.dto.TicketSummaryResponse;
import com.xcy.internproject.exception.InvalidTicketStatusTransitionException;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.model.PageRequest;
import com.xcy.internproject.repository.TicketPageQuery;
import com.xcy.internproject.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket createTicket(String title, String description) {
        return ticketRepository.save(title, description);
    }

    @Transactional
    public Ticket updateStatus(Long id, TicketStatus targetStatus) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("工单", id));

        if (!ticket.getStatus().canTransitionTo(targetStatus)) {
            throw new InvalidTicketStatusTransitionException(ticket.getStatus(), targetStatus);
        }

        return ticketRepository.updateStatus(id, targetStatus);
    }

    public Ticket getTicket(Long id){
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工单", id));
    }

    public PageResponse<TicketSummaryResponse> listTickets(
            TicketStatus status, String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String normalizedKeyword = normalizeFilter(keyword);
        var result = ticketRepository.findPage(new TicketPageQuery(
                status,
                normalizedKeyword,
                pageRequest.getOffset(),
                pageRequest.getSize()));
        var items = result.getItems().stream()
                .map(summary -> new TicketSummaryResponse(
                        summary.id(), summary.title(), summary.status(), summary.createdAt()))
                .toList();
        return PageResponse.of(items, pageRequest.getPage(), pageRequest.getSize(), result.getTotal());
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
