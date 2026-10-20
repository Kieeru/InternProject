package com.xcy.internproject.cache.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsRecorderTest {

    @Test
    void calculatesHitRateWithoutErrorsOrBypassesInTheDenominator() {
        CacheMetricsRecorder recorder = new CacheMetricsRecorder();

        recorder.recordHit(CacheArea.DOCUMENT);
        recorder.recordHit(CacheArea.DOCUMENT);
        recorder.recordNegativeHit(CacheArea.DOCUMENT);
        recorder.recordMiss(CacheArea.DOCUMENT);
        recorder.recordError(CacheArea.DOCUMENT);
        recorder.recordError(CacheArea.DOCUMENT);
        recorder.recordBypass(CacheArea.DOCUMENT);

        CacheMetricsSnapshot snapshot = recorder.snapshot(CacheArea.DOCUMENT);

        assertThat(snapshot.hit()).isEqualTo(2);
        assertThat(snapshot.negativeHit()).isEqualTo(1);
        assertThat(snapshot.miss()).isEqualTo(1);
        assertThat(snapshot.error()).isEqualTo(2);
        assertThat(snapshot.bypass()).isEqualTo(1);
        assertThat(snapshot.lookup()).isEqualTo(4);
        assertThat(snapshot.hitRate()).isEqualTo(0.75d);
    }

    @Test
    void reportsZeroHitRateWhenThereAreNoSuccessfulLookups() {
        CacheMetricsRecorder recorder = new CacheMetricsRecorder();
        recorder.recordError(CacheArea.USER_PERMISSIONS);
        recorder.recordBypass(CacheArea.USER_PERMISSIONS);

        CacheMetricsSnapshot snapshot = recorder.snapshot(CacheArea.USER_PERMISSIONS);

        assertThat(snapshot.lookup()).isZero();
        assertThat(snapshot.hitRate()).isZero();
        assertThat(snapshot.error()).isEqualTo(1);
        assertThat(snapshot.bypass()).isEqualTo(1);
    }

    @Test
    void keepsAllIncrementsUnderConcurrentUpdates() throws Exception {
        CacheMetricsRecorder recorder = new CacheMetricsRecorder();
        int workers = 8;
        int incrementsPerWorker = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    for (int increment = 0; increment < incrementsPerWorker; increment++) {
                        recorder.recordHit(CacheArea.DOCUMENT);
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(recorder.snapshot(CacheArea.DOCUMENT).hit())
                .isEqualTo((long) workers * incrementsPerWorker);
    }
}
