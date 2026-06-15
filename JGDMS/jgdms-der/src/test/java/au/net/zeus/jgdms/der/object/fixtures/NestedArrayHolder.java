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
import java.util.Arrays;

/**
 * B1 inc-3 fixture: an {@code @AtomicSerial} class with a {@code NestedValue[]}
 * field (wireType {@code "array:@AtomicSerial:...NestedValue"}). The array may be
 * null, empty, or contain null elements (each element is a nullable nested record).
 *
 * <p>Used to test:
 * <ul>
 *   <li>Round-trip of a {@code @AtomicSerial[]} field.</li>
 *   <li>Per-element null (DER NULL in element position).</li>
 *   <li>Polymorphism: declared type {@link NestedValue}, runtime element may be
 *       a {@link NestedValueSub} (each element carries its own embedded schema).</li>
 *   <li>Depth guard: the {@code depth} is threaded through to each element's
 *       {@code decodeNested} call.</li>
 * </ul>
 */
@AtomicSerial
public final class NestedArrayHolder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[]{
            new AtomicSerial.SerialForm("tag",      String.class),
            new AtomicSerial.SerialForm("elements", NestedValue[].class),
        };
    }

    private final String       tag;
    private final NestedValue[] elements; // may be null; elements may be null

    public NestedArrayHolder(String tag, NestedValue[] elements) {
        this.tag      = tag;
        this.elements = elements == null ? null : elements.clone();
    }

    public NestedArrayHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.tag      = (String)         arg.get("tag",      null);
        this.elements = (NestedValue[])  arg.get("elements", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        // No mandatory invariants
        return arg;
    }

    public String       getTag()      { return tag; }
    public NestedValue[] getElements() {
        return elements == null ? null : elements.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NestedArrayHolder that)) return false;
        if (!java.util.Objects.equals(tag, that.tag)) return false;
        if (elements == null && that.elements == null) return true;
        if (elements == null || that.elements == null) return false;
        return Arrays.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tag, Arrays.hashCode(elements));
    }

    @Override
    public String toString() {
        return "NestedArrayHolder{tag='" + tag
                + "', elements=" + Arrays.toString(elements) + '}';
    }
}
