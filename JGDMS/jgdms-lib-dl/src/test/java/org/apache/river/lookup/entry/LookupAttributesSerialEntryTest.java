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
package org.apache.river.lookup.entry;

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
 * Tests for {@link LookupAttributes} interoperability with
 * {@link SerialEntry @SerialEntry} classes.
 *
 * <p>Covers:
 * <ul>
 *   <li>RULE-CHECK: {@code check()} accepts @SerialEntry classes (public class +
 *       public {@code (GetEntryArg)} constructor).</li>
 *   <li>RULE-CHECK: {@code check()} rejects @SerialEntry classes missing the
 *       {@code (GetEntryArg)} constructor.</li>
 *   <li>RULE-EQUAL: {@code equal()} treats @SerialEntry final fields as
 *       participating fields.</li>
 *   <li>RULE-MATCHES: {@code matches()} respects @SerialEntry final fields
 *       in the template.</li>
 *   <li>RULE-ADD: {@code add()} deduplicates @SerialEntry entries correctly.</li>
 *   <li>RULE-MODIFY: {@code modify()} merges @SerialEntry entries via the
 *       serialize/constructor round-trip.</li>
 *   <li>RULE-LEGACY: Legacy (non-@SerialEntry) behaviour is unchanged.</li>
 * </ul>
 */
public class LookupAttributesSerialEntryTest {

    // ── Fixtures ───────────────────────────────────────────────────────────

    /** Minimal valid @SerialEntry fixture. */
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
            return true;
        }

        public static void serialize(PutEntryArg arg, LocationEntry e)
                throws IOException {
            arg.put("host",  e.host);
            arg.put("floor", e.floor);
            arg.writeArgs();
        }
    }

    /** @SerialEntry fixture with two fields, used for modify tests. */
    @SerialEntry
    public static class ServiceEntry extends AbstractEntry {
        public final String name;
        public final String description;

        public ServiceEntry() { name = null; description = null; }

        public ServiceEntry(String name, String description) {
            this.name        = name;
            this.description = description;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("name",        String.class),
                new EntryWireField("description", String.class),
            };
        }

        public ServiceEntry(GetEntryArg arg) throws IOException {
            this(arg.get("name",        null, String.class),
                 arg.get("description", null, String.class));
        }

        public static void serialize(PutEntryArg arg, ServiceEntry e)
                throws IOException {
            arg.put("name",        e.name);
            arg.put("description", e.description);
            arg.writeArgs();
        }
    }

    /** @SerialEntry class whose (GetEntryArg) constructor is missing — invalid. */
    @SerialEntry
    public static class BrokenSerialEntry extends AbstractEntry {
        public final String value;

        public BrokenSerialEntry() { value = null; }
        public BrokenSerialEntry(String value) { this.value = value; }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] { new EntryWireField("value", String.class) };
        }
        // Deliberately no (GetEntryArg) constructor
        public static void serialize(PutEntryArg arg, BrokenSerialEntry e)
                throws IOException {
            arg.put("value", e.value);
            arg.writeArgs();
        }
    }

    /** Legacy (non-@SerialEntry) Entry for backward-compatibility tests. */
    public static class PlainEntry extends AbstractEntry {
        public String host;

        public PlainEntry() {}
        public PlainEntry(String host) { this.host = host; }
    }

    // ── check() tests ──────────────────────────────────────────────────────

    @Test
    public void checkAcceptsValidSerialEntryClass() {
        // Must not throw
        LookupAttributes.check(new Entry[]{ new LocationEntry("h1", 1) }, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkRejectsSerialEntryMissingGetEntryArgConstructor() {
        LookupAttributes.check(new Entry[]{ new BrokenSerialEntry("x") }, false);
    }

    @Test
    public void checkAcceptsLegacyEntryWithNoArgConstructor() {
        // Must not throw
        LookupAttributes.check(new Entry[]{ new PlainEntry("h1") }, false);
    }

    @Test(expected = NullPointerException.class)
    public void checkThrowsOnNullEntryWhenNullOKFalse() {
        LookupAttributes.check(new Entry[]{ null }, false);
    }

    @Test
    public void checkAllowsNullEntryWhenNullOKTrue() {
        // Must not throw
        LookupAttributes.check(new Entry[]{ null }, true);
    }

    // ── equal() tests ──────────────────────────────────────────────────────

    @Test
    public void equalReturnsTrueForIdenticalSerialEntries() {
        LocationEntry a = new LocationEntry("server1", 3);
        LocationEntry b = new LocationEntry("server1", 3);
        assertTrue("identical @SerialEntry instances must be equal",
                   LookupAttributes.equal(a, b));
    }

    @Test
    public void equalReturnsFalseForDifferentHostSerialEntries() {
        LocationEntry a = new LocationEntry("server1", 3);
        LocationEntry b = new LocationEntry("server2", 3);
        assertFalse("@SerialEntry instances with different host must not be equal",
                    LookupAttributes.equal(a, b));
    }

    @Test
    public void equalReturnsFalseForDifferentFloorSerialEntries() {
        LocationEntry a = new LocationEntry("server1", 1);
        LocationEntry b = new LocationEntry("server1", 2);
        assertFalse("@SerialEntry instances with different floor must not be equal",
                    LookupAttributes.equal(a, b));
    }

    @Test
    public void equalHandlesNullFieldsCorrectly() {
        LocationEntry a = new LocationEntry("server1", null);
        LocationEntry b = new LocationEntry("server1", null);
        assertTrue("@SerialEntry entries with same null fields must be equal",
                   LookupAttributes.equal(a, b));
    }

    @Test
    public void equalNullFieldVsNonNullField() {
        LocationEntry a = new LocationEntry("server1", null);
        LocationEntry b = new LocationEntry("server1", 3);
        assertFalse("null floor vs non-null floor must not be equal",
                    LookupAttributes.equal(a, b));
    }

    @Test
    public void equalReturnsTrueForIdenticalLegacyEntries() {
        PlainEntry a = new PlainEntry("server1");
        PlainEntry b = new PlainEntry("server1");
        assertTrue(LookupAttributes.equal(a, b));
    }

    @Test
    public void equalArrayReturnsTrueForIdenticalSerialEntryArrays() {
        Entry[] a = { new LocationEntry("s1", 1), new LocationEntry("s2", 2) };
        Entry[] b = { new LocationEntry("s1", 1), new LocationEntry("s2", 2) };
        assertTrue(LookupAttributes.equal(a, b));
    }

    // ── matches() tests ────────────────────────────────────────────────────

    @Test
    public void matchesReturnsTrueWhenAllTemplateFieldsMatch() {
        LocationEntry tmpl = new LocationEntry("server1", 3);
        LocationEntry entry = new LocationEntry("server1", 3);
        assertTrue(LookupAttributes.matches(tmpl, entry));
    }

    @Test
    public void matchesReturnsTrueWhenNullTemplateFieldsAreWildcard() {
        // floor=null in template means "match any floor"
        LocationEntry tmpl  = new LocationEntry("server1", null);
        LocationEntry entry = new LocationEntry("server1", 99);
        assertTrue("null floor in template must match any floor",
                   LookupAttributes.matches(tmpl, entry));
    }

    @Test
    public void matchesReturnsFalseWhenTemplateFieldDiffers() {
        LocationEntry tmpl  = new LocationEntry("server1", 3);
        LocationEntry entry = new LocationEntry("server1", 4);
        assertFalse(LookupAttributes.matches(tmpl, entry));
    }

    @Test
    public void matchesAllNullTemplateMatchesAnyEntry() {
        LocationEntry tmpl  = new LocationEntry(null, null);
        LocationEntry entry = new LocationEntry("server1", 5);
        assertTrue("all-null template must match any entry",
                   LookupAttributes.matches(tmpl, entry));
    }

    // ── add() tests ────────────────────────────────────────────────────────

    @Test
    public void addAppendsNewSerialEntry() {
        Entry[] existing = { new LocationEntry("s1", 1) };
        Entry[] add      = { new LocationEntry("s2", 2) };
        Entry[] result   = LookupAttributes.add(existing, add);
        assertEquals(2, result.length);
    }

    @Test
    public void addDeduplicatesIdenticalSerialEntries() {
        Entry[] existing = { new LocationEntry("s1", 1) };
        Entry[] add      = { new LocationEntry("s1", 1) };  // duplicate
        Entry[] result   = LookupAttributes.add(existing, add);
        assertEquals("duplicate @SerialEntry must be suppressed", 1, result.length);
    }

    @Test
    public void addDoesNotDeduplicateDifferentSerialEntries() {
        Entry[] existing = { new LocationEntry("s1", 1) };
        Entry[] add      = { new LocationEntry("s1", 2) };  // different floor
        Entry[] result   = LookupAttributes.add(existing, add);
        assertEquals(2, result.length);
    }

    // ── modify() tests ─────────────────────────────────────────────────────

    @Test
    public void modifyUpdatesNonNullFieldInSerialEntry() {
        ServiceEntry original    = new ServiceEntry("MyService", "old description");
        ServiceEntry template    = new ServiceEntry("MyService", null);  // wildcard description
        ServiceEntry replacement = new ServiceEntry(null, "new description");  // update desc only

        Entry[] attrSets    = { original };
        Entry[] attrTmpls   = { template };
        Entry[] modAttrSets = { replacement };

        Entry[] result = LookupAttributes.modify(attrSets, attrTmpls, modAttrSets);
        assertEquals(1, result.length);
        assertTrue(result[0] instanceof ServiceEntry);
        ServiceEntry updated = (ServiceEntry) result[0];
        assertEquals("name must be preserved from original",         "MyService",       updated.name);
        assertEquals("description must be updated from replacement", "new description", updated.description);
    }

    @Test
    public void modifyDeletesMatchedEntryWhenReplacementIsNull() {
        ServiceEntry original  = new ServiceEntry("MyService", "desc");
        ServiceEntry template  = new ServiceEntry("MyService", null);

        Entry[] attrSets    = { original };
        Entry[] attrTmpls   = { template };
        Entry[] modAttrSets = { null };      // null = delete

        Entry[] result = LookupAttributes.modify(attrSets, attrTmpls, modAttrSets);
        assertEquals("matched entry must be deleted", 0, result.length);
    }

    @Test
    public void modifyLeavesUnmatchedEntryUnchanged() {
        ServiceEntry entry1      = new ServiceEntry("ServiceA", "desc1");
        ServiceEntry entry2      = new ServiceEntry("ServiceB", "desc2");
        ServiceEntry template    = new ServiceEntry("ServiceA", null);  // only matches entry1
        ServiceEntry replacement = new ServiceEntry(null, "updated");

        Entry[] attrSets    = { entry1, entry2 };
        Entry[] attrTmpls   = { template };
        Entry[] modAttrSets = { replacement };

        Entry[] result = LookupAttributes.modify(attrSets, attrTmpls, modAttrSets);
        assertEquals(2, result.length);
        // Find entry2 — it should be unchanged
        ServiceEntry unchanged = null;
        for (Entry e : result) {
            if (e instanceof ServiceEntry) {
                ServiceEntry se = (ServiceEntry) e;
                if ("ServiceB".equals(se.name)) { unchanged = se; break; }
            }
        }
        assertNotNull("ServiceB must still be present", unchanged);
        assertEquals("desc2", unchanged.description);
    }

    @Test
    public void modifyKeepsNullModFieldFromOriginal() {
        // If mods has null for a field, keep the original's value for that field.
        ServiceEntry original    = new ServiceEntry("MyService", "important desc");
        ServiceEntry template    = new ServiceEntry("MyService", null);
        ServiceEntry replacement = new ServiceEntry(null, null);  // both null = keep original

        Entry[] attrSets    = { original };
        Entry[] attrTmpls   = { template };
        Entry[] modAttrSets = { replacement };

        Entry[] result = LookupAttributes.modify(attrSets, attrTmpls, modAttrSets);
        assertEquals(1, result.length);
        ServiceEntry updated = (ServiceEntry) result[0];
        assertEquals("name preserved", "MyService", updated.name);
        assertEquals("description preserved", "important desc", updated.description);
    }

    // ── Backward-compatibility: legacy entries unchanged ──────────────────

    @Test
    public void checkLegacyEntryStillWorks() {
        // Legacy check must still work
        LookupAttributes.check(new Entry[]{ new PlainEntry("x") }, false);
    }

    @Test
    public void equalLegacyEntryStillWorks() {
        PlainEntry a = new PlainEntry("x");
        PlainEntry b = new PlainEntry("x");
        assertTrue(LookupAttributes.equal(a, b));
    }

    @Test
    public void matchesLegacyEntryStillWorks() {
        PlainEntry tmpl  = new PlainEntry(null);
        PlainEntry entry = new PlainEntry("x");
        assertTrue("null template field is wildcard", LookupAttributes.matches(tmpl, entry));
    }
}
