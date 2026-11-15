/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.bench.cel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Which records the benchmarks and the correctness test run on, chosen ONCE here so both use the same rule.
 * <p>
 * "In front" is decided by the StrictMath baseline's camera-frame z (cheap enough for all 8 million records); a record
 * a hair either side of z = 0 could flip under the correctly-rounded functions. That can only change which branch the
 * correctness test exercises for that record, never the comparison (both sides use the correctly-rounded functions), and
 * the test re-checks the three named records with the correctly-rounded baseline.
 */
public final class Sampling {

    /** Where the stage-1 scan lives, overridable with {@code -Dcel.bench.rawscan=...} (READ-ONLY input; never written). */
    public static final String DEFAULT_RAWSCAN = new StringBuilder("J:/stride-sx12real-2026-11-07/ZEUS_SX12_20260902/")
            .append("02_rawscan/trimble_sx12_30712309/2026-09-02/20260902-ZEUS-SX12 Files/SdeDatabase.rwi/")
            .append("SdeDatabase_Station2_Scan2.rawscan").toString();

    private Sampling() {
    }

    public static Path rawscanPath() {
        Path p = Path.of(System.getProperty("cel.bench.rawscan", DEFAULT_RAWSCAN));
        if (!Files.isReadable(p)) {
            throw new IllegalStateException(new StringBuilder("the stage-1 rawscan is not readable: ").append(p)
                    .append(" (set -Dcel.bench.rawscan=...)").toString());
        }
        return p;
    }

    /**
     * The records with a return (range above zero) that lie in front of the camera, in file order.
     *
     * @param records     the file's record count, the denominator of every share reported
     * @param validReturns records with range above zero (the denominator of the in-front share)
     */
    public record Front(int[] indices, long records, long validReturns) {

        public int first() {
            return indices[0];
        }

        public int middle() {
            return indices[indices.length / 2];
        }

        public int last() {
            return indices[indices.length - 1];
        }

        /** {@code want} indices spread evenly over the in-front records, no repeats. */
        public int[] evenly(int want) {
            if (want > indices.length) {
                throw new IllegalArgumentException("more samples wanted than in-front records");
            }
            int[] out = new int[want];
            for (int k = 0; k < want; k++) {
                out[k] = indices[(int) ((long) k * indices.length / want)];
            }
            return out;
        }
    }

    public static Front inFront(RawScanColumns cols, NativeBaselines strict) {
        int n = cols.count();
        int[] buf = new int[1 << 20];
        int used = 0;
        long valid = 0;
        for (int i = 0; i < n; i++) {
            float r = cols.range[i];
            if (!(r > 0f)) {
                continue;
            }
            valid++;
            if (strict.inFront(r, cols.azimuth[i], cols.elevation[i])) {
                if (used == buf.length) {
                    buf = Arrays.copyOf(buf, buf.length * 2);
                }
                buf[used++] = i;
            }
        }
        return new Front(Arrays.copyOf(buf, used), n, valid);
    }

    /** {@code want} record indices spread evenly over the WHOLE file (valid or not), no repeats. */
    public static int[] evenlyOverFile(int records, int want) {
        int[] out = new int[want];
        for (int k = 0; k < want; k++) {
            out[k] = (int) ((long) k * records / want);
        }
        return out;
    }
}
