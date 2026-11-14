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

package au.net.zeus.jgdms.cel.eval;

import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The calls an evaluation made on its {@link CandidateProjection} and its {@link CorrectlyRoundedMath}, in
 * order. Outcomes alone cannot show an evaluation-order fault: an evaluator that short-circuits an
 * {@code &&} returns the same value and only the trace (or an exception) tells (design section 9 item 8).
 * Test tree only.
 */
final class TraceLog {

    static final long PROJ_UNDECODABLE = 1;
    static final long PROJ_CHAIN = 2;
    static final long PROJ_DECLARES = 3;
    static final long PROJ_VALUE = 4;
    static final long MATH_SIN = 5;
    static final long MATH_COS = 6;
    static final long MATH_TAN = 7;
    static final long MATH_ASIN = 8;
    static final long MATH_ACOS = 9;
    static final long MATH_ATAN = 10;
    static final long MATH_ATAN2 = 11;

    /** One event: a code and a payload, compared by equality. */
    record Event(long code, long payload) {}

    /** Where the current evaluator's calls go; switched between the evaluators run over one record. */
    static final class Sink {
        TraceLog current;

        void log(long code, long payload) {
            if (current != null) current.add(code, payload);
        }
    }

    private long[] events = new long[64];
    private int length; // longs used: two per event

    void add(long code, long payload) {
        if (length + 2 > events.length) events = Arrays.copyOf(events, events.length * 2);
        events[length++] = code;
        events[length++] = payload;
    }

    int size() {
        return length / 2;
    }

    private Event event(int i) {
        return new Event(events[2 * i], events[2 * i + 1]);
    }

    boolean identicalTo(TraceLog other) {
        return length == other.length && Arrays.equals(events, 0, length, other.events, 0, length);
    }

    /** True if every event of this log occurs in {@code walker}, in this order, not necessarily adjacent. */
    boolean isSubsequenceOf(TraceLog walker) {
        int w = 0;
        for (int c = 0; c < size(); c++) {
            Event wanted = event(c);
            while (w < walker.size() && !walker.event(w).equals(wanted)) w++;
            if (w == walker.size()) return false;
            w++;
        }
        return true;
    }

    /** The distinct events in order of first occurrence. */
    Set<Event> firstOccurrences() {
        Set<Event> out = new LinkedHashSet<>();
        for (int i = 0; i < size(); i++) out.add(event(i));
        return out;
    }

    /** The first events as text, for a failure message. */
    String head(int limit) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(size(), limit); i++) {
            Event e = event(i);
            sb.append(i == 0 ? "" : " ").append(Long.toHexString(e.code())).append(':').append(Long.toHexString(e.payload()));
        }
        if (size() > limit) sb.append(" ...(").append(size()).append(" events)");
        return sb.toString();
    }

    /** A real provider's functions with every call logged first: argument bits, so a changed argument shows. */
    static final class TracingMath implements CorrectlyRoundedMath {
        private final CorrectlyRoundedMath inner;
        private final Sink sink;

        TracingMath(CorrectlyRoundedMath inner, Sink sink) {
            this.inner = inner;
            this.sink = sink;
        }

        @Override
        public double sin(double x) {
            sink.log(MATH_SIN, Double.doubleToRawLongBits(x));
            return inner.sin(x);
        }

        @Override
        public double cos(double x) {
            sink.log(MATH_COS, Double.doubleToRawLongBits(x));
            return inner.cos(x);
        }

        @Override
        public double tan(double x) {
            sink.log(MATH_TAN, Double.doubleToRawLongBits(x));
            return inner.tan(x);
        }

        @Override
        public double asin(double x) {
            sink.log(MATH_ASIN, Double.doubleToRawLongBits(x));
            return inner.asin(x);
        }

        @Override
        public double acos(double x) {
            sink.log(MATH_ACOS, Double.doubleToRawLongBits(x));
            return inner.acos(x);
        }

        @Override
        public double atan(double x) {
            sink.log(MATH_ATAN, Double.doubleToRawLongBits(x));
            return inner.atan(x);
        }

        @Override
        public double atan2(double y, double x) {
            sink.log(MATH_ATAN2, Double.doubleToRawLongBits(y) * 0x9E3779B97F4A7C15L ^ Double.doubleToRawLongBits(x));
            return inner.atan2(y, x);
        }
    }
}
