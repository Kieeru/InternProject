package com.xcy.internproject.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpCacheStoreTest {

    @Test
    void reportsUnavailableAndNeverClaimsToWriteOrDelete() {
        CacheStore store = new NoOpCacheStore();

        CacheReadResult result = store.get("intern:test:document:v1:1");

        assertThat(result.state()).isEqualTo(CacheReadState.UNAVAILABLE);
        assertThat(result.value()).isNull();
        assertThat(store.put("intern:test:document:v1:1", "{}", Duration.ofMinutes(1)))
                .isFalse();
        assertThat(store.delete("intern:test:document:v1:1"))
                .isEqualTo(CacheDeleteResult.UNAVAILABLE);
    }
}
