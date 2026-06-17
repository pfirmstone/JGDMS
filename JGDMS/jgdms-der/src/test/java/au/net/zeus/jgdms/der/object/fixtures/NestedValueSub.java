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
 * B1 inc-2 test fixture: a concrete subtype of {@link NestedValue}, also
 * {@code @AtomicSerial}, used for the polymorphic nested-field round-trip test.
 *
 * <p>When an {@link OuterWithNested} field is declared as {@code NestedValue}
 * but holds a {@code NestedValueSub} at runtime, the embedded schema in the
 * nested record must drive decode to reconstruct the correct subtype.
 */
@AtomicSerial
public final class NestedValueSub extends NestedValue {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("extra", String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): NestedValueSub's OWN namespace only. */
    public static void serialize(AtomicSerial.PutArg arg, NestedValueSub o) throws IOException {
        arg.put("extra", o.extra);
        arg.writeArgs();
    }

    private final String extra;

    public NestedValueSub(int id, String label, String extra) {
        super(id, label);
        this.extra = Objects.requireNonNull(extra, "extra");
    }

    public NestedValueSub(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(check(arg));
        this.extra = (String) arg.get("extra", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String e = (String) arg.get("extra", null);
        if (e == null) {
            throw new InvalidObjectException("NestedValueSub: extra must not be null");
        }
        return arg;
    }

    public String getExtra() { return extra; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NestedValueSub that)) return false;
        return super.equals(o) && Objects.equals(extra, that.extra);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), extra); }

    @Override
    public String toString() {
        return "NestedValueSub{id=" + getId() + ", label='" + getLabel()
                + "', extra='" + extra + "'}";
    }
}
