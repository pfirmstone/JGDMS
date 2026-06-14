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
 * Phase 4.3 fixture — leaf class in the three-level hierarchy
 * ({@code Gamma extends Beta extends Alpha}, all {@code @AtomicSerial}).
 *
 * <p>Gamma owns a private SEQUENCE containing:
 * <ul>
 *   <li>{@code gammaValue} (long) — Gamma-exclusive numeric field.</li>
 *   <li>{@code gammaTag}   (String) — Gamma-exclusive tag string.</li>
 * </ul>
 *
 * <h2>Constructor chain</h2>
 * <pre>
 *   Gamma(GetArg arg)
 *     → super(check(arg))              (Beta.check from Beta's frame)
 *       → Beta(GetArg)
 *         → super(check(arg))          (Alpha.check from Alpha.check's frame)
 *           → Alpha(GetArg) reads Alpha's fields
 *         → Beta reads Beta's fields
 *     → Gamma reads Gamma's fields
 * </pre>
 *
 * <p>The same {@code arg} reference travels the entire chain. StackWalker
 * dispatch at each level routes {@code arg.get(...)} to the correct
 * per-class DerFieldStore.
 */
@AtomicSerial
public final class Gamma extends Beta {

    // -------------------------------------------------------------------------
    // Serial form — Gamma's OWN fields only
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("gammaValue", long.class),
            new AtomicSerial.SerialForm("gammaTag",   String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final long   gammaValue;
    private final String gammaTag;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Gamma(int alphaX, String alphaLabel,
                 int betaX,  String betaOnly,
                 long gammaValue, String gammaTag) {
        super(alphaX, alphaLabel, betaX, betaOnly);
        this.gammaValue = gammaValue;
        this.gammaTag   = Objects.requireNonNull(gammaTag, "gammaTag");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor.
     *
     * <p>Evaluates {@code Gamma.check(arg)} (from Gamma's stack frame), then calls
     * {@code Beta(AtomicSerial.GetArg)} with the same {@code arg}. Beta chains
     * to Alpha. After the super-chain completes, Gamma assigns its own fields;
     * StackWalker routes those {@code get} calls to Gamma's DerFieldStore.
     */
    public Gamma(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        // Assign Gamma's own fields. StackWalker resolves to Gamma's DerFieldStore.
        this.gammaValue = arg.get("gammaValue", 0L);
        this.gammaTag   = (String) arg.get("gammaTag", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // gammaTag must not be null — validates from Gamma's frame → Gamma's store.
        String tag = (String) arg.get("gammaTag", null);
        if (tag == null) {
            throw new InvalidObjectException("Gamma: gammaTag must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long   getGammaValue() { return gammaValue; }
    public String getGammaTag()   { return gammaTag; }

    // -------------------------------------------------------------------------
    // equals / hashCode
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Gamma that)) return false;
        return gammaValue == that.gammaValue
                && Objects.equals(gammaTag, that.gammaTag)
                && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), gammaValue, gammaTag);
    }

    @Override
    public String toString() {
        return "Gamma{alphaX=" + super.getAlphaX() + ", alphaLabel='" + alphaLabel
                + "', betaX=" + getBetaX() + ", betaOnly='" + getBetaOnly()
                + "', gammaValue=" + gammaValue + ", gammaTag='" + gammaTag + "'}";
    }
}
