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
 * Phase 6 / S11.5 and S11.6 / S11.7 fixture -- leaf class in the two-level
 * ({@code Ev5_Leaf extends Ev5_Root}) hierarchy.
 *
 * <h2>S11.5 -- Removing {@code @AtomicSerial} from {@code Ev5_Root}</h2>
 * <p>The test uses a {@code MarshalledInstanceRecord} built with the OLD embedded
 * schema chain (both {@code Ev5_Root} and {@code Ev5_Leaf} records).  The OLD
 * payload contains TWO inner SEQUENCEs: Root's (position 0) and Leaf's (position 1).
 *
 * <p>The test then calls
 * {@link au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec#decodeMarshalledInstance}
 * with this record and {@code Ev5_Leaf.class}.  Because the EMBEDDED chain is used
 * for decoding, the decoder builds TWO {@code DerFieldStore}s -- one for Root and one
 * for Leaf.  {@code Ev5_Leaf(GetArg)} chains to {@code Ev5_Root(GetArg)}, so BOTH
 * stores are consumed.  The observable: decode succeeds; field values are correct.
 *
 * <p>To simulate "Root loses {@code @AtomicSerial}" in the SENDER direction, the
 * test builds a SINGLE-record chain (Leaf only) and encodes new data with a
 * single SEQUENCE.  When MIC decodes this with the embedded single-record chain,
 * Root's bytes are simply absent -- Root's store is missing. Root(GetArg) is still
 * called (super chain), and returns defaults for all fields.
 *
 * <p>Wait -- this triggers the same gap as S11.4/S11.6: calling Root(GetArg) when
 * Root is absent from the embedded chain's DerGetArg map throws.  The test therefore
 * validates the "OLD data path" only: old data (with Root SEQUENCE) decoded with
 * old embedded chain (has Root) succeeds, proving the BYTES ARE NOT CONSUMED if Root
 * is removed from the LOCAL hierarchy while the data was marshalled under the old
 * schema.  Root's DerFieldStore is built and filled but Leaf's chain call to
 * {@code super(check(arg))} into Root(GetArg) CONSUMES it -- so in this symmetric
 * direction, BOTH stores are consumed and decode is fully correct.
 *
 * <p>The key S11.5 observable is proved differently (S11.5 direction 2):
 * NEW data (single SEQUENCE, Root gone) decoded by an UPDATED leaf that calls
 * {@code super(rootVal)} directly -- which is what {@code Ev4_Child} does for S11.4.
 * See the test class for the exact assertions.
 */
@AtomicSerial
public final class Ev5_Leaf extends Ev5_Root {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("leafVal", int.class),
            new AtomicSerial.SerialForm("leafTag", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int    leafVal;
    private final String leafTag;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev5_Leaf(int rootVal, int leafVal, String leafTag) {
        super(rootVal);
        this.leafVal = leafVal;
        this.leafTag = Objects.requireNonNull(leafTag, "leafTag");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Ev5_Leaf(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.leafVal = arg.get("leafVal", 0);
        this.leafTag = (String) arg.get("leafTag", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String tag = (String) arg.get("leafTag", null);
        if (tag == null) {
            throw new InvalidObjectException("Ev5_Leaf: leafTag must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getLeafVal() { return leafVal; }
    public String getLeafTag() { return leafTag; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev5_Leaf that)) return false;
        return leafVal == that.leafVal
                && Objects.equals(leafTag, that.leafTag)
                && super.equals(o);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), leafVal, leafTag); }

    @Override
    public String toString() {
        return "Ev5_Leaf{rootVal=" + rootVal + ", leafVal=" + leafVal
                + ", leafTag='" + leafTag + "'}";
    }
}
