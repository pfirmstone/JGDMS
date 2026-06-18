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
 * Phase 6 / S11.6 and S11.7 fixture -- the INSERTED (S11.6) / REMOVED (S11.7)
 * middle class between {@link Ev67_Alpha} and {@link Ev67_Beta}.
 *
 * <h2>S11.6 -- Insert Mid</h2>
 * <p>This class is NEW: it didn't exist when old data was encoded.  The S11.6 test
 * proves that NEW data (with Mid's SEQUENCE) decodes correctly.
 *
 * <p>Attempting to decode OLD data (lacking Mid's SEQUENCE) against the NEW chain
 * would require that {@code Ev67_Mid(GetArg)} return defaults when Mid has no store
 * in the embedded chain.  This exposes a gap in the current {@code DerGetArg}
 * implementation (which throws {@code InvalidObjectException} instead of
 * returning defaults) -- reported separately.
 *
 * <h2>S11.7 -- Remove Mid</h2>
 * <p>This class is REMOVED: it existed when old data was encoded.  Old data carries
 * Mid's SEQUENCE.  The S11.7 test decodes old data (with Mid's SEQUENCE) using
 * the OLD embedded schema chain (which includes Mid).  The decoder builds a
 * {@code DerFieldStore} for Mid and ALL field values are present, but since
 * {@code Ev67_Beta} chains directly to {@code Ev67_Alpha} in the new code (Mid
 * removed), Mid's store is built but never consumed.  No error occurs.
 */
@AtomicSerial
public class Ev67_Mid extends Ev67_Alpha {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("midVal", int.class),
            new AtomicSerial.SerialForm("midTag", String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): Mid's OWN namespace only. */
    public static void serialize(AtomicSerial.PutArg arg, Ev67_Mid o) throws IOException {
        arg.put("midVal", o.midVal);
        arg.put("midTag", o.midTag);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    final int    midVal;
    final String midTag;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev67_Mid(int alphaVal, int midVal, String midTag) {
        super(alphaVal);
        this.midVal = midVal;
        this.midTag = Objects.requireNonNull(midTag, "midTag");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev67_Mid(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.midVal = arg.get("midVal", 0);
        this.midTag = (String) arg.get("midTag", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String tag = (String) arg.get("midTag", null);
        if (tag == null) {
            throw new InvalidObjectException("Ev67_Mid: midTag must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getMidVal() { return midVal; }
    public String getMidTag() { return midTag; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev67_Mid that)) return false;
        return midVal == that.midVal
                && Objects.equals(midTag, that.midTag)
                && super.equals(o);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), midVal, midTag); }

    @Override
    public String toString() {
        return "Ev67_Mid{alphaVal=" + alphaVal + ", midVal=" + midVal
                + ", midTag='" + midTag + "'}";
    }
}
