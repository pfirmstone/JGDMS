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

package au.net.zeus.jgdms.der.marshal.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.util.Objects;

/**
 * Phase 5.2 test fixture — an {@code @AtomicSerial} class whose current
 * {@code serialForm()} declares THREE fields ({@code id}, {@code label},
 * {@code extra}).
 *
 * <h2>Divergence simulation strategy</h2>
 * <p>
 * This class always has three fields in its current {@code serialForm()}.
 * To simulate receiver/sender divergence the tests hand-build:
 * <ul>
 *   <li><b>Case (c) "receiver newer"</b> — an embedded schema with FEWER fields
 *       ({@code id} and {@code label} only, no {@code extra}). The payload matches
 *       the two-field schema. When decoded against this class, {@code extra} is absent
 *       from the store, so {@code arg.get("extra", "DEFAULT_EXTRA")} returns the
 *       default. The round-trip succeeds; the default value proves the code took the
 *       forward-compatibility path.</li>
 *   <li><b>Case (b) "sender newer"</b> — an embedded schema with MORE fields
 *       ({@code id}, {@code label}, {@code extra}, and a fourth {@code bonus} field).
 *       The payload encodes all four fields. When decoded against this class, the
 *       constructor only requests three fields; {@code bonus} sits in the store
 *       unrequested. No error occurs.</li>
 * </ul>
 *
 * <h2>check-before-construction</h2>
 * {@code check(GetArg)} enforces that {@code label} is non-null. The {@code extra}
 * and {@code bonus} fields are allowed to be absent (they have non-null defaults in
 * the constructor).
 */
@AtomicSerial
public final class VersionedRecord {

    // -------------------------------------------------------------------------
    // Current serial form (v3: three fields)
    // -------------------------------------------------------------------------

    /**
     * The CURRENT serialForm. Tests hand-build divergent schemas based on subsets
     * or supersets of this list.
     */
    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("id",    int.class),
            new AtomicSerial.SerialForm("label", String.class),
            new AtomicSerial.SerialForm("extra", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int    id;
    private final String label;
    private final String extra;   // absent in "old schema" → defaults to "DEFAULT_EXTRA"

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public VersionedRecord(int id, String label, String extra) {
        this.id    = id;
        this.label = Objects.requireNonNull(label, "label");
        this.extra = (extra == null) ? "DEFAULT_EXTRA" : extra;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor — check FIRST, then assign
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor.
     *
     * <ul>
     *   <li>{@code id} has default 0.</li>
     *   <li>{@code label} is validated non-null by {@code check}.</li>
     *   <li>{@code extra} defaults to {@code "DEFAULT_EXTRA"} when absent from
     *       the store (i.e. when the embedded schema is older and did not include
     *       this field — case (c)).</li>
     * </ul>
     */
    public VersionedRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.id    = arg.get("id",    0);
        this.label = (String) arg.get("label", null);
        this.extra = (String) arg.get("extra", "DEFAULT_EXTRA");
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String lbl = (String) arg.get("label", null);
        if (lbl == null) {
            throw new java.io.InvalidObjectException(
                    "VersionedRecord: label must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getId()    { return id; }
    public String getLabel() { return label; }
    public String getExtra() { return extra; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionedRecord that)) return false;
        return id == that.id
                && Objects.equals(label, that.label)
                && Objects.equals(extra, that.extra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, extra);
    }

    @Override
    public String toString() {
        return "VersionedRecord{id=" + id
                + ", label='" + label + "'"
                + ", extra='" + extra + "'"
                + '}';
    }
}
