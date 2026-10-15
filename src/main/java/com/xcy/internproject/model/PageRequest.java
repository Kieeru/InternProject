package com.xcy.internproject.model;

import com.xcy.internproject.exception.InvalidPaginationException;

public final class PageRequest {

    private final int page;
    private final int size;

    private PageRequest(int page, int size) {
        this.page = page;
        this.size = size;
    }

    public static PageRequest of(int page, int size) {
        if (page < 1) {
            throw new InvalidPaginationException("页码不能小于1");
        }
        if (size < 1 || size > 100) {
            throw new InvalidPaginationException("每页条数必须在1到100之间");
        }
        return new PageRequest(page, size);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
