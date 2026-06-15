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
 * B1 inc-2 test fixture: an {@code @AtomicSerial} class with a nested
 * {@code @AtomicSerial} object field (STD-008 sec.16).
 *
 * <p>The field {@code inner} is declared as the {@link NestedValue} supertype;
 * at runtime it may hold a {@link NestedValueSub} (polymorphism test).
 * It may also be {@code null} (null-nested test).
 *
 * <p>The field {@code tag} is a plain String so the class has at least one
 * non-nested field, exercising mixed field types in the same SEQUENCE.
 */
@AtomicSerial
public final class OuterWithNested {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("tag",   String.class),
            // Declared type NestedValue is @AtomicSerial -> wireType "@AtomicSerial"
            new AtomicSerial.SerialForm("inner", NestedValue.class),
        };
    }

    private final String      tag;
    private final NestedValue inner; // may be null; may be a NestedValueSub at runtime

    public OuterWithNested(String tag, NestedValue inner) {
        this.tag   = Objects.requireNonNull(tag, "tag");
        this.inner = inner; // null is allowed
    }

    public OuterWithNested(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.tag   = (String)      arg.get("tag",   null);
        this.inner = (NestedValue) arg.get("inner", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        String t = (String) arg.get("tag", null);
        if (t == null) {
            throw new InvalidObjectException("OuterWithNested: tag must not be null");
        }
        return arg;
    }

    public String      getTag()   { return tag; }
    public NestedValue getInner() { return inner; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OuterWithNested that)) return false;
        return Objects.equals(tag, that.tag)
                && Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode() { return Objects.hash(tag, inner); }

    @Override
    public String toString() {
        return "OuterWithNested{tag='" + tag + "', inner=" + inner + '}';
    }
}
