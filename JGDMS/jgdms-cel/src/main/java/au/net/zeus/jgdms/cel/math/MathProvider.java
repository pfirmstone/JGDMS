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
 * Gates access to a {@link CorrectlyRoundedMath} implementation for the
 * evaluator's {@code sin}/{@code cos}/{@code tan}/{@code asin}/{@code
 * acos}/{@code atan}/{@code atan2} {@code CALL} handling.
 * <p>
 * Fail-closed by construction: {@link #none()} (the default an {@code
 * Evaluator} uses unless told otherwise) installs nothing, and {@link
 * #get()} throws rather than silently falling back to any approximation.
 * Per JGDMS-STD-011 §7.5's ratified mandate, evaluating a transcendental
 * {@code CALL} with no conformant provider installed MUST fail -- this class
 * is that enforcement point, not the evaluator's per-call logic, so the
 * fail-closed behaviour cannot be accidentally bypassed at a single call
 * site.
 * <p>
 * A genuinely conformant provider (T3 phase 2, a separate task -- see the
 * package documentation) is installed via {@link #conformant(CorrectlyRoundedMath)}.
 * The explicitly non-conformant {@link StrictMathPlaceholder} is reachable
 * <em>only</em> through {@link #nonConformantForTestingOnly()} -- a method
 * name chosen so that no caller can install it by accident or by a
 * plausible-looking generic call.
 */
public final class MathProvider {

    private final CorrectlyRoundedMath delegate;
    private final boolean conformant;

    private MathProvider(CorrectlyRoundedMath delegate, boolean conformant) {
        this.delegate = delegate;
        this.conformant = conformant;
    }

    /** No provider installed. {@link #get()} always throws. This is the default an {@code Evaluator} uses. */
    public static MathProvider none() {
        return new MathProvider(null, false);
    }

    /** Installs a genuinely STD-011 §7.5-conformant (correctly-rounded) implementation. */
    public static MathProvider conformant(CorrectlyRoundedMath impl) {
        if (impl == null) throw new NullPointerException("impl");
        return new MathProvider(impl, true);
    }

    /**
     * Installs the explicitly non-conformant {@link StrictMathPlaceholder}.
     * Calling this method <em>is</em> the required unmistakable opt-in --
     * there is no other way to reach the placeholder. Never call this in
     * production code; it exists to let tests exercise the transcendental
     * call machinery before a conformant provider (T3 phase 2) exists.
     */
    public static MathProvider nonConformantForTestingOnly() {
        return new MathProvider(new StrictMathPlaceholder(), false);
    }

    /** True iff the installed provider (if any) was installed via {@link #conformant(CorrectlyRoundedMath)}. */
    public boolean isConformant() {
        return conformant;
    }

    /**
     * Returns the installed provider.
     *
     * @throws IllegalStateException if no provider is installed ({@link #none()}) -- the fail-closed default
     */
    public CorrectlyRoundedMath get() {
        if (delegate == null) {
            throw new IllegalStateException(
                    "No correctly-rounded transcendental provider installed. JGDMS-STD-011 §7.5 "
                    + "REQUIRES correct rounding for sin/cos/tan/asin/acos/atan/atan2; a CALL to any of "
                    + "these cannot be evaluated without a conformant provider, fail-closed. Install one "
                    + "via MathProvider.conformant(...) (T3 phase 2's deliverable), or explicitly accept "
                    + "non-conformance for testing via MathProvider.nonConformantForTestingOnly().");
        }
        return delegate;
    }
}
