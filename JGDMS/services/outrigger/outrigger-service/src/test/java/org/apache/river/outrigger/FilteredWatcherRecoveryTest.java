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
package org.apache.river.outrigger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;

import org.junit.Test;

import net.jini.core.entry.Entry;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.space.FilterRejectedException;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.outrigger.proxy.EntryRep;
import org.apache.river.outrigger.proxy.FilterEnvelope;

import au.net.zeus.jgdms.cel.ast.ExprNode;
import au.net.zeus.jgdms.cel.authoring.CelEncoder;
import au.net.zeus.jgdms.cel.authoring.CelRecordBuilder;

/**
 * B3 filter <b>durability</b> (SOW Part&nbsp;B follow-up): a filtered standing
 * event registration must survive a server restart with its CEL filter intact,
 * and a filter that cannot be faithfully reconstructed on recovery must be
 * dropped rather than resurrected <em>unfiltered</em> (fail-closed).
 *
 * <p>This exercises the real production write/read path — the
 * {@code Storable*Watcher} {@code store}/{@code restore} that now persists the
 * raw filter envelope, and {@link OutriggerServerImpl#rebuildFilterSet} which
 * rebuilds the {@link FilterSet} by RE-ADMITTING that envelope (the same
 * re-verification the forward path runs). It is the fast unit-level "simulated
 * recover": a full activatable restart is covered separately by the qa harness.
 *
 * <p>Requires {@code jgdms-der} + {@code jgdms-cel-authoring} on the test
 * classpath (both declared test-scope in this module's pom) so real
 * {@link EntryRep}s and filter envelopes can be built.
 */
public class FilteredWatcherRecoveryTest {

    // ---- test entries -------------------------------------------------------

    public static class Reading implements Entry {
        public Double temperatureCelsius;
        public String stationName;
        public Reading() {}
        public Reading(Double t, String s) { temperatureCelsius = t; stationName = s; }
    }

    /** A second, distinctly-shaped entry that also declares a String
     *  {@code stationName}, so a {@code stationName == "North"} predicate
     *  type-checks against it too (used for the multi-template case). */
    public static class Alert implements Entry {
        public String stationName;
        public String severity;
        public Alert() {}
        public Alert(String s, String sev) { stationName = s; severity = sev; }
    }

    // ---- a marshallable listener the StorableReference can wrap -------------

    /** Minimal {@code @AtomicSerial} {@link RemoteEventListener}: the DER codec
     *  (au.net.zeus.jgdms.der) requires {@code @AtomicSerial}-declared classes,
     *  so a plain {@code Serializable} listener would be rejected at store. */
    @AtomicSerial
    public static class NoopListener implements RemoteEventListener, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ID = "id";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(ID, String.class) };
        }
        public static void serialize(PutArg arg, NoopListener l) throws IOException {
            arg.put(ID, l.id);
            arg.writeArgs();
        }

        public final String id;
        public NoopListener(String id) { this.id = id; }
        public NoopListener(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(ID, null, String.class));
        }
        @Override public void notify(RemoteEvent theEvent) { /* no-op */ }
    }

    // ---- CEL / envelope helpers (mirrors FilterAdmissionTest) ----------------

    private static ExprNode ref(String name) {
        return new ExprNode.FieldRef(
                Collections.<ExprNode.SelectorStep>singletonList(
                        new ExprNode.SelectorStep.Unqual(name)));
    }

    /** stationName == "North" */
    private static ExprNode stationIsNorth() {
        return new ExprNode.Eq(ref("stationName"), new ExprNode.LitString("North"));
    }

    private static byte[] envelopeOfPredicate(ExprNode node) throws Exception {
        byte[] celWire = CelEncoder.encode(CelRecordBuilder.predicate(node));
        return FilterEnvelope.encode(celWire);
    }

    // ---- store/restore round-trip helpers ------------------------------------

    private static byte[] store(StorableEventWatcher w) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        w.store(oos);
        oos.flush();
        return bout.toByteArray();
    }

    private static byte[] store(StorableAvailabilityWatcher w) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        w.store(oos);
        oos.flush();
        return bout.toByteArray();
    }

    private static StorableEventWatcher restoreEvent(byte[] bytes)
            throws IOException, ClassNotFoundException {
        StorableEventWatcher r = new StorableEventWatcher(0L, 0L, 0L); // recovery ctor
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        r.restore(ois);
        return r;
    }

    private static StorableAvailabilityWatcher restoreAvailability(byte[] bytes)
            throws IOException, ClassNotFoundException {
        StorableAvailabilityWatcher r =
                new StorableAvailabilityWatcher(0L, 0L, 0L); // recovery ctor
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        r.restore(ois);
        return r;
    }

    // =====================================================================
    // 1. A filtered notify registration survives recovery, filter intact.
    // =====================================================================

    @Test
    public void eventWatcherSurvivesRecoveryWithFilterIntact() throws Exception {
        final EntryRep tmpl = new EntryRep(new Reading(null, null));
        final byte[] env = envelopeOfPredicate(stationIsNorth());
        final FilterSet original = FilterSet.of(FilterAdmission.admit(env, tmpl));

        final Uuid cookie = UuidFactory.generate();
        StorableEventWatcher w = new StorableEventWatcher(
                1000L, 0L, cookie, (MarshalledInstance) null, 42L, new NoopListener("L"));
        w.setExpiration(Long.MAX_VALUE);
        w.setFilters(original);
        w.setFilterEnvelope(env);

        // Simulated recover: store -> bytes -> restore into an empty watcher.
        StorableEventWatcher r = restoreEvent(store(w));

        assertEquals("cookie must round-trip", cookie, r.getCookie());
        assertArrayEquals("the durable filter envelope must round-trip",
                env, r.filterEnvelope());

        // Rebuild the FilterSet by re-admission, exactly as recoverRegister does.
        final FilterSet rebuilt =
                OutriggerServerImpl.rebuildFilterSet(r.filterEnvelope(), new EntryRep[]{ tmpl });

        final EntryRep north = new EntryRep(new Reading(25.0, "North"));
        final EntryRep south = new EntryRep(new Reading(25.0, "South"));

        // Filter intact: correct verdicts, and identical to the pre-restart FilterSet.
        assertTrue("matching entry still delivered", FilterEval.matches(rebuilt, north));
        assertFalse("non-matching entry still excluded", FilterEval.matches(rebuilt, south));
        assertEquals(FilterEval.matches(original, north), FilterEval.matches(rebuilt, north));
        assertEquals(FilterEval.matches(original, south), FilterEval.matches(rebuilt, south));
    }

    // =====================================================================
    // 2. A multi-template filtered availability registration survives, per
    //    template.
    // =====================================================================

    @Test
    public void availabilityWatcherSurvivesRecoveryMultiTemplate() throws Exception {
        final EntryRep[] tmpls = {
                new EntryRep(new Reading(null, null)),
                new EntryRep(new Alert(null, null)),
        };
        final byte[] env = envelopeOfPredicate(stationIsNorth());

        // Forward path: admit the one envelope against each template.
        final FilterAdmission.PreparedFilter prepared = FilterAdmission.prepare(env);
        final FilterSet.Builder fb = new FilterSet.Builder();
        for (EntryRep t : tmpls) fb.add(FilterAdmission.admit(prepared, t));
        final FilterSet original = fb.build();

        final Uuid cookie = UuidFactory.generate();
        StorableAvailabilityWatcher w = new StorableAvailabilityWatcher(
                1000L, 0L, cookie, false, (MarshalledInstance) null, 7L, new NoopListener("L"));
        w.setExpiration(Long.MAX_VALUE);
        w.setFilters(original);
        w.setFilterEnvelope(env);

        StorableAvailabilityWatcher r = restoreAvailability(store(w));
        assertArrayEquals(env, r.filterEnvelope());

        final FilterSet rebuilt =
                OutriggerServerImpl.rebuildFilterSet(r.filterEnvelope(), tmpls);

        // A candidate of EITHER template's schema is gated by its own predicate.
        final EntryRep readingNorth = new EntryRep(new Reading(1.0, "North"));
        final EntryRep readingSouth = new EntryRep(new Reading(1.0, "South"));
        final EntryRep alertNorth = new EntryRep(new Alert("North", "high"));
        final EntryRep alertSouth = new EntryRep(new Alert("South", "high"));

        assertTrue(FilterEval.matches(rebuilt, readingNorth));
        assertFalse(FilterEval.matches(rebuilt, readingSouth));
        assertTrue(FilterEval.matches(rebuilt, alertNorth));
        assertFalse(FilterEval.matches(rebuilt, alertSouth));

        assertEquals(FilterEval.matches(original, readingNorth), FilterEval.matches(rebuilt, readingNorth));
        assertEquals(FilterEval.matches(original, alertNorth), FilterEval.matches(rebuilt, alertNorth));
    }

    // =====================================================================
    // 3. Fail-closed: a persisted filter that cannot be re-admitted must throw
    //    at rebuild time (so recoverRegister drops it, never runs unfiltered).
    // =====================================================================

    @Test
    public void tamperedFilterFailsClosedOnRebuild() throws Exception {
        // A well-formed FilterEnvelope wrapping non-CEL bytes: passes envelope
        // decode (prepare) but the CEL verifier rejects the payload at admit.
        final byte[] tamperedEnv =
                FilterEnvelope.encode(new byte[]{0x30, 0x03, 0x02, 0x01, 0x07});

        final Uuid cookie = UuidFactory.generate();
        StorableEventWatcher w = new StorableEventWatcher(
                1000L, 0L, cookie, (MarshalledInstance) null, 1L, new NoopListener("L"));
        w.setExpiration(Long.MAX_VALUE);
        w.setFilterEnvelope(tamperedEnv);

        StorableEventWatcher r = restoreEvent(store(w));
        assertArrayEquals(tamperedEnv, r.filterEnvelope());

        try {
            OutriggerServerImpl.rebuildFilterSet(
                    r.filterEnvelope(), new EntryRep[]{ new EntryRep(new Reading(null, null)) });
            fail("expected FilterRejectedException: an unreconstructable filter must "
                 + "fail closed, never yield an (unfiltered) FilterSet");
        } catch (FilterRejectedException expected) {
            // recoverRegister catches exactly this and DROPS the registration.
        }
    }

    @Test
    public void malformedEnvelopeFailsClosedOnRebuild() throws Exception {
        // Not even a valid FilterEnvelope: prepare() itself must reject it.
        final byte[] garbage = { 1, 2, 3, 4 };
        final Uuid cookie = UuidFactory.generate();
        StorableEventWatcher w = new StorableEventWatcher(
                1000L, 0L, cookie, (MarshalledInstance) null, 1L, new NoopListener("L"));
        w.setExpiration(Long.MAX_VALUE);
        w.setFilterEnvelope(garbage);

        StorableEventWatcher r = restoreEvent(store(w));
        assertArrayEquals(garbage, r.filterEnvelope());

        try {
            OutriggerServerImpl.rebuildFilterSet(
                    r.filterEnvelope(), new EntryRep[]{ new EntryRep(new Reading(null, null)) });
            fail("expected FilterRejectedException for a malformed envelope");
        } catch (FilterRejectedException expected) {
            // fail-closed
        }
    }

    // =====================================================================
    // 4. An unfiltered registration round-trips unchanged; a pre-durability
    //    blob (no envelope block) restores as unfiltered (backward compat).
    // =====================================================================

    @Test
    public void unfilteredRegistrationRoundTripsUnchanged() throws Exception {
        final Uuid cookie = UuidFactory.generate();
        StorableEventWatcher w = new StorableEventWatcher(
                1000L, 0L, cookie, (MarshalledInstance) null, 5L, new NoopListener("L"));
        w.setExpiration(Long.MAX_VALUE);
        // no setFilterEnvelope: unfiltered -> store writes the -1 sentinel

        StorableEventWatcher r = restoreEvent(store(w));
        assertNull("unfiltered registration carries no envelope", r.filterEnvelope());
        assertTrue("unfiltered registration has an EMPTY FilterSet", r.filters().isEmpty());
        assertEquals(cookie, r.getCookie());
    }

    @Test
    public void preDurabilityBlobRestoresUnfiltered() throws Exception {
        // Hand-write the exact PRE-B3-durability store() body (no trailing envelope
        // block). restore() must hit a clean EOF and treat it as unfiltered.
        final Uuid cookie = UuidFactory.generate();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        cookie.write(oos);
        oos.writeLong(Long.MAX_VALUE);          // expiration
        oos.writeLong(99L);                      // eventID
        oos.writeObject((MarshalledInstance) null); // handback
        oos.writeObject(new StorableReference(new NoopListener("L"))); // listener
        oos.flush();

        StorableEventWatcher r = restoreEvent(bout.toByteArray());
        assertNull("old blob has no envelope field -> unfiltered", r.filterEnvelope());
        assertTrue(r.filters().isEmpty());
        assertEquals(cookie, r.getCookie());
    }
}
