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
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import net.jini.core.entry.Entry;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link SerialEntry @SerialEntry} integration in the
 * Outrigger (JavaSpace) proxy layer: {@link OutriggerPutEntryArgImpl},
 * {@link OutriggerGetEntryArgImpl}, and the full
 * {@link EntryRep} marshal/unmarshal round-trip.
 *
 * <p>These tests live in the {@code org.apache.river.outrigger.proxy} package
 * to access the package-private implementation classes.
 */
public class OutriggerSerialEntryRoundTripTest {

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
     * Same wire schema as {@link LocationEntry} but with a renamed Java field.
     * The SHA-256 hash must differ only because the class name differs.
     */
    @SerialEntry
    public static class LocationEntryRenamed extends AbstractEntry {

        public final String  hostname;   // Java rename; wire name stays "host"
        public final Integer floor;

        public LocationEntryRenamed() { hostname = null; floor = null; }

        public LocationEntryRenamed(String hostname, Integer floor) {
            this.hostname = hostname;
            this.floor    = floor;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("host",  String.class),  // wire name unchanged
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

    // ── OutriggerPutEntryArgImpl tests ─────────────────────────────────────

    @Test
    public void putArgCollectsValuesInWireOrder() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", Integer.valueOf(3));
        put.writeArgs();

        Object[] result = put.getResult();
        assertEquals("server1",           result[0]);
        assertEquals(Integer.valueOf(3),  result[1]);
    }

    @Test
    public void putArgNullValueStoredAtCorrectIndex() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
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
        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.writeArgs();
        put.writeArgs();
    }

    @Test(expected = IllegalStateException.class)
    public void putArgGetResultBeforeWriteArgsThrows() {
        EntryWireField[] fields = LocationEntry.entryForm();
        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.getResult();
    }

    @Test(expected = NullPointerException.class)
    public void putArgNullNameThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.put(null, "value");
    }

    // ── OutriggerGetEntryArgImpl tests ─────────────────────────────────────

    @Test
    public void getArgReturnsStoredValue() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);

        assertEquals("server1",         get.get("host",  null, String.class));
        assertEquals(Integer.valueOf(3), get.get("floor", null, Integer.class));
    }

    @Test
    public void getArgReturnsDefaultForAbsentField() throws IOException {
        EntryWireField[] fields = new EntryWireField[] {
            new EntryWireField("host", String.class)
        };
        Object[] stored = { "server1" };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);

        Integer def = 99;
        assertEquals("absent field must return default", def,
                     get.get("floor", def, Integer.class));
    }

    @Test
    public void getArgNullValueReturnsNull() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", null };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);

        assertNull("explicitly null field must return null",
                   get.get("floor", 99, Integer.class));
    }

    @Test(expected = InvalidObjectException.class)
    public void getArgTypeMismatchThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", "not-an-integer" };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        get.get("floor", null, Integer.class);
    }

    @Test
    public void getArgDefaultedTrueForAbsentField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        assertTrue("'unknown' is absent", get.defaulted("unknown"));
    }

    @Test
    public void getArgDefaultedFalseForPresentField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        assertFalse("'host' is present", get.defaulted("host"));
    }

    @Test
    public void getArgDefaultedFalseForPresentNullField() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", null };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        assertFalse("'floor' is present (null)", get.defaulted("floor"));
    }

    @Test(expected = NullPointerException.class)
    public void getArgGetNullNameThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        get.get(null, null, String.class);
    }

    @Test(expected = NullPointerException.class)
    public void getArgGetNullTypeThrows() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        get.get("host", null, null);
    }

    @Test(expected = NullPointerException.class)
    public void getArgDefaultedNullNameThrows() {
        EntryWireField[] fields = LocationEntry.entryForm();
        Object[] stored = { "server1", Integer.valueOf(3) };
        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, stored);
        get.defaulted(null);
    }

    // ── OutriggerPutEntryArgImpl/GetEntryArgImpl round-trip ─────────────────

    @Test
    public void putGetRoundTrip() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();

        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", Integer.valueOf(3));
        put.writeArgs();

        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, put.getResult());
        assertEquals("server1",           get.get("host",  null, String.class));
        assertEquals(Integer.valueOf(3),  get.get("floor", null, Integer.class));
    }

    @Test
    public void putGetRoundTripNullFloor() throws IOException {
        EntryWireField[] fields = LocationEntry.entryForm();

        OutriggerPutEntryArgImpl put = new OutriggerPutEntryArgImpl(fields);
        put.put("host",  "server1");
        put.put("floor", null);
        put.writeArgs();

        OutriggerGetEntryArgImpl get = new OutriggerGetEntryArgImpl(fields, put.getResult());
        assertEquals("server1", get.get("host",  null, String.class));
        assertNull(             get.get("floor", 99,   Integer.class));
        assertFalse("floor was explicitly put (as null)", get.defaulted("floor"));
    }
}

