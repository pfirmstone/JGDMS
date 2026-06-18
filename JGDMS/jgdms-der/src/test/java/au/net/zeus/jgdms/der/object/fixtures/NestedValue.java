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
 * B1 inc-2 test fixture: a simple {@code @AtomicSerial} class used as a
 * nested field value (STD-008 sec.16).
 *
 * <p>This class is intended to be held as the declared type of a field in
 * {@link OuterWithNested}; {@link NestedValueSub} is a subtype used for the
 * polymorphism round-trip test.
 */
@AtomicSerial
public class NestedValue {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("id",    int.class),
            new AtomicSerial.SerialForm("label", String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): emit each serialForm() field by name. */
    public static void serialize(AtomicSerial.PutArg arg, NestedValue o) throws IOException {
        arg.put("id",    o.id);
        arg.put("label", o.label);
        arg.writeArgs();
    }

    private final int    id;
    private final String label;

    public NestedValue(int id, String label) {
        this.id    = id;
        this.label = Objects.requireNonNull(label, "label");
    }

    public NestedValue(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.id    = arg.get("id", 0);
        this.label = (String) arg.get("label", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String lbl = (String) arg.get("label", null);
        if (lbl == null) {
            throw new InvalidObjectException("NestedValue: label must not be null");
        }
        return arg;
    }

    public int    getId()    { return id; }
    public String getLabel() { return label; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NestedValue that)) return false;
        return id == that.id && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() { return Objects.hash(id, label); }

    @Override
    public String toString() {
        return "NestedValue{id=" + id + ", label='" + label + "'}";
    }
}
