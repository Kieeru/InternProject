package com.xcy.internproject.dto;

import java.time.LocalDateTime;

public record DocumentSummaryResponse(Long id, String title, LocalDateTime createdAt) {
}
