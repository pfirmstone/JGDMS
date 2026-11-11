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

package au.net.zeus.jgdms.cel.math;

/**
 * An immutable unsigned 128-bit integer, written as two 64-bit halves. It exists because the
 * CORE-MATH C sources ({@link CoreMathSin}, {@link CoreMathCos}) use {@code unsigned __int128}
 * and Java has no such type. Every operation is exact arithmetic modulo 2^128, the same
 * semantics as the C type; the shifts take a count in [0, 127] (a count of 128 or more is
 * undefined in C and never occurs in the ported code).
 * <p>
 * <b>Where it is used.</b> Only on the cold "accurate" paths of sin and cos, which a call reaches
 * only when the fast path's error bound cannot decide the rounding. The hot fast paths and the
 * large-argument reduction of the fast path use plain {@code long} arithmetic and allocate
 * nothing beyond one {@code U128} for the reduction result.
 * <p>
 * <b>Independence of the check.</b> {@code U128Test} compares every operation here against
 * {@link java.math.BigInteger} on random operands, so this class is tested against a different
 * implementation of the same arithmetic, not against the C code.
 */
final class U128 {

    /** Upper 64 bits, as an unsigned value. */
    final long hi;
    /** Lower 64 bits, as an unsigned value. */
    final long lo;

    private U128(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    /** The value {@code hi * 2^64 + lo}. */
    static U128 of(long hi, long lo) {
        return new U128(hi, lo);
    }

    /**
     * The value {@code hi * 2^64 + lo} with the arguments in the order of CORE-MATH's C macro
     * {@code U128(l,h)} ({@code sin.c:151}, little-endian), so a table row is transcribed
     * without swapping its two words.
     */
    static U128 lh(long lo, long hi) {
        return new U128(hi, lo);
    }

    /** C {@code (u128) v} for a {@code uint64_t v}: zero extension. */
    static U128 ofU64(long v) {
        return new U128(0L, v);
    }

    /** C {@code (u128) a * b} for two {@code uint64_t}: the full 128-bit product. */
    static U128 mulFull(long a, long b) {
        return new U128(Math.unsignedMultiplyHigh(a, b), a * b);
    }

    /** C {@code this + b} (mod 2^128). */
    U128 add(U128 b) {
        long l = lo + b.lo;
        long carry = Long.compareUnsigned(l, lo) < 0 ? 1L : 0L;
        return new U128(hi + b.hi + carry, l);
    }

    /** C {@code this - b} (mod 2^128). */
    U128 sub(U128 b) {
        long l = lo - b.lo;
        long borrow = Long.compareUnsigned(lo, b.lo) < 0 ? 1L : 0L;
        return new U128(hi - b.hi - borrow, l);
    }

    /**
     * C {@code this * b} for two {@code u128}: the product's low 128 bits. The cross terms
     * {@code hi*b.lo + lo*b.hi} only reach the upper half, so they are taken mod 2^64.
     */
    U128 mul(U128 b) {
        return new U128(Math.unsignedMultiplyHigh(lo, b.lo) + hi * b.lo + lo * b.hi, lo * b.lo);
    }

    /**
     * CORE-MATH's {@code mhUU(a,b)} ({@code sin.c:179-186}): an approximation of
     * {@code floor(a*b / 2^128)} that drops the low-by-low product and its carry. It is NOT the
     * exact high product; the C error analysis is written for exactly this truncation, so it is
     * reproduced as is.
     */
    U128 mhUU(U128 b) {
        U128 ahbh = mulFull(hi, b.hi);
        long ahblHigh = Math.unsignedMultiplyHigh(hi, b.lo);   // (ah*bl) >> 64
        long albhHigh = Math.unsignedMultiplyHigh(lo, b.hi);   // (al*bh) >> 64
        return ahbh.add(ofU64(ahblHigh)).add(ofU64(albhHigh));
    }

    /** C {@code this << n}, 0 <= n < 128. */
    U128 shl(int n) {
        if (n == 0) {
            return this;
        }
        if (n >= 64) {
            return new U128(lo << (n - 64), 0L);
        }
        return new U128((hi << n) | (lo >>> (64 - n)), lo << n);
    }

    /** C {@code this >> n} (logical, the type is unsigned), 0 <= n < 128. */
    U128 shr(int n) {
        if (n == 0) {
            return this;
        }
        if (n >= 64) {
            return new U128(0L, hi >>> (n - 64));
        }
        return new U128(hi >>> n, (lo >>> n) | (hi << (64 - n)));
    }

    /** C {@code this & m}. */
    U128 and(U128 m) {
        return new U128(hi & m.hi, lo & m.lo);
    }

    /** C {@code this < b}, unsigned. */
    boolean ltU(U128 b) {
        return hi != b.hi ? Long.compareUnsigned(hi, b.hi) < 0 : Long.compareUnsigned(lo, b.lo) < 0;
    }

    /** C {@code this <= b}, unsigned. */
    boolean leU(U128 b) {
        return !b.ltU(this);
    }

    /** Bit {@code n} (0 = least significant) of this value, 0 <= n < 128. */
    int bit(int n) {
        return (int) ((n >= 64 ? hi >>> (n - 64) : lo >>> n) & 1L);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof U128 other && other.hi == hi && other.lo == lo;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hi) * 31 + Long.hashCode(lo);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(36);
        sb.append("0x");
        String h = Long.toHexString(hi);
        for (int i = h.length(); i < 16; i++) {
            sb.append('0');
        }
        sb.append(h);
        String l = Long.toHexString(lo);
        for (int i = l.length(); i < 16; i++) {
            sb.append('0');
        }
        sb.append(l);
        return sb.toString();
    }
}
