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
package net.jini.lookup.entry;

import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Part A1 of {@code SOW-Entry-ATOMIC-DER-Migration.md}: {@link StatusType} (the type of
 * {@link Status#severity}) is retrofitted {@code @AtomicSerial} so a {@code Status} entry
 * is encodable under an {@code ATOMIC_DER} space, closing the {@code SchemaGenerator
 * .toWireType} rejection a plain non-{@code @AtomicSerial} {@code Serializable} field
 * hit before this fix.
 *
 * <p>Asserts that both a bare {@link StatusType} and a concrete {@link Status} entry
 * survive round-trips through {@link MarshalledInstance} under both wire formats --
 * {@link MarshallingFormat#JOSS} (backward compatibility: this must keep working
 * unchanged) and {@link MarshallingFormat#ATOMIC_DER} (the new capability) -- and that
 * canonical-instance ({@code ==}) identity and the underlying {@code type} value survive
 * every round-trip.
 */
public class StatusTypeDerEncodabilityTest {

    /** Minimal concrete {@link Status} entry fixture (Status itself is abstract). */
    public static class TestStatus extends Status {
        public TestStatus() {
            super();
        }

        public TestStatus(StatusType severity) {
            super(severity);
        }
    }

    private static InvocationConstraints joss() {
        return new InvocationConstraints(MarshallingFormat.JOSS, null);
    }

    private static InvocationConstraints atomicDer() {
        return new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null);
    }

    // ── Bare StatusType round-trips ──────────────────────────────────────────

    @Test
    public void statusTypeRoundTripsUnderJoss() throws Exception {
        for (StatusType original : new StatusType[]{
                StatusType.ERROR, StatusType.WARNING, StatusType.NOTICE, StatusType.NORMAL}) {
            MarshalledInstance mi = new MarshalledInstance(
                    original, Collections.emptySet(), joss());
            StatusType copy = mi.get(false, StatusType.class);
            assertSame("JOSS round-trip must preserve canonical-instance identity",
                    original, copy);
        }
    }

    @Test
    public void statusTypeRoundTripsUnderAtomicDer() throws Exception {
        for (StatusType original : new StatusType[]{
                StatusType.ERROR, StatusType.WARNING, StatusType.NOTICE, StatusType.NORMAL}) {
            MarshalledInstance mi = new MarshalledInstance(
                    original, Collections.emptySet(), atomicDer());
            StatusType copy = mi.get(false, StatusType.class);
            assertSame("ATOMIC_DER round-trip must preserve canonical-instance identity",
                    original, copy);
        }
    }

    // ── Status entry round-trips (the shipped, real-world path) ─────────────
    //
    // Outrigger/Reggie's EntryRep marshals a stored/templated Entry field-by-field --
    // each field value wrapped in its OWN MarshalledInstance (SOW-Entry-ATOMIC-DER
    // -Migration.md sec.2.2), not the whole Entry object in a single MarshalledInstance
    // (a plain, non-@AtomicSerial Entry class such as this fixture's TestStatus is not
    // itself a DER wire type, and making arbitrary Entry classes DER-encodable as whole
    // objects is out of A1's scope). These tests therefore round-trip the Status entry's
    // severity field exactly the way EntryRep does, proving StatusType -- the field's
    // declared type -- is now DER-encodable.

    @Test
    public void statusEntryRoundTripsUnderJoss() throws Exception {
        TestStatus original = new TestStatus(StatusType.WARNING);
        MarshalledInstance severityMi = new MarshalledInstance(
                original.severity, Collections.emptySet(), joss());
        TestStatus copy = new TestStatus();
        copy.severity = severityMi.get(false, StatusType.class);
        assertSame("JOSS field round-trip must preserve severity canonical-instance identity",
                StatusType.WARNING, copy.severity);
    }

    @Test
    public void statusEntryRoundTripsUnderAtomicDer() throws Exception {
        TestStatus original = new TestStatus(StatusType.NOTICE);
        MarshalledInstance severityMi = new MarshalledInstance(
                original.severity, Collections.emptySet(), atomicDer());
        TestStatus copy = new TestStatus();
        copy.severity = severityMi.get(false, StatusType.class);
        assertSame("ATOMIC_DER field round-trip must preserve severity canonical-instance identity",
                StatusType.NOTICE, copy.severity);
    }

    // ── The underlying int type value survives, independent of identity ─────

    @Test
    public void underlyingTypeValueSurvivesAtomicDerRoundTrip() throws Exception {
        MarshalledInstance mi = new MarshalledInstance(
                StatusType.ERROR, Collections.emptySet(), atomicDer());
        StatusType copy = mi.get(false, StatusType.class);
        assertEquals("StatusType.ERROR", copy.toString());
    }
}
