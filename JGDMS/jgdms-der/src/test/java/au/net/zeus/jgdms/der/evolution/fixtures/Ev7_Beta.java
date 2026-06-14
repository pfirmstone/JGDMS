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
 * Phase 6 / S11.7 fixture -- leaf class for the TWO-level hierarchy AFTER
 * {@code Ev67_Mid} has been REMOVED ({@code Ev7_Beta extends Ev67_Alpha}).
 *
 * <h2>S11.7 -- Remove {@code Ev67_Mid} from the hierarchy</h2>
 * <p>The NEW chain (after removal) is {@code [Ev7_Beta, Ev67_Alpha]}.
 *
 * <p>OLD data was encoded with OLD chain {@code [OldBeta, Ev67_Mid, Ev67_Alpha]}
 * where {@code OldBeta} was {@code Beta extends Mid}.  The old payload carries
 * THREE inner SEQUENCEs.
 *
 * <p>The S11.7 test uses a {@code MarshalledInstanceRecord} built with the OLD
 * embedded schema chain that INCLUDES {@code Ev67_Mid}.  Decoding via
 * {@link au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec} uses the embedded
 * (old) chain: the decoder reads THREE SEQUENCEs and builds THREE DerFieldStores
 * (Alpha, Mid, and Beta).  It then invokes {@code Ev7_Beta(GetArg)}, which chains
 * to {@code Ev67_Alpha(GetArg)} via {@code super(check(arg))}.
 *
 * <p>{@code Ev67_Mid}'s store is built and populated from the wire, but no
 * constructor (neither {@code Ev7_Beta} nor {@code Ev67_Alpha}) consumes it --
 * Mid is gone from the local hierarchy.  Those field values sit unrequested in
 * the DerGetArg map and become GC-eligible after construction.  No error occurs.
 *
 * <p>Observable assertions (S11.7):
 * <ul>
 *   <li>Decode completes without exception (Mid's bytes are harmlessly present).</li>
 *   <li>{@code Ev67_Alpha}'s field ({@code alphaVal}) is decoded correctly.</li>
 *   <li>{@code Ev7_Beta}'s own fields ({@code betaVal}, {@code betaTag}) are
 *       decoded correctly.</li>
 * </ul>
 */
@AtomicSerial
public final class Ev7_Beta extends Ev67_Alpha {

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

    public Ev7_Beta(int alphaVal, int betaVal, String betaTag) {
        super(alphaVal);
        this.betaVal = betaVal;
        this.betaTag = Objects.requireNonNull(betaTag, "betaTag");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev7_Beta(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
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
            throw new InvalidObjectException("Ev7_Beta: betaTag must not be null");
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
        if (!(o instanceof Ev7_Beta that)) return false;
        return betaVal == that.betaVal
                && Objects.equals(betaTag, that.betaTag)
                && super.equals(o);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), betaVal, betaTag); }

    @Override
    public String toString() {
        return "Ev7_Beta{alphaVal=" + alphaVal + ", betaVal=" + betaVal
                + ", betaTag='" + betaTag + "'}";
    }
}
