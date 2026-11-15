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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.SplittableRandom;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

/**
 * Checks {@link U128}, the exact 128-bit unsigned arithmetic the CORE-MATH port needs because Java
 * has no {@code unsigned __int128}, against {@link BigInteger} (a different implementation of the
 * same arithmetic). Runtime tier: FAST (default suite), well under a second.
 * <p>
 * Predictions are P10 of {@code CoreMathSinCosDifferentialTest}'s javadoc, sealed before the first run.
 * The reference for {@code mhUU} is a model of the C text ({@code sin.c:179-186}) written in
 * BigInteger: {@code ahbh + (ahbl >> 64) + (albh >> 64)} mod 2^128, with the three products formed
 * from the 64-bit halves. It therefore checks the emulation of that C expression, not the C's
 * numerical meaning.
 */
class U128Test {

    private static final BigInteger MOD = BigInteger.ONE.shiftLeft(128);
    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private static BigInteger big(U128 u) {
        return new BigInteger(Long.toUnsignedString(u.hi)).shiftLeft(64).add(new BigInteger(Long.toUnsignedString(u.lo)));
    }

    private static U128 u128(BigInteger b) {
        BigInteger m = b.mod(MOD);
        return U128.of(m.shiftRight(64).longValue(), m.and(M64).longValue());
    }

    private static BigInteger mhUUModel(BigInteger a, BigInteger b) {
        BigInteger ah = a.shiftRight(64);
        BigInteger al = a.and(M64);
        BigInteger bh = b.shiftRight(64);
        BigInteger bl = b.and(M64);
        BigInteger ahbh = ah.multiply(bh);
        BigInteger ahbl = ah.multiply(bl);
        BigInteger albh = al.multiply(bh);
        return ahbh.add(ahbl.shiftRight(64)).add(albh.shiftRight(64)).mod(MOD);
    }

    private static U128[] operands(long seed, int n) {
        long[] edge = {0L, 1L, 2L, -1L, -2L, Long.MIN_VALUE, Long.MAX_VALUE, 0x8000000000000001L, 0x00000000ffffffffL, 0xffffffff00000000L};
        U128[] ops = new U128[n + edge.length * edge.length];
        int k = 0;
        for (long h : edge) {
            for (long l : edge) {
                ops[k++] = U128.of(h, l);
            }
        }
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = 0; i < n; i++) {
            ops[k++] = U128.of(r.nextLong(), r.nextLong());
        }
        return ops;
    }

    /** Counts operand pairs where {@code op} (the emulation) differs from {@code model}. */
    private static long mismatches(U128[] ops, BiFunction<U128, U128, U128> op, BiFunction<BigInteger, BigInteger, BigInteger> model) {
        long bad = 0;
        for (int i = 0; i < ops.length; i++) {
            U128 a = ops[i];
            U128 b = ops[(i * 7 + 3) % ops.length];
            BigInteger want = model.apply(big(a), big(b)).mod(MOD);
            if (!big(op.apply(a, b)).equals(want)) {
                bad++;
            }
        }
        return bad;
    }

    @Test
    void arithmeticMatchesBigInteger() {
        U128[] ops = operands(20261112L, 20_000);
        long n = ops.length;
        long add = mismatches(ops, U128::add, BigInteger::add);
        long sub = mismatches(ops, U128::sub, BigInteger::subtract);
        long mul = mismatches(ops, U128::mul, BigInteger::multiply);
        long mhuu = mismatches(ops, U128::mhUU, U128Test::mhUUModel);
        long and = mismatches(ops, U128::and, BigInteger::and);
        StringBuilder sb = new StringBuilder(160);
        sb.append("U128 vs BigInteger over ").append(n).append(" operand pairs: add ").append(add).append(", sub ").append(sub)
                .append(", mul ").append(mul).append(", mhUU ").append(mhuu).append(", and ").append(and).append(" mismatches");
        System.out.println(sb);
        assertEquals(0L, add + sub + mul + mhuu + and, sb.toString());
    }

    @Test
    void shiftsComparisonsAndBitsMatchBigInteger() {
        U128[] ops = operands(20261113L, 2_000);
        long bad = 0;
        long checked = 0;
        for (U128 a : ops) {
            BigInteger ba = big(a);
            for (int n = 0; n < 128; n++) {
                checked++;
                if (!big(a.shl(n)).equals(ba.shiftLeft(n).mod(MOD))) {
                    bad++;
                }
                if (!big(a.shr(n)).equals(ba.shiftRight(n))) {
                    bad++;
                }
                if (a.bit(n) != (ba.testBit(n) ? 1 : 0)) {
                    bad++;
                }
            }
        }
        for (int i = 0; i < ops.length; i++) {
            U128 a = ops[i];
            U128 b = ops[(i * 7 + 3) % ops.length];
            checked++;
            if (a.ltU(b) != (big(a).compareTo(big(b)) < 0)) {
                bad++;
            }
            if (a.leU(b) != (big(a).compareTo(big(b)) <= 0)) {
                bad++;
            }
        }
        String msg = new StringBuilder(80).append("U128 shifts/bits/compare vs BigInteger: mismatches ").append(bad).append(" of ").append(checked).toString();
        System.out.println(msg);
        assertEquals(0L, bad, msg);
    }

    @Test
    void mulFullAndLhOrderMatchBigInteger() {
        SplittableRandom r = new SplittableRandom(20261114L);
        long bad = 0;
        for (int i = 0; i < 20_000; i++) {
            long a = r.nextLong();
            long b = r.nextLong();
            BigInteger want = new BigInteger(Long.toUnsignedString(a)).multiply(new BigInteger(Long.toUnsignedString(b)));
            if (!big(U128.mulFull(a, b)).equals(want)) {
                bad++;
            }
        }
        assertEquals(0L, bad, "mulFull mismatches");
        // the C macro U128(l,h) lists the LOW word first (sin.c:151)
        U128 u = U128.lh(0x1111111111111111L, 0x2222222222222222L);
        assertEquals(0x2222222222222222L, u.hi);
        assertEquals(0x1111111111111111L, u.lo);
    }

    /** The control: the comparison above must be able to fail. An addition that drops the carry is rejected. */
    @Test
    void controlAdditionWithoutCarryIsRejected() {
        U128[] ops = operands(20261112L, 20_000);
        long bad = mismatches(ops, (a, b) -> U128.of(a.hi + b.hi, a.lo + b.lo), BigInteger::add);
        System.out.println(new StringBuilder(80).append("U128 control (add without carry) rejected on ").append(bad).append(" of ").append(ops.length).append(" operand pairs"));
        assertTrue(bad > 0, "the comparison accepted an addition that drops the carry: it cannot fail");
    }
}
