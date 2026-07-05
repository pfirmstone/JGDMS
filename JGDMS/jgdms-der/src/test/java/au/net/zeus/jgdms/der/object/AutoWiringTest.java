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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.object.fixtures.AutoWiredSetRecord;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.object.fixtures.RawSetRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auto-wiring conformance (memo §8): the deferred item -- a plain {@code Set<Foo>}/{@code Map<K,V>}
 * field now auto-derives its collection token from the declared generic signature, with NO
 * developer-supplied element wire-type and NO annotation. A raw {@code Set} field auto-derives the
 * {@code Any} fallback. Both are exercised end-to-end via {@link SchemaGenerator#generate}.
 */
class AutoWiringTest {

    // -------------------------------------------------------------------------
    // A plain Set<Foo> field auto-wires to set:@AtomicSerial (E1) and round-trips.
    // -------------------------------------------------------------------------

    @Test
    void plainSetOfFoo_autoWiresToSetAtomicSerial() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AutoWiredSetRecord.class);
        List<AtomicSerialFieldDef> fields = schema.fields();
        assertEquals(1, fields.size());
        assertEquals("set:@AtomicSerial", fields.get(0).wireType(),
                "a plain Set<Foo> field must auto-wire to set:@AtomicSerial via the rule (memo §8)");
    }

    @Test
    void plainSetOfFoo_roundTrips() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AutoWiredSetRecord.class);
        Set<Foo> in = new HashSet<>();
        in.add(new Foo(1, "a"));
        in.add(new Foo(2, "b"));

        byte[] enc = ObjectCodec.encode(new AutoWiredSetRecord(in), AutoWiredSetRecord.class, schema);
        AutoWiredSetRecord out = ObjectCodec.decode(AutoWiredSetRecord.class, schema, enc);

        assertEquals(in, out.getTags(),
                "auto-wired set:@AtomicSerial round-trips the Set<Foo> value");
    }

    @Test
    void plainSetOfFoo_determinism_twoInsertionOrders_byteIdentical() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AutoWiredSetRecord.class);
        Set<Foo> a = new HashSet<>();
        a.add(new Foo(1, "a")); a.add(new Foo(2, "b")); a.add(new Foo(3, "c"));
        Set<Foo> b = new HashSet<>();
        b.add(new Foo(3, "c")); b.add(new Foo(1, "a")); b.add(new Foo(2, "b"));

        byte[] ea = ObjectCodec.encode(new AutoWiredSetRecord(a), AutoWiredSetRecord.class, schema);
        byte[] eb = ObjectCodec.encode(new AutoWiredSetRecord(b), AutoWiredSetRecord.class, schema);
        assertArrayEquals(ea, eb,
                "an auto-wired canonicalise set of @AtomicSerial elements is order-deterministic");
    }

    // -------------------------------------------------------------------------
    // A raw Set field auto-wires to the Any fallback (E10) and round-trips.
    // -------------------------------------------------------------------------

    @Test
    void rawSet_autoWiresToSetAny() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(RawSetRecord.class);
        assertEquals("set:any", schema.fields().get(0).wireType(),
                "a raw Set field must auto-wire to the Any fallback (memo §3.4 / E10)");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rawSet_roundTripsMixedClosedSubsetValues() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(RawSetRecord.class);
        Set in = new HashSet();
        in.add("s");
        in.add(42);
        in.add(new Foo(7, "z"));

        byte[] enc = ObjectCodec.encode(new RawSetRecord(in), RawSetRecord.class, schema);
        RawSetRecord out = ObjectCodec.decode(RawSetRecord.class, schema, enc);
        assertEquals(in, out.getItems(),
                "a raw Set of mixed closed-subset values round-trips via set:any");
    }
}
