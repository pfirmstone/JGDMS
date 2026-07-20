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

import au.net.zeus.jgdms.cel.testsupport.TestDer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Appendix B §B.5's ten canonical-form MUST-reject rules, and §B.13's probe
 * transcript (each probe reproduced here as its own test case against the
 * real decoder, not merely reasoned about).
 */
class CanonicalFormRejectTest {

    private static void assertRejected(byte[] wire) {
        assertThrows(CelDecodeException.class, () -> CelDecoder.decode(wire));
    }

    private static void assertAccepted(byte[] wire) throws Exception {
        assertNotNull(CelDecoder.decode(wire));
    }

    // ---- item 1: non-minimal tag encoding ----------------------------------

    /** §B.13.1 / §B.10.6: an unknown/reserved ExprNode tag using the high-tag-number form (tag 40, within [27]-[62]) is rejected -- reproduces the exact probe bytes {@code BF 28 00}. */
    @Test
    void item1_and_B10_6_and_B13_1_reservedHighTagNumberFormRejected() {
        byte[] exprBytes = TestDer.hexToBytes("bf2800");
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    /** An out-of-range tag (> 62) is likewise rejected -- tested separately per §B.11 item 2's explicit requirement (a decoder must not special-case "beyond the reserved block only"). */
    @Test
    void item1_outOfRangeTagAlsoRejected() {
        // Context, constructed, high-tag-number form, tag number 100 (>62): 0xBF, base-128(100)=0x64, len 0.
        byte[] exprBytes = TestDer.hexToBytes("bf6400");
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    // ---- item 2 / §B.13.2: non-minimal length encoding ---------------------

    @Test
    void item2_and_B10_5_and_B13_2_nonMinimalLengthRejected() throws Exception {
        // Canonical LIT_INT(30): 81 01 1E. Non-canonical: long-form length for a value < 128.
        byte[] canonical = TestDer.hexToBytes("81011e");
        assertAccepted(TestDer.wrapAsPredicateRecord(canonical));
        byte[] nonCanonical = TestDer.hexToBytes("8181011e");
        assertRejected(TestDer.wrapAsPredicateRecord(nonCanonical));
    }

    // ---- item 3: non-minimal INTEGER content -------------------------------

    @Test
    void item3_nonMinimalIntegerRedundantLeadingZero() {
        // LIT_INT with content 00 1E: redundant leading 0x00 (value would still be 30, but non-canonical).
        byte[] exprBytes = TestDer.ctxPrim(1, new byte[] { 0x00, 0x1E });
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item3_nonMinimalIntegerRedundantLeadingFF() {
        // Redundant leading 0xFF where the following byte's sign bit is already 1 (e.g. -2 as FF FE is canonical;
        // FF FF FE is redundant).
        byte[] exprBytes = TestDer.ctxPrim(1, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFE });
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    // ---- item 4: out-of-range LIT_INT --------------------------------------

    @Test
    void item4_literalAtMaxInt64IsAccepted() throws Exception {
        byte[] exprBytes = TestDer.litInt(Long.MAX_VALUE);
        assertAccepted(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item4_literalAtMinInt64IsAccepted() throws Exception {
        byte[] exprBytes = TestDer.litInt(Long.MIN_VALUE);
        assertAccepted(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item4_literalOneAbove2pow63minus1Rejected() {
        // 2^63 exactly -- one past Long.MAX_VALUE.
        BigInteger tooLarge = BigInteger.valueOf(2).pow(63);
        byte[] exprBytes = TestDer.ctxPrim(1, TestDer.minimalIntContent(tooLarge));
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item4_literalOneBelowMinInt64Rejected() {
        // -2^63 - 1 -- one past Long.MIN_VALUE.
        BigInteger tooSmall = BigInteger.valueOf(2).pow(63).negate().subtract(BigInteger.ONE);
        byte[] exprBytes = TestDer.ctxPrim(1, TestDer.minimalIntContent(tooSmall));
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    // ---- item 5: non-finite LIT_DOUBLE --------------------------------------

    @Test
    void item5_positiveInfinityRejected() {
        byte[] exprBytes = TestDer.litDoubleBits(0x7FF0000000000000L);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item5_negativeInfinityRejected() {
        byte[] exprBytes = TestDer.litDoubleBits(0xFFF0000000000000L);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item5_nonCanonicalNaNPayloadRejected() {
        // Any exponent-all-ones pattern other than the canonical quiet NaN is still "a NaN" -- and non-finite,
        // so it MUST be rejected regardless of payload.
        byte[] exprBytes = TestDer.litDoubleBits(0x7FF0000000000001L);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item5_finiteDoubleAccepted() throws Exception {
        byte[] exprBytes = TestDer.litDouble(1013.25);
        assertAccepted(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    // ---- item 8 / §B.13.6: non-canonical BOOLEAN ---------------------------

    @Test
    void item8_and_B13_6_canonicalTrueAccepted() throws Exception {
        assertAccepted(TestDer.wrapAsPredicateRecord(TestDer.hexToBytes("8001ff")));
    }

    @Test
    void item8_and_B13_6_nonCanonicalTrueRejected() {
        // BER-legal, DER-illegal encoding of TRUE (content 0x01 instead of 0xFF).
        assertRejected(TestDer.wrapAsPredicateRecord(TestDer.hexToBytes("800101")));
    }

    @Test
    void item8_falseMustBeExactlyZero() {
        byte[] exprBytes = TestDer.ctxPrim(0, new byte[] { 0x01 });
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    // ---- item 9 / §B.13.6: non-minimal ENUMERATED --------------------------

    @Test
    void item9_nonMinimalEnumeratedInDeclaredResultTypeRejected() {
        // transform context [1] EXPLICIT wrapping DeclaredResultType.listType with a non-minimal
        // ENUMERATED encoding: canonical is `82 01 02` (doubleT=2); non-canonical is `82 02 00 02`
        // (redundant leading 0x00) -- exactly §B.5 item 9 / §B.13.6's vector.
        byte[] nonCanonicalListType = TestDer.hexToBytes("82020002");
        byte[] context = TestDer.tlv(TestDer.contextTag(1, true), nonCanonicalListType);
        byte[] wire = TestDer.filterRecord(1, context, TestDer.litBool(true));
        assertRejected(wire);
    }

    @Test
    void item9_canonicalEnumeratedListTypeAccepted() throws Exception {
        byte[] canonicalListType = TestDer.hexToBytes("820102");
        byte[] context = TestDer.tlv(TestDer.contextTag(1, true), canonicalListType);
        byte[] wire = TestDer.filterRecord(1, context, TestDer.litBool(true));
        assertAccepted(wire);
    }

    // ---- item 10: non-canonical / ill-formed UTF-8 -------------------------

    @Test
    void item10_overlongUtf8Rejected() {
        // Overlong 2-byte encoding of ASCII 'A' (U+0041), which has a 1-byte encoding: C1 81 is an overlong form.
        byte[] overlong = new byte[] { (byte) 0xC1, (byte) 0x81 };
        byte[] exprBytes = TestDer.litStringRaw(overlong);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item10_loneSurrogateRejected() {
        // CESU-8-style encoding of a lone high surrogate (U+D800) -- ill-formed UTF-8, must be rejected.
        byte[] loneSurrogate = new byte[] { (byte) 0xED, (byte) 0xA0, (byte) 0x80 };
        byte[] exprBytes = TestDer.litStringRaw(loneSurrogate);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item10_truncatedMultiByteSequenceRejected() {
        // A 2-byte-sequence lead byte with no continuation byte.
        byte[] truncated = new byte[] { (byte) 0xC2 };
        byte[] exprBytes = TestDer.litStringRaw(truncated);
        assertRejected(TestDer.wrapAsPredicateRecord(exprBytes));
    }

    @Test
    void item10_wellFormedUtf8Accepted() throws Exception {
        byte[] exprBytes = TestDer.litString("GLS-é😀"); // includes a BMP accented char and a supplementary-plane emoji
        assertAccepted(TestDer.wrapAsPredicateRecord(exprBytes));
    }
}
