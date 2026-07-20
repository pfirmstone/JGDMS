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

package au.net.zeus.jgdms.cel.wire;

import java.util.Map;

/**
 * The closed, pinned {@code CALL} function-id table of Appendix B §B.8.3:
 * one wire id per (function name x operand-type signature), ids 1-24. This
 * is the allowlist T2 (this decoder) and T6 enforce -- a {@code functionId}
 * outside this table, or an argument count not matching the pinned arity for
 * the decoded id, is a hard decode reject (Appendix B §B.6.1).
 * <p>
 * Implemented as a genuine table lookup ({@link #byId(int)}), not merely a
 * range check, per §B.6.1's explicit requirement ("range-checking alone
 * happens to suffice for this specific table, but the decoder MUST implement
 * it as a table lookup, so a future gap in the middle of the range remains
 * rejectable").
 */
public final class FunctionRegistry {

    private FunctionRegistry() {}

    /** The pinned wire id of {@code contains(s: string, sub: string) -> bool}, needed by the decoder's needle-literal check (§B.6.5). */
    public static final int CONTAINS_ID = 22;
    public static final int STARTS_WITH_ID = 23;
    public static final int ENDS_WITH_ID = 24;

    public enum Name {
        SIZE_STRING, SIZE_BYTES, SIZE_LIST,
        INT_OF_DOUBLE, DOUBLE_OF_INT,
        ABS_INT, ABS_DOUBLE,
        MIN_INT, MIN_DOUBLE, MAX_INT, MAX_DOUBLE,
        SQRT, RADIANS, DEGREES,
        SIN, COS, TAN, ASIN, ACOS, ATAN, ATAN2,
        CONTAINS, STARTS_WITH, ENDS_WITH
    }

    public record Entry(int id, Name name, int arity) {}

    private static final Entry[] TABLE = {
        new Entry(1, Name.SIZE_STRING, 1),
        new Entry(2, Name.SIZE_BYTES, 1),
        new Entry(3, Name.SIZE_LIST, 1),
        new Entry(4, Name.INT_OF_DOUBLE, 1),
        new Entry(5, Name.DOUBLE_OF_INT, 1),
        new Entry(6, Name.ABS_INT, 1),
        new Entry(7, Name.ABS_DOUBLE, 1),
        new Entry(8, Name.MIN_INT, 2),
        new Entry(9, Name.MIN_DOUBLE, 2),
        new Entry(10, Name.MAX_INT, 2),
        new Entry(11, Name.MAX_DOUBLE, 2),
        new Entry(12, Name.SQRT, 1),
        new Entry(13, Name.RADIANS, 1),
        new Entry(14, Name.DEGREES, 1),
        new Entry(15, Name.SIN, 1),
        new Entry(16, Name.COS, 1),
        new Entry(17, Name.TAN, 1),
        new Entry(18, Name.ASIN, 1),
        new Entry(19, Name.ACOS, 1),
        new Entry(20, Name.ATAN, 1),
        new Entry(21, Name.ATAN2, 2),
        new Entry(22, Name.CONTAINS, 2),
        new Entry(23, Name.STARTS_WITH, 2),
        new Entry(24, Name.ENDS_WITH, 2),
    };

    private static final Map<Integer, Entry> BY_ID;
    static {
        Map<Integer, Entry> m = new java.util.HashMap<>();
        for (Entry e : TABLE) {
            m.put(e.id(), e);
        }
        BY_ID = Map.copyOf(m);
    }

    /** Returns the table entry for {@code functionId}, or {@code null} if it is not an assigned id (a hard-reject condition for the caller). */
    public static Entry byId(int functionId) {
        return BY_ID.get(functionId);
    }
}
