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
package net.jini.core.entry;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link EntryWireField}.
 */
public class EntryWireFieldTest {

    // ── Constructor validation ──────────────────────────────────────────────

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNullName() {
        new EntryWireField(null, String.class);
    }

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNullType() {
        new EntryWireField("host", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsEmptyName() {
        new EntryWireField("", String.class);
    }

    @Test
    public void constructorAcceptsValidArgs() {
        EntryWireField f = new EntryWireField("host", String.class);
        assertEquals("host", f.getName());
        assertEquals(String.class, f.getType());
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    @Test
    public void getNameReturnsSuppliedName() {
        EntryWireField f = new EntryWireField("floor", Integer.class);
        assertEquals("floor", f.getName());
    }

    @Test
    public void getTypeReturnsSuppliedType() {
        EntryWireField f = new EntryWireField("room", String.class);
        assertSame(String.class, f.getType());
    }

    // ── equals / hashCode ───────────────────────────────────────────────────

    @Test
    public void equalsSameInstance() {
        EntryWireField f = new EntryWireField("host", String.class);
        assertEquals(f, f);
    }

    @Test
    public void equalsEqualInstances() {
        EntryWireField a = new EntryWireField("host", String.class);
        EntryWireField b = new EntryWireField("host", String.class);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void notEqualDifferentName() {
        EntryWireField a = new EntryWireField("host",  String.class);
        EntryWireField b = new EntryWireField("other", String.class);
        assertNotEquals(a, b);
    }

    @Test
    public void notEqualDifferentType() {
        EntryWireField a = new EntryWireField("floor", Integer.class);
        EntryWireField b = new EntryWireField("floor", Long.class);
        assertNotEquals(a, b);
    }

    @Test
    public void notEqualNull() {
        EntryWireField f = new EntryWireField("host", String.class);
        assertNotEquals(f, null);
    }

    @Test
    public void notEqualOtherType() {
        EntryWireField f = new EntryWireField("host", String.class);
        assertNotEquals(f, "host");
    }

    @Test
    public void hashCodeConsistentWithEquals() {
        EntryWireField a = new EntryWireField("host", String.class);
        EntryWireField b = new EntryWireField("host", String.class);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCodeVariesWithName() {
        EntryWireField a = new EntryWireField("host",  String.class);
        EntryWireField b = new EntryWireField("other", String.class);
        // Not strictly guaranteed but highly likely for these inputs
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCodeVariesWithType() {
        EntryWireField a = new EntryWireField("floor", Integer.class);
        EntryWireField b = new EntryWireField("floor", Long.class);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    public void toStringContainsNameAndType() {
        EntryWireField f = new EntryWireField("host", String.class);
        String s = f.toString();
        assertTrue("toString should contain field name",  s.contains("host"));
        assertTrue("toString should contain type name",   s.contains("java.lang.String"));
    }
}
