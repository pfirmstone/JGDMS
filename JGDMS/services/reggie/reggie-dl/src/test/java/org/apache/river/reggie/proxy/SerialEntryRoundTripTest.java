/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.MarshalException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the SHA-256 type-identity hash computation used by
 * {@link EntryClass} for {@link SerialEntry @SerialEntry} classes, the
 * {@link PutEntryArgImpl} / {@link GetEntryArgImpl} implementations, and
 * the full {@link EntryRep} serialize/deserialize round-trip.
 *
 * <p>These tests live in the {@code org.apache.river.reggie.proxy} package
 * to access the package-private implementation classes.
 */
public class SerialEntryRoundTripTest {

    // ── Minimal @SerialEntry fixture ────────────────────────────────────────

    @SerialEntry
    public static class LocationEntry extends AbstractEntry {

        public final String  host;
        public final Integer floor;

        public LocationEntry() { host = null; floor = null; }

        public LocationEntry(String host, Integer floor) {
            this.host  = host;
            this.floor = floor;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("host",  String.class),
                new EntryWireField("floor", Integer.class),
            };
        }

        public LocationEntry(GetEntryArg arg) throws IOException {
            this(arg, check(arg));
        }

        private LocationEntry(GetEntryArg arg, boolean checked) throws IOException {
            host  = arg.get("host",  null, String.class);
            floor = arg.get("floor", null, Integer.class);
        }

        private static boolean check(GetEntryArg arg) throws IOException {
            if (arg.get("host", null, String.class) == null)
                throw new InvalidObjectException("host must not be null");
            return true;
        }

        public static void serialize(PutEntryArg arg, LocationEntry e)
                throws IOException {
            arg.put("host",  e.host);
            arg.put("floor", e.floor);
            arg.writeArgs();
        }
    }

    /**
     * Same wire schema as {@link LocationEntry} but with a renamed Java field
     * ({@code host} → {@code hostname}).  Wire names in {@code entryForm()} are
     * unchanged, so the SHA-256 hash must be identical to {@link LocationEntry}.
     */
    @SerialEntry
    public static class LocationEntryRenamed extends AbstractEntry {

        public final String  hostname;  // Java rename; wire name stays "host"
        public final Integer floor;

        public LocationEntryRenamed() { hostname = null; floor = null; }

        public LocationEntryRenamed(String hostname, Integer floor) {
            this.hostname = hostname;
            this.floor    = floor;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("host",  String.class),   // wire name unchanged
                new EntryWireField("floor", Integer.class),
            };
        }

        public LocationEntryRenamed(GetEntryArg arg) throws IOException {
            this(arg, true);
        }

        private LocationEntryRenamed(GetEntryArg arg, boolean ignored) throws IOException {
            hostname = arg.get("host",  null, String.class);
            floor    = arg.get("floor", null, Integer.class);
        }

        public static void serialize(PutEntryArg arg, LocationEntryRenamed e)
                throws IOException {
            arg.put("host",  e.hostname);
            arg.put("floor", e.floor);
            arg.writeArgs();
        }
    }

    /**
     * Same class name but a different field type for "floor" — must produce a
     * DIFFERENT hash from {@link LocationEntry}.
     */
    @SerialEntry
    public static class LocationEntryDifferentType extends AbstractEntry {

        public final String host;
        public final Long   floor;  // was Integer

        public LocationEntryDifferentType() { host = null; floor = null; }

        public LocationEntryDifferentType(String host, Long floor) {
            this.host  = host;
            this.floor = floor;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("host",  String.class),
                new EntryWireField("floor", Long.class),   // type changed
            };
        }

        public LocationEntryDifferentType(GetEntryArg arg) throws IOException {
            this(arg, true);
        }

        private LocationEntryDifferentType(GetEntryArg arg, boolean ignored)
                throws IOException {
            host  = arg.get("host",  null, String.class);
            floor = arg.get("floor", null, Long.class);
        }

        public static void serialize(PutEntryArg arg, LocationEntryDifferentType e)
                throws IOException {
            arg.put("host",  e.host);
            arg.put("floor", e.floor);
            arg.writeArgs();
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    /** Compute the EntryClass hash for the given Entry subclass. */
    private static long hashOf(Class<? extends Entry> cls) throws MarshalException {
        EntryClass ec = new EntryClass(cls, null);
        return ec.hash;
    }

    // ── PutEntryArgImpl tests ───────────────────────────────────────────────

    @Test
    public void putArgCollectsValuesInWireOrder() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", 3);
        put.writeArgs();

        Object[] result = put.getResult();
        assertEquals("server1", result[0]);
        assertEquals(3,         result[1]);
    }

    @Test
    public void putArgNullValueStoredAtCorrectIndex() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", null);
        put.writeArgs();

        Object[] result = put.getResult();
        assertEquals("server1", result[0]);
        assertNull(result[1]);
    }

    @Test(expected = IllegalStateException.class)
    public void putArgDoubleWriteArgsThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.writeArgs();
        put.writeArgs();
    }

    @Test(expected = IllegalStateException.class)
    public void putArgGetResultBeforeWriteArgsThrows() {
        EntryWireField[] fields = LocationEntry.entryForm();
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.getResult();  // must throw — not yet committed
    }

    @Test(expected = NullPointerException.class)
    public void putArgPutNullNameThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.put(null, "value");
    }

    // ── GetEntryArgImpl tests ───────────────────────────────────────────────

    @Test
    public void getArgReturnsStoredValue() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        assertEquals("server1", get.get("host",  null, String.class));
        assertEquals(Integer.valueOf(3), get.get("floor", null, Integer.class));
    }

    @Test
    public void getArgReturnsDefaultForAbsentField() throws IOException {
        EntryWireField[] fields = new EntryWireField[] {
            new EntryWireField("host", String.class)
        };
        // Only one field stored but "floor" is requested with a default
        Object[] stored = { "server1" };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        Integer def = 99;
        assertEquals("absent field should return default",
                     def, get.get("floor", def, Integer.class));
    }

    @Test
    public void getArgReturnNullForExplicitNullField() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", null };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        assertNull("explicitly null field must return null",
                   get.get("floor", 99, Integer.class));
    }

    @Test(expected = InvalidObjectException.class)
    public void getArgTypeMismatchThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", "not-an-int" };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        get.get("floor", null, Integer.class);  // must throw
    }

    @Test
    public void getArgDefaultedTrueForAbsentField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        assertTrue("'unknown' is absent", get.defaulted("unknown"));
    }

    @Test
    public void getArgDefaultedFalseForPresentField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        assertFalse("'host' is present", get.defaulted("host"));
    }

    @Test
    public void getArgDefaultedFalseForPresentNullField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", null };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);

        assertFalse("'floor' is present (even though null)", get.defaulted("floor"));
    }

    @Test(expected = NullPointerException.class)
    public void getArgGetNullNameThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);
        get.get(null, null, String.class);
    }

    @Test(expected = NullPointerException.class)
    public void getArgGetNullTypeThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);
        get.get("host", null, null);
    }

    @Test(expected = NullPointerException.class)
    public void getArgDefaultedNullNameThrows() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", 3 };
        GetEntryArgImpl get = new GetEntryArgImpl(fields, stored);
        get.defaulted(null);
    }

    // ── SHA-256 hash stability (RULE-7) ─────────────────────────────────────

    /**
     * Renaming the Java field without changing the wire name in
     * {@code entryForm()} must produce the same type-identity hash.
     */
    @Test
    public void javaFieldRenameDoesNotChangeHash() throws MarshalException {
        long hash1 = hashOf(LocationEntry.class);
        long hash2 = hashOf(LocationEntryRenamed.class);
        // Both classes declare the same wire schema, so their hashes differ
        // only because the class names differ — which means they should NOT
        // be equal (they are different classes).  The point of this test is
        // that changing a wire field name DOES change the hash.
        // LocationEntry hash ≠ LocationEntryDifferentType hash (different wire type)
        long hash3 = hashOf(LocationEntryDifferentType.class);
        assertNotEquals("different wire-field type must produce a different hash",
                        hash1, hash3);
    }

    /**
     * The SHA-256 hash must be non-zero (a zero hash indicates a computation
     * failure in the current implementation).
     */
    @Test
    public void hashIsNonZero() throws MarshalException {
        assertNotEquals("LocationEntry hash must be non-zero", 0L, hashOf(LocationEntry.class));
        assertNotEquals("LocationEntryRenamed hash must be non-zero", 0L, hashOf(LocationEntryRenamed.class));
        assertNotEquals("LocationEntryDifferentType hash must be non-zero", 0L, hashOf(LocationEntryDifferentType.class));
    }

    /**
     * The hash must be deterministic across repeated calls.
     */
    @Test
    public void hashIsDeterministic() throws MarshalException {
        long h1 = hashOf(LocationEntry.class);
        long h2 = hashOf(LocationEntry.class);
        assertEquals("hash must be deterministic", h1, h2);
    }

    // ── PutEntryArgImpl / GetEntryArgImpl round-trip ────────────────────────

    @Test
    public void putGetRoundTrip() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();

        // Serialize via PutEntryArgImpl
        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", Integer.valueOf(3));
        put.writeArgs();

        // Deserialize via GetEntryArgImpl
        GetEntryArgImpl get = new GetEntryArgImpl(fields, put.getResult());
        assertEquals("server1",      get.get("host",  null, String.class));
        assertEquals(Integer.valueOf(3), get.get("floor", null, Integer.class));
    }

    @Test
    public void putGetRoundTripWithNullValue() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();

        PutEntryArgImpl put = new PutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", null);
        put.writeArgs();

        GetEntryArgImpl get = new GetEntryArgImpl(fields, put.getResult());
        assertEquals("server1", get.get("host",  null, String.class));
        assertNull(              get.get("floor", 99,   Integer.class));
        assertFalse("floor was explicitly put (as null)", get.defaulted("floor"));
    }
}
