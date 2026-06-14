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
import java.util.Objects;

/**
 * Phase 6 / §11.4 fixture — the superclass that GAINED {@code @AtomicSerial}.
 *
 * <p>Scenario: {@code Ev4_Parent} was previously a plain (non-{@code @AtomicSerial})
 * class.  Its child {@link Ev4_Child} carried the parent's state ({@code parentValue})
 * in Child's own namespace.  Later, {@code Ev4_Parent} gained {@code @AtomicSerial}
 * and now owns its own SEQUENCE with {@code parentValue}.
 *
 * <p>Observable: the parent's SEQUENCE is now present on the wire for new data.
 * For old data (no parent SEQUENCE), an "unmodified Child" (one that still calls
 * a regular {@code super(parentValue)} rather than {@code super(check(arg))})
 * does NOT invoke {@code Ev4_Parent(GetArg)}, so no store lookup occurs for the
 * parent — decode succeeds and the parent's fields come from what Child passes to
 * the regular constructor.
 */
@AtomicSerial
public class Ev4_Parent {

    // -------------------------------------------------------------------------
    // Serial form
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("parentValue", int.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    final int parentValue;

    // -------------------------------------------------------------------------
    // Value constructor (also called by Ev4_Child via regular super(...))
    // -------------------------------------------------------------------------

    public Ev4_Parent(int parentValue) {
        this.parentValue = parentValue;
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor (newly added to Ev4_Parent)
    // -------------------------------------------------------------------------

    public Ev4_Parent(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.parentValue = arg.get("parentValue", 0);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // No invariants on int fields; just return arg
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getParentValue() { return parentValue; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ev4_Parent that)) return false;
        return parentValue == that.parentValue;
    }

    @Override
    public int hashCode() { return Objects.hash(parentValue); }

    @Override
    public String toString() { return "Ev4_Parent{parentValue=" + parentValue + "}"; }
}
