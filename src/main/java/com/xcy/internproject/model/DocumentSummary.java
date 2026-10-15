package com.xcy.internproject.model;

import java.time.LocalDateTime;

public record DocumentSummary(Long id, String title, LocalDateTime createdAt) {
}
