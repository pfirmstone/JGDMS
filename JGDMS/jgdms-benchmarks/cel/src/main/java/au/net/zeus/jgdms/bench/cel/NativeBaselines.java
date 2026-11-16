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

import au.net.zeus.jgdms.cel.math.CorrectlyRoundedMath;

/**
 * Native Java baselines of the SAME arithmetic as the E1 and E2 texts of {@link Stage1Expressions}, with every
 * intermediate held in a local the way a hand-written program would. They are the control the CEL timings are read
 * against: B-CR passes the correctly-rounded functions the evaluator itself calls
 * ({@code MathProviders.correctlyRounded().get()}), B-STRICT passes {@link Trig.Strict}.
 * <p>
 * WHY the operation order is spelled out term by term and never simplified: the CEL text and this code must round
 * identically, so the comparison can be bit-for-bit. Specifically (each a place a "tidy" rewrite would change bits):
 * {@code a5 * t2 * t2} is {@code (a5 * t2) * t2}; {@code 2 * a * a} is {@code (2 * a) * a}; the decentering constants
 * {@code 2 * pR} and {@code 2 * pC} are folded once, as the generator folds them; the hypotenuse is
 * {@code sqrt(a * a + b * b)}, NOT {@code Math.hypot} (which rounds differently and is not in STD-011 §7.2); {@code sqrt}
 * is {@link Math#sqrt} in both baselines because it is IEEE-exact and so the same in either.
 * <p>
 * CALL COUNTS differ from the CEL text, by design and disclosed: with locals, E1 makes 4 transcendental calls (the text
 * writes {@code cos(elevation)} twice, so 5) and E2 makes 6 (the written-out text makes 156,553 per in-front record,
 * MEASURED by the counting provider in the correctness test, prediction P9). Repeated pure calls return identical bits, so
 * the results are unaffected; the timing difference is the cost of having no variables.
 * <p>
 * Not thread-safe (the instance owns no state but the caller's output array); one instance per thread is cheap.
 */
public final class NativeBaselines {

    private final CorrectlyRoundedMath m;
    private final Stage1Expressions.Camera k;

    public NativeBaselines(CorrectlyRoundedMath m, Stage1Expressions.Camera k) {
        this.m = m;
        this.k = k;
    }

    /** E1: {@code out = {x, y, z}} (length 3). */
    public void e1(float rangeF, float azimuthF, float elevationF, double[] out) {
        double r = rangeF;
        double az = azimuthF;
        double el = elevationF;
        double ce = m.cos(el);
        out[0] = r * ce * m.cos(az);
        out[1] = r * ce * m.sin(az);
        out[2] = r * m.sin(el);
    }

    /**
     * E2, forward branch: {@code out = {colCrop, rowCrop}} (length 2), or {@code {-1.0, -1.0}} for a point behind the
     * camera.
     */
    public void e2(float rangeF, float azimuthF, float elevationF, double[] out) {
        double r = rangeF;
        double az = azimuthF;
        double el = elevationF;
        double ce = m.cos(el);
        double px = r * ce * m.cos(az);
        double py = r * ce * m.sin(az);
        double pz = r * m.sin(el);
        double xc = k.cx0 * px + k.cx1 * py + k.cx2 * pz + k.dx;
        double yc = k.cy0 * px + k.cy1 * py + k.cy2 * pz + k.dy;
        double zc = k.cz0 * px + k.cz1 * py + k.cz2 * pz + k.dz;
        if (zc >= 0.0) {
            out[0] = -1.0;
            out[1] = -1.0;
            return;
        }
        double rho = Math.sqrt(xc * xc + yc * yc);
        double theta = m.atan2(rho, -zc);
        double ar = rho < 1.0E-15 ? 0.0 : theta * (xc / rho);
        double br = rho < 1.0E-15 ? 0.0 : theta * (yc / rho);
        double t2 = theta * theta;
        double rs = 1.0 + k.a3 * t2 + k.a5 * t2 * t2 + k.a7 * t2 * t2 * t2 + k.a9 * t2 * t2 * t2 * t2;
        double s = theta < 1.0E-15 ? 1.0 : (theta * rs) / theta;
        double a1 = ar * s;
        double b1 = br * s;
        double r2 = a1 * a1 + b1 * b1;
        double a2 = a1 + (k.pC * (r2 + 2.0 * a1 * a1) + k.twoPR * a1 * b1);
        double b2 = b1 + (k.pR * (r2 + 2.0 * b1 * b1) + k.twoPC * a1 * b1);
        double th2 = Math.sqrt(a2 * a2 + b2 * b2);
        double rpx = k.focalPx * m.tan(th2);
        double ux = th2 < 1.0E-15 ? 0.0 : a2 / th2;
        double uy = th2 < 1.0E-15 ? 0.0 : b2 / th2;
        out[0] = k.ppCol + rpx * ux;
        out[1] = k.ppRow + rpx * uy;
    }

    /**
     * Whether a record is in front of the camera (the forward branch of E2 is taken), by the baseline's own camera-frame
     * z; the one definition the sample selection and the named-record choice share.
     */
    public boolean inFront(float rangeF, float azimuthF, float elevationF) {
        double r = rangeF;
        double az = azimuthF;
        double el = elevationF;
        double ce = m.cos(el);
        double px = r * ce * m.cos(az);
        double py = r * ce * m.sin(az);
        double pz = r * m.sin(el);
        return !(k.cz0 * px + k.cz1 * py + k.cz2 * pz + k.dz >= 0.0);
    }
}
