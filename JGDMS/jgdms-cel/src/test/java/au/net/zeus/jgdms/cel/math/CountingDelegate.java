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

import java.util.concurrent.atomic.LongAdder;

/**
 * A {@link CrMath} that counts how often each function is called: installed as the delegate of {@code CoreMath}, it shows which calls the
 * ported code answered itself and which fell back. One definition for the phase A and phase B1 differential tests.
 */
final class CountingDelegate implements CorrectlyRoundedMath {
    private final CrMath real = new CrMath();
    final LongAdder sin = new LongAdder();
    final LongAdder cos = new LongAdder();
    final LongAdder tan = new LongAdder();
    final LongAdder asin = new LongAdder();
    final LongAdder acos = new LongAdder();
    final LongAdder atan = new LongAdder();
    final LongAdder atan2 = new LongAdder();

    @Override
    public double sin(double x) {
        sin.increment();
        return real.sin(x);
    }

    @Override
    public double cos(double x) {
        cos.increment();
        return real.cos(x);
    }

    @Override
    public double tan(double x) {
        tan.increment();
        return real.tan(x);
    }

    @Override
    public double asin(double x) {
        asin.increment();
        return real.asin(x);
    }

    @Override
    public double acos(double x) {
        acos.increment();
        return real.acos(x);
    }

    @Override
    public double atan(double x) {
        atan.increment();
        return real.atan(x);
    }

    @Override
    public double atan2(double y, double x) {
        atan2.increment();
        return real.atan2(y, x);
    }

    long ported() {
        return asin.sum() + acos.sum() + atan.sum() + atan2.sum();
    }
}
