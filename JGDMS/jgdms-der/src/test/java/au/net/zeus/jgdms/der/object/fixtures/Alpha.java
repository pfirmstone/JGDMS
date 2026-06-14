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
 * Phase 4.3 fixture — root class in a three-level {@code @AtomicSerial} hierarchy.
 *
 * <p>Alpha owns a private SEQUENCE containing:
 * <ul>
 *   <li>{@code x} (int) — shares its name with {@link Beta#x} to prove per-class
 *       namespace isolation: "x" in Alpha and "x" in Beta live in separate SEQUENCEs
 *       and carry independent values.</li>
 *   <li>{@code alphaLabel} (String).</li>
 * </ul>
 *
 * <h2>Namespace isolation probe</h2>
 * <p>Alpha's constructor also reads {@code arg.get("betaOnly", "ALPHA_DEFAULT")}.
 * With correct StackWalker dispatch this resolves to Alpha's own {@link
 * au.net.zeus.jgdms.der.getarg.DerFieldStore}. Field "betaOnly" does not exist in
 * Alpha's schema, so the default {@code "ALPHA_DEFAULT"} is returned — proving that
 * Alpha cannot see Beta's namespace. The result is stored in the transient (non-serial)
 * field {@link #betaOnlySeenByAlpha}, exposed by {@link #getBetaOnlySeenByAlpha()}.
 */
@AtomicSerial
public class Alpha {

    // -------------------------------------------------------------------------
    // Serial form — Alpha's OWN fields only
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("x",          int.class),
            new AtomicSerial.SerialForm("alphaLabel", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Alpha's own {@code x}. Same name as {@link Beta#x} — different SEQUENCE. */
    final int    x;
    final String alphaLabel;

    /**
     * Transient observation: what "betaOnly" looked like from Alpha's GetArg frame.
     * Must always be "ALPHA_DEFAULT" if StackWalker dispatch is correct.
     * NOT in serialForm — never encoded.
     */
    final String betaOnlySeenByAlpha;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Alpha(int x, String alphaLabel) {
        this.x                   = x;
        this.alphaLabel          = Objects.requireNonNull(alphaLabel, "alphaLabel");
        this.betaOnlySeenByAlpha = "ALPHA_DEFAULT";
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor — check FIRST, then assign fields
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor — called directly when Alpha is the leaf, or via
     * {@code super(check(arg))} when Beta/Gamma is the leaf.
     *
     * <p>The {@code arg.get("betaOnly", "ALPHA_DEFAULT")} call below is the
     * namespace-isolation probe. When called from within the Alpha constructor, the
     * StackWalker finds {@code Alpha} as the first registered class on the stack and
     * dispatches to Alpha's DerFieldStore. "betaOnly" is absent from Alpha's store,
     * so the default {@code "ALPHA_DEFAULT"} is returned.
     */
    public Alpha(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // check runs first — validates Alpha's own fields; stack frame is Alpha.check
        check(arg);
        // Assign Alpha's own fields — stack frame is Alpha.<init>
        this.x          = arg.get("x", 0);
        this.alphaLabel = (String) arg.get("alphaLabel", null);
        // Namespace-isolation probe: attempt to read a Beta-only field from Alpha's frame.
        // StackWalker resolves this to Alpha's DerFieldStore; "betaOnly" is absent there.
        this.betaOnlySeenByAlpha = (String) arg.get("betaOnly", "ALPHA_DEFAULT");
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String label = (String) arg.get("alphaLabel", null);
        if (label == null) {
            throw new InvalidObjectException("Alpha: alphaLabel must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getX()                   { return x; }
    public String getAlphaLabel()          { return alphaLabel; }

    /**
     * Returns the value Alpha's constructor observed when it read "betaOnly" via
     * GetArg. Must always equal "ALPHA_DEFAULT" when StackWalker dispatch is correct.
     */
    public String getBetaOnlySeenByAlpha() { return betaOnlySeenByAlpha; }

    // -------------------------------------------------------------------------
    // equals / hashCode — serial fields only (not transient betaOnlySeenByAlpha)
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alpha that)) return false;
        return x == that.x && Objects.equals(alphaLabel, that.alphaLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, alphaLabel);
    }

    @Override
    public String toString() {
        return "Alpha{x=" + x + ", alphaLabel='" + alphaLabel
                + "', betaOnlySeenByAlpha='" + betaOnlySeenByAlpha + "'}";
    }
}
