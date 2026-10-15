package com.xcy.internproject.repository;

import com.xcy.internproject.model.TicketStatus;

public record TicketPageQuery(TicketStatus status, String keyword, long offset, int limit) {
}
