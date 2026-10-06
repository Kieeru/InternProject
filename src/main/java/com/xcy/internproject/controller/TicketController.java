package com.xcy.internproject.controller;

import com.xcy.internproject.dto.ApiResponse;
import com.xcy.internproject.dto.CreateTicketRequest;
import com.xcy.internproject.dto.UpdateTicketStatusRequest;
import com.xcy.internproject.model.Ticket;
import com.xcy.internproject.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> createTicket(
            @Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.createTicket(request.getTitle(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ticket));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Ticket> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ApiResponse.success(ticketService.updateStatus(id, request.getStatus()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Ticket> getTicket(@PathVariable Long id){return ApiResponse.success(ticketService.getTicket(id));}
}
