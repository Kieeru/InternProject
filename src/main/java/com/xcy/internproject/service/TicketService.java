package com.xcy.internproject.service;

import com.xcy.internproject.exception.InvalidTicketStatusTransitionException;
import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.model.TicketStatus;
import com.xcy.internproject.repository.TicketRepository;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket createTicket(String title, String description) {
        return ticketRepository.save(title, description);
    }

    public Ticket updateStatus(Long id, TicketStatus targetStatus) {
        Ticket ticket = ticketRepository.findById(id)
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
}
