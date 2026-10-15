package com.xcy.internproject.repository;

import java.util.List;

public final class PageResult<T> {

    private final List<T> items;
    private final long total;

    public PageResult(List<T> items, long total) {
        this.items = items;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }
}
