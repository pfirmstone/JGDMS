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

import au.net.zeus.jgdms.der.getarg.DerFieldStore;
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.object.fixtures.OuterWithNested;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Inc2 idempotency / decode-once test for the DER {@code GetArg} path.
 *
 * <p>The memoizing {@code AtomicSerial.GetArg} base invokes
 * {@link DerGetArg#lookup} at most once per {@code (caller, field)}. For a nested
 * {@code @AtomicSerial} field this means the lazy DER decode runs exactly once and
 * every subsequent read returns the SAME decoded instance -- the "decode-once"
 * property the SOW calls out as desirable. It also guarantees a class's
 * {@code check(GetArg)} and {@code (GetArg)} constructor observe an identical
 * nested value, so a replayed or hostile {@code GetArg} cannot TOCTOU the nested
 * object.
 *
 * <p>These tests construct a {@link DerGetArg} over a single-class store, so the
 * base's {@code StackWalker} caller resolution uses the single-entry fallback
 * (the registered class need not be on the stack).
 */
class DerGetArgIdempotencyTest {

    private static DerGetArg outerArg(OuterWithNested outer) throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(OuterWithNested.class);
        byte[] payload = ObjectCodec.encode(outer, OuterWithNested.class, schema);
        DerFieldStore store = new DerFieldStore(schema, payload);
        Map<Class<?>, DerFieldStore> storeMap = new LinkedHashMap<>();
        storeMap.put(OuterWithNested.class, store);
        return new DerGetArg(storeMap);
    }

    /**
     * A nested {@code @AtomicSerial} field is decoded exactly once: two reads of
     * {@code "inner"} return the very same instance.
     */
    @Test
    void nestedFieldIsDecodedOnce_sameInstanceAcrossReads() throws Exception {
        NestedValue inner = new NestedValue(42, "decode-once");
        DerGetArg arg = outerArg(new OuterWithNested("tag", inner));

        Object first = arg.get("inner", null);
        Object second = arg.get("inner", null);

        assertNotNull(first, "nested field must decode to a value");
        assertEquals(inner, first, "decoded nested value must equal the original");
        assertSame(first, second,
                "decode-once: the memoizing base must return the SAME decoded instance, "
                + "not decode the nested record again");
    }

    /**
     * The typed 3-arg accessor and the Object accessor for the same nested field
     * share the single memoized (decode-once) instance.
     */
    @Test
    void nestedField_objectAndTypedAccessorsShareInstance() throws Exception {
        NestedValue inner = new NestedValue(7, "shared");
        DerGetArg arg = outerArg(new OuterWithNested("tag", inner));

        Object viaObject = arg.get("inner", null);
        NestedValue viaTyped = arg.get("inner", null, NestedValue.class);

        assertSame(viaObject, viaTyped,
                "the Object and 3-arg accessors must return the one memoized instance");
    }

    /**
     * {@code defaulted()} on a present nested field is decode-free (it must not
     * trigger the lazy decode) yet correctly reports the field present; value
     * reads afterwards remain decode-once.
     */
    @Test
    void defaultedOnNestedFieldIsDecodeFreeThenStillDecodeOnce() throws Exception {
        NestedValue inner = new NestedValue(1, "present");
        DerGetArg arg = outerArg(new OuterWithNested("tag", inner));

        assertFalse(arg.defaulted("inner"), "present nested field must report not-defaulted");

        Object a = arg.get("inner", null);
        Object b = arg.get("inner", null);
        assertSame(a, b, "value reads after defaulted() must still be decode-once");
    }
}
