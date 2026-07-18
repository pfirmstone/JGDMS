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
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.List;
import net.jini.core.entry.Entry;
import net.jini.entry.AbstractEntry;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.proxy.MarshalledWrapper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for the {@code useDer} per-relationship format derivation
 * added to {@link EntryRep}/{@link Item} for the reggie EntryRep/Item DER
 * migration (the 3 sites deferred from the 19-site AtomicMarshalledInstance
 * -&gt;DER migration, since these cross an actual JVM boundary to a
 * third-party client).
 *
 * <p>These tests live in the {@code org.apache.river.reggie.proxy} package to
 * access {@link EntryRep}'s package-private {@code fields()} accessor and
 * {@link EntryRep#toEntryRep(Entry[], boolean, boolean)}.
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml) so that {@code MarshalledInstance
 * .get()}'s {@code ServiceLoader}-based {@code MarshalFactoryProvider}
 * dispatch can actually decode {@code MarshallingFormat.ATOMIC_DER}-tagged
 * payloads; the compiled test source itself references no {@code jgdms-der}
 * symbol, so this module's {@code --release 8} target for {@code reggie-dl}
 * is unaffected.
 */
public class EntryRepDerFormatTest {

    /**
     * Minimal @AtomicSerial payload -- not one of EntryField's known-immutable
     * types, so ClassMapper.EntryField.marshal is true for a field of this
     * type and it will be wrapped in a MarshalledWrapper. Both the JOSS
     * (AtomicMarshalOutputStream) and DER (DerObjectStreamCodec) marshal
     * paths this test exercises are @AtomicSerial-restricted (plain
     * java.io.Serializable is rejected by both), so the fixture must be a
     * properly-declared @AtomicSerial type, following this codebase's
     * convention (see e.g. tests.support.SerializableTestObject).
     */
    @AtomicSerial
    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, Payload p) throws IOException {
            arg.put(VALUE, p.value);
            arg.writeArgs();
        }

        public final String value;

        public Payload(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        public Payload(String value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Payload && value.equals(((Payload) o).value);
        }
        @Override
        public int hashCode() { return value.hashCode(); }
    }

    /** Minimal plain (non-@SerialEntry) Entry with one marshallable field,
     *  exercising EntryRep's ClassMapper.getFields()/fields(Entry,boolean)
     *  path (not the fieldsViaSerialEntry(...) path). */
    public static class PayloadEntry extends AbstractEntry {
        public Payload payload;
        public PayloadEntry() {}
        public PayloadEntry(Payload payload) { this.payload = payload; }
    }

    private static MarshalledWrapper wrappedField(EntryRep rep) {
        List flds = rep.fields();
        assertEquals(1, flds.size());
        Object val = flds.get(0);
        assertTrue("field should have been wrapped in a MarshalledWrapper",
            val instanceof MarshalledWrapper);
        return (MarshalledWrapper) val;
    }

    private static EntryRep repFor(Entry entry, boolean useDer) throws RemoteException {
        EntryRep[] reps = EntryRep.toEntryRep(new Entry[]{ entry }, true, useDer);
        return reps[0];
    }

    // ── (a) useDer=false: unchanged legacy JOSS behavior ────────────────────

    @Test
    public void useDerFalseProducesJossFormat() throws Exception {
        PayloadEntry entry = new PayloadEntry(new Payload("hello"));
        EntryRep rep = repFor(entry, false);
        MarshalledWrapper wrapper = wrappedField(rep);
        Object instance = wrapper.getMarshalledInstance();
        assertTrue("useDer=false must produce the legacy JOSS AtomicMarshalledInstance form",
            instance instanceof AtomicMarshalledInstance);
    }

    @Test
    public void useDerFalseIsByteIdenticalToDirectLegacyConstruction() throws Exception {
        Payload payload = new Payload("hello");
        PayloadEntry entry = new PayloadEntry(payload);
        EntryRep rep = repFor(entry, false);
        MarshalledWrapper viaEntryRep = wrappedField(rep);

        // Exactly the pre-existing construction shape EntryRep used to hardcode.
        MarshalledWrapper viaDirectConstruction =
            new MarshalledWrapper(new AtomicMarshalledInstance(payload));

        // MarshalledInstance.equals() is a raw payloadBytes byte-array comparison
        // (see net.jini.io.MarshalledInstance#equals), so this proves useDer=false
        // is byte-identical to the unmodified legacy call site -- no regression.
        assertEquals(viaDirectConstruction, viaEntryRep);
    }

    // ── (b) useDer=true: DER-tagged, decodes correctly ──────────────────────

    @Test
    public void useDerTrueProducesDerTaggedInstance() throws Exception {
        PayloadEntry entry = new PayloadEntry(new Payload("hello"));
        EntryRep rep = repFor(entry, true);
        MarshalledWrapper wrapper = wrappedField(rep);
        Object instance = wrapper.getMarshalledInstance();
        assertTrue("useDer=true must produce a MarshalledInstance",
            instance instanceof MarshalledInstance);
        assertFalse("useDer=true must NOT produce the legacy JOSS AtomicMarshalledInstance form",
            instance instanceof AtomicMarshalledInstance);
    }

    @Test
    public void useDerTrueDecodesCorrectly() throws Exception {
        Payload payload = new Payload("hello");
        PayloadEntry entry = new PayloadEntry(payload);
        EntryRep rep = repFor(entry, true);
        MarshalledWrapper wrapper = wrappedField(rep);
        Object decoded = wrapper.get();
        assertEquals("DER-tagged instance must decode back to the original value",
            payload, decoded);
    }

    // ── (c) the core design insight: mixing formats breaks matching ─────────

    @Test
    public void mixingFormatsBreaksEqualsAndMatchEntry() throws Exception {
        // Two EntryReps for the *logically identical* Entry value, one
        // constructed via each path -- exactly what happens if a client is
        // concurrently registered with both a "legacy" and a "der" Reggie
        // (the normal case mid-migration, since JoinManager registers with
        // every discovered lookup service concurrently).
        Payload payload = new Payload("hello");
        EntryRep jossRep = repFor(new PayloadEntry(new Payload(payload.value)), false);
        EntryRep derRep  = repFor(new PayloadEntry(new Payload(payload.value)), true);

        assertFalse(
            "EntryRep.equals() must NOT consider a JOSS-encoded and a "
            + "DER-encoded EntryRep for the same logical Entry equal -- "
            + "MarshalledWrapper.equals() is a raw byte comparison of the "
            + "inner MarshalledInstance, so mixing formats silently breaks "
            + "matching instead of throwing. This is exactly why the format "
            + "choice must be derived per client<->server relationship "
            + "(Util.requiresDerFormat), never as a JVM-global flag.",
            jossRep.equals(derRep));

        assertFalse(
            "EntryRep.matchEntry() must NOT match a DER-encoded template "
            + "against a JOSS-encoded (or vice versa) registered entry for "
            + "the same logical value",
            derRep.matchEntry(jossRep));
        assertFalse(
            jossRep.matchEntry(derRep));
    }

    // ── sanity: same format on both sides still matches ─────────────────────

    @Test
    public void sameFormatOnBothSidesStillMatches() throws Exception {
        Payload payload = new Payload("hello");
        EntryRep a = repFor(new PayloadEntry(new Payload(payload.value)), true);
        EntryRep b = repFor(new PayloadEntry(new Payload(payload.value)), true);
        assertTrue(a.equals(b));
        assertTrue(a.matchEntry(b));
        assertTrue(b.matchEntry(a));
    }
}
