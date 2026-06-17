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
 * Phase 6 / S11.3 fixture -- "Add a non-{@code @AtomicSerial} superclass".
 *
 * <p>Scenario: {@code Ev3_EvolvedSub} was originally a standalone class with
 * two fields ({@code legacyName}, {@code subValue}).  A non-{@code @AtomicSerial}
 * superclass {@code NewBase} was later inserted above it.  {@code NewBase}
 * introduced a field ({@code newBaseTag}) that must survive serialisation, so
 * {@code Ev3_EvolvedSub} added {@code newBaseTag} at the END of its own
 * {@code serialForm()} -- exactly as S11.3 prescribes.
 *
 * <p>The NEW (current) {@code serialForm()} has three fields:
 * {@code legacyName}, {@code subValue}, {@code newBaseTag}.
 *
 * <p>OLD data encoded before {@code newBaseTag} was added carries only two
 * fields in the SEQUENCE.  Decoding old data against this class must succeed
 * with {@code newBaseTag} receiving its default value {@code "DEFAULT_BASE_TAG"}.
 */
@AtomicSerial
public final class Ev3_EvolvedSub {

    // -------------------------------------------------------------------------
    // Current (three-field) serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("legacyName",  String.class),
            new AtomicSerial.SerialForm("subValue",    int.class),
            new AtomicSerial.SerialForm("newBaseTag",  String.class),   // added when NewBase appeared
        };
    }

    /**
     * @AtomicSerial WRITE contract (STD-008). S11.3: {@code newBaseTag} was absorbed
     * into this class's OWN namespace (no non-{@code @AtomicSerial} superclass field
     * is reached), so all three are own fields.
     */
    public static void serialize(AtomicSerial.PutArg arg, Ev3_EvolvedSub o) throws IOException {
        arg.put("legacyName", o.legacyName);
        arg.put("subValue",   o.subValue);
        arg.put("newBaseTag", o.newBaseTag);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String legacyName;
    private final int    subValue;
    /**
     * Added when a non-{@code @AtomicSerial} superclass was introduced.
     * Absent in old data -> defaults to {@code "DEFAULT_BASE_TAG"}.
     */
    private final String newBaseTag;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev3_EvolvedSub(String legacyName, int subValue, String newBaseTag) {
        this.legacyName = Objects.requireNonNull(legacyName, "legacyName");
        this.subValue   = subValue;
        this.newBaseTag = (newBaseTag == null) ? "DEFAULT_BASE_TAG" : newBaseTag;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev3_EvolvedSub(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.legacyName = (String) arg.get("legacyName", null);
        this.subValue   = arg.get("subValue", 0);
        // newBaseTag absent in old data -> returns "DEFAULT_BASE_TAG"
        this.newBaseTag = (String) arg.get("newBaseTag", "DEFAULT_BASE_TAG");
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String name = (String) arg.get("legacyName", null);
        if (name == null) {
            throw new InvalidObjectException("Ev3_EvolvedSub: legacyName must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getLegacyName()  { return legacyName; }
    public int    getSubValue()    { return subValue; }
    public String getNewBaseTag()  { return newBaseTag; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev3_EvolvedSub that)) return false;
        return subValue == that.subValue
                && Objects.equals(legacyName, that.legacyName)
                && Objects.equals(newBaseTag, that.newBaseTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legacyName, subValue, newBaseTag);
    }

    @Override
    public String toString() {
        return "Ev3_EvolvedSub{legacyName='" + legacyName + "', subValue=" + subValue
                + ", newBaseTag='" + newBaseTag + "'}";
    }
}
