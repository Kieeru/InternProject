package com.xcy.internproject.dto;

import java.util.List;

public final class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final long totalPages;

    private PageResponse(List<T> items, int page, int size, long total, long totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        long totalPages = total == 0 ? 0 : ((total - 1) / size) + 1;
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }

    public long getTotalPages() {
        return totalPages;
    }
}
