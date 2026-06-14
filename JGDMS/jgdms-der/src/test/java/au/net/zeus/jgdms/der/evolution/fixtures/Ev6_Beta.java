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

package au.net.zeus.jgdms.der.evolution.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Objects;

/**
 * Phase 6 / §11.6 fixture — leaf class for the THREE-level hierarchy
 * ({@code Ev6_Beta extends Ev67_Mid extends Ev67_Alpha}), representing the
 * NEW state after {@code Ev67_Mid} was INSERTED.
 *
 * <h2>§11.6 — Insert a new {@code @AtomicSerial} class</h2>
 * <p>The NEW chain is {@code [Ev6_Beta, Ev67_Mid, Ev67_Alpha]}.  New data carries
 * three SEQUENCEs.
 *
 * <p>OLD data (before Mid was inserted) had only two SEQUENCEs: Alpha's and an
 * old Beta-that-extended-Alpha's.  Decoding OLD data against the NEW chain using
 * the embedded old schema (without Mid) would require that Mid receive defaults
 * for all its fields.  The current {@code DerGetArg} implementation throws in
 * this case (no store for Mid) — this is reported as a gap in the Phase 6 report.
 *
 * <p>The §11.6 test therefore validates only the NEW data path: encode a
 * {@code Ev6_Beta} instance with the NEW chain → decode → correct field values.
 * This proves that the new SEQUENCE structure works end-to-end.
 */
@AtomicSerial
public final class Ev6_Beta extends Ev67_Mid {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("betaVal", int.class),
            new AtomicSerial.SerialForm("betaTag", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int    betaVal;
    private final String betaTag;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev6_Beta(int alphaVal, int midVal, String midTag, int betaVal, String betaTag) {
        super(alphaVal, midVal, midTag);
        this.betaVal = betaVal;
        this.betaTag = Objects.requireNonNull(betaTag, "betaTag");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev6_Beta(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.betaVal = arg.get("betaVal", 0);
        this.betaTag = (String) arg.get("betaTag", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String tag = (String) arg.get("betaTag", null);
        if (tag == null) {
            throw new InvalidObjectException("Ev6_Beta: betaTag must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getBetaVal() { return betaVal; }
    public String getBetaTag() { return betaTag; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev6_Beta that)) return false;
        return betaVal == that.betaVal
                && Objects.equals(betaTag, that.betaTag)
                && super.equals(o);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), betaVal, betaTag); }

    @Override
    public String toString() {
        return "Ev6_Beta{alphaVal=" + alphaVal + ", midVal=" + midVal
                + ", midTag='" + midTag + "', betaVal=" + betaVal
                + ", betaTag='" + betaTag + "'}";
    }
}
