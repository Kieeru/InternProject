package com.xcy.internproject.dto;

import com.xcy.internproject.model.TicketStatus;

import java.time.LocalDateTime;

public record TicketSummaryResponse(Long id, String title, TicketStatus status, LocalDateTime createdAt) {
}
