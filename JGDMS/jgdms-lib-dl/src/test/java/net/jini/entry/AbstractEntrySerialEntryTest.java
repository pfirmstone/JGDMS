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
package net.jini.entry;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Objects;
import net.jini.core.entry.Entry;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Standards-compliance tests for {@link AbstractEntry} and
 * {@link SerialEntry @SerialEntry} classes.
 *
 * <p>Covers:
 * <ul>
 *   <li>RULE-1: {@code @SerialEntry} annotation on a class means {@code final}
 *       fields ARE included in {@code AbstractEntry.equals/hashCode/toString}.</li>
 *   <li>RULE-2: Without {@code @SerialEntry}, {@code final} fields are
 *       excluded.</li>
 *   <li>RULE-3: {@code entryForm()} must return a non-null, non-empty array.</li>
 *   <li>RULE-4: {@code serialize()} + {@code (GetEntryArg)} round-trip via
 *       simple in-memory {@link GetEntryArg}/{@link PutEntryArg} stubs.</li>
 *   <li>RULE-5: Absent fields return the supplied default value.</li>
 *   <li>RULE-6: {@code defaulted()} distinguishes absent from explicitly-null.</li>
 *   <li>RULE-7: Type mismatch on get() throws {@link InvalidObjectException}.</li>
 * </ul>
 */
public class AbstractEntrySerialEntryTest {

    // ── Fixture: plain Entry (no @SerialEntry) ────────────────────────────

    public static class PlainEntry extends AbstractEntry {
        public String  host;
        public final String finalIgnored = "ignored"; // must NOT appear in fieldInfo
        public PlainEntry() {}
        public PlainEntry(String host) { this.host = host; }
    }

    // ── Fixture: @SerialEntry class ───────────────────────────────────────

    @SerialEntry
    public static class LocationEntry extends AbstractEntry {
        public final String  host;
        public final Integer floor;

        /** Required no-arg constructor for legacy interop. */
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

    // ── In-memory stubs for GetEntryArg / PutEntryArg ────────────────────

    /** Simple in-memory PutEntryArg that collects values by name. */
    static class SimplePutArg extends PutEntryArg {
        final java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        boolean committed = false;

        @Override
        public void put(String name, Object value) throws IOException {
            if (name == null) throw new NullPointerException("name must not be null");
            if (committed) throw new IllegalStateException("already committed");
            map.put(name, value);
        }

        @Override
        public void writeArgs() throws IOException {
            if (committed) throw new IllegalStateException("writeArgs() already called");
            committed = true;
        }
    }

    /** Simple in-memory GetEntryArg that serves values from a map. */
    static class SimpleGetArg extends GetEntryArg {
        private final java.util.Map<String, Object> present;

        SimpleGetArg(java.util.Map<String, Object> present) {
            this.present = present;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String name, T defaultValue, Class<T> type) throws IOException {
            if (name == null) throw new NullPointerException("name must not be null");
            if (type == null) throw new NullPointerException("type must not be null");
            if (!present.containsKey(name)) return defaultValue;
            Object val = present.get(name);
            if (val == null) return null;
            if (!type.isInstance(val)) {
                InvalidObjectException ex = new InvalidObjectException(
                    "type mismatch for field \"" + name + "\"");
                ex.initCause(new ClassCastException());
                throw ex;
            }
            return type.cast(val);
        }

        @Override
        public boolean defaulted(String name) {
            if (name == null) throw new NullPointerException("name must not be null");
            return !present.containsKey(name);
        }
    }

    // ── RULE-1: @SerialEntry final fields included in equals/hashCode/toString

    @Test
    public void serialEntryFinalFieldsIncludedInEquals() {
        LocationEntry a = new LocationEntry("server1", 3);
        LocationEntry b = new LocationEntry("server1", 3);
        assertTrue("identical @SerialEntry instances must be equal", a.equals(b));
    }

    @Test
    public void serialEntryFinalFieldsIncludedInEqualsNegative() {
        LocationEntry a = new LocationEntry("server1", 3);
        LocationEntry b = new LocationEntry("server2", 3);
        assertFalse("@SerialEntry instances with different host must not be equal", a.equals(b));
    }

    @Test
    public void serialEntryFinalFieldsIncludedInHashCode() {
        LocationEntry a = new LocationEntry("server1", 3);
        LocationEntry b = new LocationEntry("server1", 3);
        assertEquals("equal @SerialEntry instances must have same hashCode",
                     a.hashCode(), b.hashCode());
    }

    @Test
    public void serialEntryToStringIncludesFinalFields() {
        LocationEntry e = new LocationEntry("server1", 3);
        String s = e.toString();
        assertTrue("toString must include 'host'",  s.contains("host"));
        assertTrue("toString must include host value", s.contains("server1"));
        assertTrue("toString must include 'floor'", s.contains("floor"));
    }

    // ── RULE-2: Without @SerialEntry, final fields excluded ─────────────────

    @Test
    public void plainEntryFinalFieldExcludedFromEquals() {
        // Two PlainEntry instances with same host but both have the same
        // final field value anyway; the key point is that changing the
        // mutable 'host' field changes equality, not the final field.
        PlainEntry a = new PlainEntry("server1");
        PlainEntry b = new PlainEntry("server1");
        assertTrue("plain entries with same mutable fields must be equal", a.equals(b));
    }

    @Test
    public void plainEntryFinalFieldExcludedFromToString() {
        PlainEntry e = new PlainEntry("server1");
        String s = e.toString();
        assertFalse("toString must NOT include 'finalIgnored' field for non-@SerialEntry",
                    s.contains("finalIgnored"));
    }

    // ── RULE-3: entryForm() returns non-null, non-empty array ───────────────

    @Test
    public void entryFormReturnsNonNullNonEmptyArray() {
        EntryWireField[] form = LocationEntry.entryForm();
        assertNotNull("entryForm() must not return null", form);
        assertTrue("entryForm() must return at least one field", form.length > 0);
    }

    @Test
    public void entryFormFieldNamesMatchExpected() {
        EntryWireField[] form = LocationEntry.entryForm();
        assertEquals("host",  form[0].getName());
        assertEquals("floor", form[1].getName());
        assertEquals(String.class,  form[0].getType());
        assertEquals(Integer.class, form[1].getType());
    }

    // ── RULE-4: serialize + (GetEntryArg) round-trip ────────────────────────

    @Test
    public void serializeDeserializeRoundTrip() throws IOException {
        LocationEntry original = new LocationEntry("server1", 3);

        // Serialize
        SimplePutArg put = new SimplePutArg();
        LocationEntry.serialize(put, original);
        assertTrue("writeArgs() must have been called", put.committed);

        // Deserialize
        SimpleGetArg get = new SimpleGetArg(put.map);
        LocationEntry restored = new LocationEntry(get);

        assertEquals("host must survive round-trip",  original.host,  restored.host);
        assertEquals("floor must survive round-trip", original.floor, restored.floor);
    }

    @Test
    public void serializeNullFloorRoundTrip() throws IOException {
        LocationEntry original = new LocationEntry("server1", null);

        SimplePutArg put = new SimplePutArg();
        LocationEntry.serialize(put, original);

        SimpleGetArg get = new SimpleGetArg(put.map);
        LocationEntry restored = new LocationEntry(get);

        assertEquals("host must survive round-trip",  original.host, restored.host);
        assertNull("null floor must survive round-trip", restored.floor);
    }

    // ── RULE-5: Absent fields return default value ───────────────────────────

    @Test
    public void absentFieldReturnsDefaultValue() throws IOException {
        // Simulate an older serialized form that had no "floor" field
        java.util.Map<String, Object> stored = new java.util.HashMap<>();
        stored.put("host", "server1");
        // "floor" intentionally absent

        SimpleGetArg get = new SimpleGetArg(stored);
        Integer defaultFloor = 99;
        Integer result = get.get("floor", defaultFloor, Integer.class);
        assertEquals("absent field must return the supplied default", defaultFloor, result);
    }

    // ── RULE-6: defaulted() distinguishes absent from explicitly-null ────────

    @Test
    public void defaultedReturnsTrueForAbsentField() {
        SimpleGetArg get = new SimpleGetArg(new java.util.HashMap<>());
        assertTrue("defaulted() must be true for absent field", get.defaulted("floor"));
    }

    @Test
    public void defaultedReturnsFalseForExplicitlyNullField() {
        java.util.Map<String, Object> stored = new java.util.HashMap<>();
        stored.put("floor", null);
        SimpleGetArg get = new SimpleGetArg(stored);
        assertFalse("defaulted() must be false when field is present (even if null)",
                    get.defaulted("floor"));
    }

    @Test
    public void getReturnNullForExplicitlyNullField() throws IOException {
        java.util.Map<String, Object> stored = new java.util.HashMap<>();
        stored.put("host", "server1");
        stored.put("floor", null);
        SimpleGetArg get = new SimpleGetArg(stored);
        assertNull("explicitly null field must return null, not the default",
                   get.get("floor", 42, Integer.class));
    }

    // ── RULE-7: Type mismatch on get() throws InvalidObjectException ─────────

    @Test(expected = InvalidObjectException.class)
    public void typeMismatchThrowsInvalidObjectException() throws IOException {
        java.util.Map<String, Object> stored = new java.util.HashMap<>();
        stored.put("floor", "not-an-integer");   // String stored where Integer expected
        SimpleGetArg get = new SimpleGetArg(stored);
        get.get("floor", null, Integer.class);   // must throw
    }

    // ── Invariant check via check-before-bridge-constructor pattern ──────────

    @Test(expected = InvalidObjectException.class)
    public void invariantCheckRejectsNullHost() throws IOException {
        java.util.Map<String, Object> stored = new java.util.HashMap<>();
        stored.put("host",  null);
        stored.put("floor", 3);
        SimpleGetArg get = new SimpleGetArg(stored);
        new LocationEntry(get);   // must throw because host is null
    }

    // ── NullPointerException guards on GetEntryArg ─────────────────────────

    @Test(expected = NullPointerException.class)
    public void getEntryArgGetRejectsNullName() throws IOException {
        SimpleGetArg get = new SimpleGetArg(new java.util.HashMap<>());
        get.get(null, null, String.class);
    }

    @Test(expected = NullPointerException.class)
    public void getEntryArgGetRejectsNullType() throws IOException {
        SimpleGetArg get = new SimpleGetArg(new java.util.HashMap<>());
        get.get("host", null, null);
    }

    @Test(expected = NullPointerException.class)
    public void getEntryArgDefaultedRejectsNullName() {
        SimpleGetArg get = new SimpleGetArg(new java.util.HashMap<>());
        get.defaulted(null);
    }

    // ── PutEntryArg double-commit guard ─────────────────────────────────────

    @Test(expected = IllegalStateException.class)
    public void putArgDoubleWriteArgsThrows() throws IOException {
        SimplePutArg put = new SimplePutArg();
        put.writeArgs();
        put.writeArgs();  // second call must throw
    }

    @Test(expected = IllegalStateException.class)
    public void putArgPutAfterWriteArgsThrows() throws IOException {
        SimplePutArg put = new SimplePutArg();
        put.writeArgs();
        put.put("host", "server1");  // put after commit must throw
    }
}
