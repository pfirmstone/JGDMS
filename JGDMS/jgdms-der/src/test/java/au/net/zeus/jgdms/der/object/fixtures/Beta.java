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

package au.net.zeus.jgdms.der.object.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Objects;

/**
 * Phase 4.3 fixture — middle class in the three-level hierarchy
 * ({@code Beta extends Alpha}, both {@code @AtomicSerial}).
 *
 * <p>Beta owns its private SEQUENCE containing:
 * <ul>
 *   <li>{@code x} (int) — deliberately shares the name with {@link Alpha#x} to
 *       exercise namespace isolation: the two {@code "x"} fields are in entirely
 *       separate SEQUENCEs with independent values.</li>
 *   <li>{@code betaOnly} (String) — a Beta-exclusive field that Alpha must never
 *       see through its own {@link au.net.zeus.jgdms.der.getarg.DerFieldStore}.</li>
 * </ul>
 *
 * <h2>Constructor chain</h2>
 * <pre>
 *   Beta(GetArg arg) → super(check(arg))      (passes same arg to Alpha)
 *                    → Alpha(arg) reads Alpha's fields from Alpha's store
 *   Beta(arg) then reads Beta's fields from Beta's store
 * </pre>
 *
 * <p>The same {@code arg} object is shared up the constructor chain. StackWalker
 * dispatch ensures that {@code arg.get(...)} inside Alpha's constructor resolves
 * to Alpha's DerFieldStore, and {@code arg.get(...)} inside Beta's constructor
 * resolves to Beta's DerFieldStore — even though both use the same {@code arg}
 * reference.
 */
@AtomicSerial
public class Beta extends Alpha {

    // -------------------------------------------------------------------------
    // Serial form — Beta's OWN fields only
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("x",        int.class),
            new AtomicSerial.SerialForm("betaOnly", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Beta's own {@code x}. Same name as {@link Alpha#x}, independent value. */
    final int    x;
    final String betaOnly;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Beta(int alphaX, String alphaLabel, int betaX, String betaOnly) {
        super(alphaX, alphaLabel);
        this.x        = betaX;
        this.betaOnly = Objects.requireNonNull(betaOnly, "betaOnly");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor.
     *
     * <p>{@code super(check(arg))} evaluates {@code Beta.check(arg)} first (from
     * Beta's stack frame → Beta's DerFieldStore), then calls
     * {@code Alpha(AtomicSerial.GetArg)} with the SAME {@code arg}. Inside
     * {@code Alpha.<init>}, StackWalker finds Alpha on the stack → Alpha's store.
     *
     * <p>After super() returns, Beta assigns its own fields. {@code arg.get("x", 0)}
     * here resolves to Beta's store (Beta is the first registered class on the
     * current stack), returning Beta's {@code x}, not Alpha's.
     */
    public Beta(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // Beta.check(arg) runs from Beta's frame → validates Beta's fields.
        // Then Alpha(arg) is invoked with the same arg; Alpha reads its own store.
        super(check(arg));
        // Now assign Beta's own fields. StackWalker resolves to Beta's DerFieldStore.
        this.x        = arg.get("x", 0);
        this.betaOnly = (String) arg.get("betaOnly", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // Validate Beta's own fields.
        // arg.get("betaOnly", null) dispatches to Beta's store (Beta.check is on stack).
        String b = (String) arg.get("betaOnly", null);
        if (b == null) {
            throw new InvalidObjectException("Beta: betaOnly must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Beta's own x (NOT Alpha's x). */
    public int    getBetaX()    { return x; }
    /** Alpha's x (inherited). Use {@link #getAlphaX()} for explicit disambiguation. */
    public int    getAlphaX()   { return super.x; }
    public String getBetaOnly() { return betaOnly; }

    // -------------------------------------------------------------------------
    // equals / hashCode
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Beta that)) return false;
        // Check Beta's own fields AND Alpha's serial fields
        return x == that.x
                && Objects.equals(betaOnly, that.betaOnly)
                && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), x, betaOnly);
    }

    @Override
    public String toString() {
        return "Beta{alphaX=" + super.x + ", alphaLabel='" + alphaLabel
                + "', betaX=" + x + ", betaOnly='" + betaOnly + "'}";
    }
}
