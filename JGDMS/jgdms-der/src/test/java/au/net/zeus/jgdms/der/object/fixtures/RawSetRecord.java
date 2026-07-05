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
import java.util.HashSet;
import java.util.Set;

/**
 * Auto-wiring fixture (memo §8 / E10): a RAW {@code Set} field. {@code Field.getGenericType()} is a
 * plain {@code Class} with no element type, so the rule resolves the element to the {@code Any} form
 * ({@code set:any}) -- automatically and safely. The raw declaration warns at compile time (that is
 * the point: a raw collection carries no schema-committed element type; per-element validation
 * shifts onto {@code check(GetArg)}, memo §8.2). Round-trips a mix of closed-subset values.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@AtomicSerial
public final class RawSetRecord {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("items", Set.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, RawSetRecord o) throws IOException {
        arg.put("items", o.items);
        arg.writeArgs();
    }

    private final Set items;

    public RawSetRecord(Set items) {
        this.items = items;
    }

    public RawSetRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        Object v = arg.get("items", null);
        this.items = (v == null) ? null : new HashSet((Set) v);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        return arg;
    }

    public Set getItems() { return items; }
}
