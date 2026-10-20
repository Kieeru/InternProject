package com.xcy.internproject.cache;

public record CacheReadResult(CacheReadState state, String value) {

    public static CacheReadResult unavailable() {
        return new CacheReadResult(CacheReadState.UNAVAILABLE, null);
    }
}
