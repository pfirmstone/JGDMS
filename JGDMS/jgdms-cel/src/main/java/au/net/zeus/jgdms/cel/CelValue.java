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

package au.net.zeus.jgdms.cel;

import au.net.zeus.jgdms.cel.eval.CandidateProjection;

import java.util.Arrays;
import java.util.List;

/**
 * A value of the expression language's type system (JGDMS-STD-011 §4.1).
 * Every evaluation yields either a {@code CelValue} or a {@link CelError}
 * (see {@link EvalOutcome}) -- never anything else.
 * <p>
 * {@code double} values ({@link DoubleV}) are stored as the exact IEEE-754
 * binary64 bit pattern; record-generated {@code equals}/{@code hashCode} for
 * a {@code double} component compare bit patterns (via {@code
 * Double.doubleToLongBits}), which is exactly the granularity STD-011 §4.3.4
 * and §13's byte-exact conformance assertions need (NaN payloads and signed
 * zero are distinguished, matching the standard's own equality semantics for
 * checking results -- language-level {@code ==}/{@code !=} on {@code double}
 * is implemented separately in the evaluator per §4.3.3/§4.3.4, and MUST NOT
 * be confused with this Java-level {@code equals}).
 */
public sealed interface CelValue {

    CelType type();

    record BoolV(boolean value) implements CelValue {
        @Override public CelType type() { return CelType.BOOL; }
    }

    record IntV(long value) implements CelValue {
        @Override public CelType type() { return CelType.INT; }
    }

    record DoubleV(double value) implements CelValue {
        @Override public CelType type() { return CelType.DOUBLE; }

        /**
         * The canonical quiet-NaN bit pattern JGDMS-STD-011 §4.3.4 requires
         * at every boundary where a {@code double} leaves the evaluator (a
         * transform result being DER-encoded, a conformance-suite
         * observation): {@code 0x7FF8000000000000}. Non-NaN values
         * (including {@code -0.0} and {@code +-Inf}) are returned unchanged,
         * with their exact bit pattern.
         */
        public static final long CANONICAL_NAN_BITS = 0x7FF8000000000000L;

        /** Returns this value, or the canonical quiet NaN if this value is any NaN payload. Never mutates -- returns a new instance only when canonicalization actually changes something. */
        public DoubleV canonicalizedForResultBoundary() {
            if (Double.isNaN(value)) {
                double canonical = Double.longBitsToDouble(CANONICAL_NAN_BITS);
                if (Double.doubleToRawLongBits(value) == CANONICAL_NAN_BITS) return this;
                return new DoubleV(canonical);
            }
            return this;
        }
    }

    record StringV(String value) implements CelValue {
        public StringV {
            if (value == null) throw new NullPointerException("value");
        }
        @Override public CelType type() { return CelType.STRING; }
    }

    record BytesV(byte[] value) implements CelValue {
        public BytesV {
            if (value == null) throw new NullPointerException("value");
        }
        @Override public CelType type() { return CelType.BYTES; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytesV other)) return false;
            return Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "BytesV[" + value.length + " bytes]";
        }
    }

    /** The single value {@code null_t} may take (JGDMS-STD-011 §4.6). */
    record NullV() implements CelValue {
        public static final NullV INSTANCE = new NullV();
        @Override public CelType type() { return CelType.NULL_T; }
    }

    /**
     * A homogeneous finite sequence of scalar values (§4.7). {@code
     * elementType} is one of {@code BOOL/INT/DOUBLE/STRING/BYTES} -- never
     * {@code NULL_T}, {@code LIST}, or {@code OBJECT} (§5.3.2's element-type
     * restriction, enforced by whoever constructs a {@code ListV}).
     */
    record ListV(CelType elementType, List<CelValue> elements) implements CelValue {
        public ListV {
            if (elementType == null) throw new NullPointerException("elementType");
            if (elements == null) throw new NullPointerException("elements");
            elements = List.copyOf(elements);
        }
        @Override public CelType type() { return CelType.LIST; }
    }

    /**
     * An opaque nested field map (a nested {@code @AtomicSerial} object).
     * Supports only field selection and {@code has()} (§8.4) -- the
     * evaluator navigates {@code projection} per {@link CandidateProjection}
     * rather than this class exposing any further structure.
     */
    record ObjectV(CandidateProjection projection) implements CelValue {
        public ObjectV {
            if (projection == null) throw new NullPointerException("projection");
        }
        @Override public CelType type() { return CelType.OBJECT; }
    }
}
