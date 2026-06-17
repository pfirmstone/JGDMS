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
 * Phase 6 / S11.4 fixture -- the child class whose superclass GAINED
 * {@code @AtomicSerial}.  This child is the "UNMODIFIED" variant described in
 * S11.4: it still calls a REGULAR {@code super(parentValue)} rather than
 * {@code super(check(arg))}.
 *
 * <p>The child carries {@code parentValue} in its OWN namespace (as it did
 * before the parent gained {@code @AtomicSerial}).  After the parent gained
 * {@code @AtomicSerial}, the NEW wire format includes BOTH a parent SEQUENCE
 * (with parent's own {@code parentValue}) and a child SEQUENCE (still carrying
 * {@code childParentValue} to feed the parent's regular constructor).
 *
 * <p>An "unmodified child" never calls {@code Ev4_Parent(GetArg)}, so the
 * parent's new SEQUENCE on the wire is present but never consumed.  The parent
 * is still constructed correctly via {@code super(childParentValue)}.
 *
 * <h2>S11.4 observable consequences tested</h2>
 * <ol>
 *   <li>New data (both parent and child SEQUENCEs): child's SEQUENCE is
 *       unchanged; parent's SEQUENCE is present on wire but ignored by this
 *       unmodified child.  Decode succeeds.</li>
 *   <li>Old data (child SEQUENCE only, encoded via MarshalledInstanceRecord
 *       with embedded single-record chain): child decodes correctly using
 *       only child's store.  Parent constructed via {@code super(childParentValue)}.
 *       No error from absent parent SEQUENCE.</li>
 * </ol>
 */
@AtomicSerial
public final class Ev4_Child extends Ev4_Parent {

    // -------------------------------------------------------------------------
    // Serial form -- Child's OWN namespace
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            // Child carries parentValue in its OWN namespace (legacy carry-forward).
            // Wire name "parentValue" is independent of Ev4_Parent's "parentValue"
            // (each is in a separate SEQUENCE / namespace).
            new AtomicSerial.SerialForm("parentValue", int.class),
            new AtomicSerial.SerialForm("childValue",  int.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): Child's OWN namespace (legacy carry-forward of parentValue). */
    public static void serialize(AtomicSerial.PutArg arg, Ev4_Child o) throws IOException {
        arg.put("parentValue", o.parentValue);
        arg.put("childValue",  o.childValue);
        arg.writeArgs();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final int childValue;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Ev4_Child(int parentValue, int childValue) {
        super(parentValue);
        this.childValue = childValue;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor -- UNMODIFIED child (calls regular super)
    // -------------------------------------------------------------------------

    /**
     * Deserialization constructor -- "Beta unchanged" (S11.4).
     *
     * <p>This child calls {@code super(childParentValue)} -- a REGULAR constructor,
     * NOT {@code super(check(arg))}.  Therefore {@code Ev4_Parent(GetArg)} is
     * never invoked.  Whether old data (no parent SEQUENCE) or new data (parent
     * SEQUENCE present but unconsumed) is being decoded, this constructor
     * succeeds without touching the parent's {@code GetArg} store.
     */
    public Ev4_Child(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        // checkAndGetParentValue(arg) runs from Child's frame -> resolves to Child's store.
        // Returns the "parentValue" from Child's own namespace, passes it to the REGULAR
        // Ev4_Parent(int) constructor (NOT Ev4_Parent(GetArg)).
        super(checkAndGetParentValue(arg));
        // Now assign Child's own field from Child's store.
        this.childValue = arg.get("childValue", 0);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    /**
     * Reads {@code parentValue} from Child's own store and returns it for use
     * in the {@code super(int)} call.
     *
     * <p>Called as {@code super(checkAndGetParentValue(arg))} from Child's stack
     * frame, so StackWalker routes {@code arg.get("parentValue", 0)} to Child's
     * {@link au.net.zeus.jgdms.der.getarg.DerFieldStore}.
     */
    public static int checkAndGetParentValue(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg.get("parentValue", 0);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getChildValue() { return childValue; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev4_Child that)) return false;
        return childValue == that.childValue && super.equals(o);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), childValue); }

    @Override
    public String toString() {
        return "Ev4_Child{parentValue=" + parentValue + ", childValue=" + childValue + "}";
    }
}
