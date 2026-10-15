package com.xcy.internproject.model;

import java.time.LocalDateTime;

public record TicketSummary(Long id, String title, TicketStatus status, LocalDateTime createdAt) {
}
