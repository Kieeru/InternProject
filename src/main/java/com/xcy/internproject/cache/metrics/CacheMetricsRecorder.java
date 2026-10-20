package com.xcy.internproject.cache.metrics;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class CacheMetricsRecorder {

    private final Map<CacheArea, Counters> counters = new EnumMap<>(CacheArea.class);

    public CacheMetricsRecorder() {
        for (CacheArea area : CacheArea.values()) {
            counters.put(area, new Counters());
        }
    }

    public void recordHit(CacheArea area) {
        counters.get(area).hit.increment();
    }

    public void recordNegativeHit(CacheArea area) {
        counters.get(area).negativeHit.increment();
    }

    public void recordMiss(CacheArea area) {
        counters.get(area).miss.increment();
    }

    public void recordError(CacheArea area) {
        counters.get(area).error.increment();
    }

    public void recordBypass(CacheArea area) {
        counters.get(area).bypass.increment();
    }

    public CacheMetricsSnapshot snapshot(CacheArea area) {
        Counters areaCounters = counters.get(area);
        long hit = areaCounters.hit.sum();
        long negativeHit = areaCounters.negativeHit.sum();
        long miss = areaCounters.miss.sum();
        long lookup = hit + negativeHit + miss;
        double hitRate = lookup == 0L ? 0.0d : (double) (hit + negativeHit) / lookup;
        return new CacheMetricsSnapshot(
                hit,
                negativeHit,
                miss,
                areaCounters.error.sum(),
                areaCounters.bypass.sum(),
                lookup,
                hitRate);
    }

    private static final class Counters {

        private final LongAdder hit = new LongAdder();
        private final LongAdder negativeHit = new LongAdder();
        private final LongAdder miss = new LongAdder();
        private final LongAdder error = new LongAdder();
        private final LongAdder bypass = new LongAdder();
    }
}
