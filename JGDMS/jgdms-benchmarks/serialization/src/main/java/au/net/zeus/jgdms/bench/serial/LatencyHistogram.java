/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fixed-size, log-bucketed histogram of nanosecond latencies, in the manner of HdrHistogram and with no
 * dependency: values below 2048 ns are counted exactly, above that each power of two is split into 1024 equal
 * buckets, so the relative error is at most 1/1024 (three significant digits). The range is 0 to 100 s; larger
 * values are clamped to 100 s (and counted as such). The size is about 230 KB however many values are recorded.
 *
 * <p>{@link #record(long)} is not thread-safe and is for a histogram owned by one thread;
 * {@link #recordShared(long)} uses atomic increments and may be used by several threads on one histogram.
 * Do not mix them on one histogram while recording.
 *
 * <p>Written with AI assistance (Claude); see the README.
 */
final class LatencyHistogram {

    private static final int SUB_BITS = 11;
    private static final int SUB = 1 << SUB_BITS;
    private static final int HALF = SUB >>> 1;
    static final long MAX_TRACKABLE_NANOS = 100_000_000_000L;
    private static final int LENGTH = indexOf(MAX_TRACKABLE_NANOS) + 1;
    private static final VarHandle COUNTS = MethodHandles.arrayElementVarHandle(long[].class);

    private final long[] counts = new long[LENGTH];
    private final AtomicLong max = new AtomicLong();

    static int indexOf(long v) {
        if (v < 0) {
            v = 0;
        } else if (v > MAX_TRACKABLE_NANOS) {
            v = MAX_TRACKABLE_NANOS;
        }
        int bucket = Math.max(0, 64 - Long.numberOfLeadingZeros(v | (SUB - 1)) - SUB_BITS);
        return ((bucket + 1) << (SUB_BITS - 1)) + (int) (v >>> bucket) - HALF;
    }

    /** The lowest value that falls in bucket {@code index}. */
    private static long lowest(int index) {
        if (index < SUB) {
            return index;
        }
        int bucket = (index >> (SUB_BITS - 1)) - 1;
        long sub = (index & (HALF - 1)) + HALF;
        return sub << bucket;
    }

    /** The highest value that falls in bucket {@code index}. */
    private static long highest(int index) {
        if (index < SUB) {
            return index;
        }
        int bucket = (index >> (SUB_BITS - 1)) - 1;
        return lowest(index) + (1L << bucket) - 1;
    }

    void record(long nanos) {
        counts[indexOf(nanos)]++;
        noteMax(nanos);
    }

    void recordShared(long nanos) {
        COUNTS.getAndAdd(counts, indexOf(nanos), 1L);
        noteMax(nanos);
    }

    private void noteMax(long nanos) {
        long m = max.get();
        while (nanos > m && !max.compareAndSet(m, nanos)) {
            m = max.get();
        }
    }

    /** Adds all of {@code other}'s counts to this histogram. */
    void add(LatencyHistogram other) {
        for (int i = 0; i < counts.length; i++) {
            counts[i] += other.counts[i];
        }
        noteMax(other.max.get());
    }

    long count() {
        long n = 0;
        for (long c : counts) {
            n += c;
        }
        return n;
    }

    /** The exact largest value recorded (not clamped), or 0. */
    long max() {
        return max.get();
    }

    double mean() {
        long n = 0;
        double sum = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] != 0) {
                n += counts[i];
                sum += counts[i] * ((lowest(i) + highest(i)) / 2.0);
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** The value (upper edge of its bucket, never above the maximum seen) at percentile {@code p}, 0 to 100. */
    long percentile(double p) {
        long n = count();
        if (n == 0) {
            return 0;
        }
        long target = Math.max(1, (long) Math.ceil(p / 100.0 * n));
        long seen = 0;
        for (int i = 0; i < counts.length; i++) {
            seen += counts[i];
            if (seen >= target) {
                return Math.min(highest(i), max.get());
            }
        }
        return max.get();
    }

    /**
     * The mean time per call, in microseconds, by Little's law: {@code callers / throughput}, with the throughput in
     * calls per millisecond. In a closed loop every caller is always inside a call or between two, so this is the
     * mean latency the callers see, including the time of callers that complete almost nothing. NaN if the
     * throughput is zero.
     */
    static double littleMeanMicros(int callers, double callsPerMs) {
        return callsPerMs > 0 ? callers * 1000.0 / callsPerMs : Double.NaN;
    }

    /** A quick self-check: {@code java -cp ... au.net.zeus.jgdms.bench.serial.LatencyHistogram}. */
    public static void main(String[] args) {
        LatencyHistogram h = new LatencyHistogram();
        for (long v = 1; v <= 1_000_000; v++) {
            h.record(v * 1000);                      // 1 us .. 1 s, uniformly
        }
        double[] ps = {50, 90, 99, 99.9, 100};
        for (double p : ps) {
            long got = h.percentile(p);
            long want = (long) (p / 100.0 * 1_000_000) * 1000;
            double err = Math.abs(got - want) / (double) want;
            System.out.printf("p%s got %d want %d error %.4f%%%n", p, got, want, err * 100);
            if (err > 0.0011) {
                throw new AssertionError("p" + p);
            }
        }
        for (long v : new long[] {0, 1, 2047, 2048, 4095, 4096, 1_000_000, MAX_TRACKABLE_NANOS}) {
            int i = indexOf(v);
            if (v > highest(i) || v < lowest(i) || (i > 0 && indexOf(lowest(i)) != i) || indexOf(highest(i)) != i) {
                throw new AssertionError("bucket of " + v + " is " + i + " [" + lowest(i) + "," + highest(i) + "]");
            }
        }
        if (Math.abs(littleMeanMicros(64, 32.0) - 2000.0) > 1e-9 || !Double.isNaN(littleMeanMicros(8, 0))) {
            throw new AssertionError("littleMeanMicros");
        }
        System.out.println("ok, " + LENGTH + " buckets");
    }
}
