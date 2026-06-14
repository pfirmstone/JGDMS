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
 * Phase 4.4 fixture -- the {@code @AtomicSerial} superclass in the
 * non-{@code @AtomicSerial} subclass case.
 *
 * <p>{@code Bar extends Foo}, but only {@code Foo} carries {@code @AtomicSerial}.
 * The wire carries only {@code Foo}'s SEQUENCE; a decoded result is always a
 * {@code Foo}, never a {@code Bar} (S3.10, first rule).
 *
 * <p>Serial fields:
 * <ul>
 *   <li>{@code fooId}    (int)    -- an integer identifier.</li>
 *   <li>{@code fooLabel} (String) -- a required non-null label.</li>
 * </ul>
 */
@AtomicSerial
public class Foo {

    // -------------------------------------------------------------------------
    // Serial form -- Foo's own fields
    // -------------------------------------------------------------------------

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("fooId",    int.class),
            new AtomicSerial.SerialForm("fooLabel", String.class),
        };
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    final int    fooId;
    final String fooLabel;

    // -------------------------------------------------------------------------
    // Value constructor
    // -------------------------------------------------------------------------

    public Foo(int fooId, String fooLabel) {
        this.fooId    = fooId;
        this.fooLabel = Objects.requireNonNull(fooLabel, "fooLabel");
    }

    // -------------------------------------------------------------------------
    // @AtomicSerial constructor
    // -------------------------------------------------------------------------

    public Foo(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.fooId    = arg.get("fooId", 0);
        this.fooLabel = (String) arg.get("fooLabel", null);
    }

    // -------------------------------------------------------------------------
    // check-before-construction
    // -------------------------------------------------------------------------

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String label = (String) arg.get("fooLabel", null);
        if (label == null) {
            throw new InvalidObjectException("Foo: fooLabel must not be null");
        }
        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int    getFooId()    { return fooId; }
    public String getFooLabel() { return fooLabel; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Foo that)) return false;
        return fooId == that.fooId && Objects.equals(fooLabel, that.fooLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fooId, fooLabel);
    }

    @Override
    public String toString() {
        return "Foo{fooId=" + fooId + ", fooLabel='" + fooLabel + "'}";
    }
}
