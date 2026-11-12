/*
 * CoreMathTintTestCopies: a test-only copy of the Java port CoreMathTint.mul of CORE-MATH's mul_tint, with one carry dropped on request
 * (src/binary64/atan2/tint.h at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7), for the controls of CoreMathTintTest.
 *
 * DERIVATION: this file is a copy of Java code that is itself a Java port, that is a modified and derived work, of mul_tint in
 * src/binary64/atan2/tint.h of the CORE-MATH project at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7, with one carry (or the whole carry word)
 * left out on request. It is therefore a derived work of that file and carries the upstream's notice, whatever the licence of the test that calls it
 * (operator ruling 2026-11-12, verbatim: "Keep the license MIT"). It is not part of CORE-MATH and is not endorsed by its authors. The copyright and
 * permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2023-2024 Paul Zimmermann and Alexei Sibidanov
 *
 * This file is part of the CORE-MATH project
 * (https://core-math.gitlabpages.inria.fr/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.net.zeus.jgdms.cel.math;

/**
 * The one copy of production arithmetic that the tests of the 192-bit routines need: {@link CoreMathTint#mul} with a carry dropped, so
 * that {@code CoreMathTintTest} can show its BigInteger model rejects exactly the inputs where the dropped carry matters. It is kept
 * in its own file because it is MIT-derived code (see the header); the BigInteger models and the other controls of {@code CoreMathTintTest}
 * are fresh formulations in BigInteger, not copies of the C text or of the port, and stay in the test file that carries the project's own licence header.
 * (Fix pass of phase B1, Opus review LOW-7: the copy used to sit inside the test file with the project's own header.)
 */
final class CoreMathTintTestCopies {

    private CoreMathTintTestCopies() {}

    /**
     * A copy of {@link CoreMathTint#mul} with one carry dropped, for the controls. {@code mutation} 0: none (must equal the
     * production routine); 1: {@code cm} never added into {@code m}; 2: the carry of the first {@code m} accumulation not added
     * to {@code h}; 3: the final {@code m -> h} carry (of adding {@code cm}) dropped.
     */
    static void mulCopy(CoreMathTint.Tint r, CoreMathTint.Tint a, CoreMathTint.Tint b, int mutation) {
        long ex = a.ex + b.ex;
        long sgn = a.sgn ^ b.sgn;
        long ah = a.h;
        long am = a.m;
        long al = a.l;
        long bh = b.h;
        long bm = b.m;
        long bl = b.l;
        long rH = Math.unsignedMultiplyHigh(ah, bh);
        long rM = ah * bh;
        long rL = ah * bm;
        long h = Math.unsignedMultiplyHigh(ah, bm);
        rM += h;
        rH += (mutation != 2 && Long.compareUnsigned(rM, h) < 0) ? 1L : 0L;
        long l = am * bh;
        h = Math.unsignedMultiplyHigh(am, bh);
        rL += l;
        long cm = Long.compareUnsigned(rL, l) < 0 ? 1L : 0L;
        rM += h;
        rH += Long.compareUnsigned(rM, h) < 0 ? 1L : 0L;
        long s1 = Math.unsignedMultiplyHigh(ah, bl);
        long s2 = Math.unsignedMultiplyHigh(am, bm);
        long s3 = Math.unsignedMultiplyHigh(al, bh);
        long s12 = s1 + s2;
        long carry = Long.compareUnsigned(s12, s1) < 0 ? 1L : 0L;
        long s123 = s12 + s3;
        carry += Long.compareUnsigned(s123, s12) < 0 ? 1L : 0L;
        l = s123;
        cm += carry;
        rL += l;
        cm += Long.compareUnsigned(rL, l) < 0 ? 1L : 0L;
        if (mutation != 1) {
            rM += cm;
            rH += (mutation != 3 && Long.compareUnsigned(rM, cm) < 0) ? 1L : 0L;
        }
        if ((rH >>> 63) == 0L) {
            rH = (rH << 1) | (rM >>> 63);
            rM = (rM << 1) | (rL >>> 63);
            rL = rL << 1;
            ex--;
        }
        r.ex = ex;
        r.sgn = sgn;
        r.h = rH;
        r.m = rM;
        r.l = rL;
    }
}
