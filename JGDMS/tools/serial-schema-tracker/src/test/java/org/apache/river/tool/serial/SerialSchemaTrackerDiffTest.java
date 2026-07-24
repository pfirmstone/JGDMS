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
package org.apache.river.tool.serial;

import java.util.List;

import org.apache.river.tool.serial.SerialSchemaTracker.ChangeKind;
import org.apache.river.tool.serial.SerialSchemaTracker.FieldChange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Severity classification of {@code @AtomicSerial} serialForm() drift:
 * ADD is informative (defaulted GetArg.get), RETYPE and REMOVE gate the
 * build (they break reads of streams written by earlier versions).
 */
public class SerialSchemaTrackerDiffTest {

    private static List<FieldChange> diff(String oldF, String newF) {
        return SerialSchemaTracker.diffFields(oldF, newF);
    }

    // ------------------------------------------------------------------- ADD

    @Test
    public void addIsInformative() {
        List<FieldChange> c = diff(
                "source:java.lang.Object, eventID:long",
                "source:java.lang.Object, eventID:long, seqNum:long");
        assertEquals(1, c.size());
        assertEquals(ChangeKind.ADD, c.get(0).kind);
        assertEquals("seqNum", c.get(0).name);
        assertEquals("long", c.get(0).newType);
        assertNull(c.get(0).oldType);
        assertFalse("field addition must not gate", c.get(0).breaking());
    }

    // ---------------------------------------------------------------- RETYPE

    @Test
    public void retypeGates() {
        // The DerThrowableForm case: retyping an existing serial field breaks
        // typed GetArg.get reads of streams written by earlier versions.
        List<FieldChange> c = diff(
                "cause:java.lang.Throwable, message:java.lang.String",
                "cause:org.apache.river.api.io.DerThrowableForm, message:java.lang.String");
        assertEquals(1, c.size());
        FieldChange fc = c.get(0);
        assertEquals(ChangeKind.RETYPE, fc.kind);
        assertEquals("cause", fc.name);
        assertEquals("java.lang.Throwable", fc.oldType);
        assertEquals("org.apache.river.api.io.DerThrowableForm", fc.newType);
        assertTrue("retype must gate", fc.breaking());
    }

    // ---------------------------------------------------------------- REMOVE

    @Test
    public void removeGates() {
        List<FieldChange> c = diff(
                "source:java.lang.Object, handback:java.rmi.MarshalledObject",
                "source:java.lang.Object");
        assertEquals(1, c.size());
        FieldChange fc = c.get(0);
        assertEquals(ChangeKind.REMOVE, fc.kind);
        assertEquals("handback", fc.name);
        assertEquals("java.rmi.MarshalledObject", fc.oldType);
        assertNull(fc.newType);
        assertTrue("field removal must gate", fc.breaking());
    }

    // ------------------------------------------------------------ edge cases

    @Test
    public void renameClassifiesAsRemovePlusAddAndGates() {
        // e.g. reggie's constriants -> constraints typo fix
        List<FieldChange> c = diff(
                "constriants:net.jini.core.constraint.MethodConstraints",
                "constraints:net.jini.core.constraint.MethodConstraints");
        assertEquals(2, c.size());
        boolean breaking = false;
        int removes = 0, adds = 0;
        for (FieldChange fc : c) {
            if (fc.breaking()) breaking = true;
            if (fc.kind == ChangeKind.REMOVE) removes++;
            if (fc.kind == ChangeKind.ADD) adds++;
        }
        assertEquals(1, removes);
        assertEquals(1, adds);
        assertTrue("a rename must gate (REMOVE side)", breaking);
    }

    @Test
    public void reorderIsInformative() {
        List<FieldChange> c = diff(
                "a:int, b:long",
                "b:long, a:int");
        assertEquals(1, c.size());
        assertEquals(ChangeKind.REORDER, c.get(0).kind);
        assertFalse("reorder must not gate (fields are read by name)", c.get(0).breaking());
    }

    @Test
    public void mixedChangeReportsEachFieldOnce() {
        List<FieldChange> c = diff(
                "keep:int, retyped:java.lang.Object, dropped:short",
                "keep:int, retyped:java.lang.String, added:boolean");
        assertEquals(3, c.size());
        int add = 0, retype = 0, remove = 0;
        for (FieldChange fc : c) {
            switch (fc.kind) {
                case ADD:    add++;    break;
                case RETYPE: retype++; break;
                case REMOVE: remove++; break;
                default: break;
            }
        }
        assertEquals(1, add);
        assertEquals(1, retype);
        assertEquals(1, remove);
    }

    @Test
    public void whitespaceOnlyDifferenceIsNoChange() {
        assertTrue(diff("a:int,b:long", "a:int, b:long").isEmpty());
    }

    @Test
    public void identicalIsNoChange() {
        assertTrue(diff("a:int, b:long", "a:int, b:long").isEmpty());
    }
}
