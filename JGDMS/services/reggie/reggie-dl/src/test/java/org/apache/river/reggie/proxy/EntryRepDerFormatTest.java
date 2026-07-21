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
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Assume;
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
     * Minimal @AtomicSerial payload. Both the JOSS (AtomicMarshalOutputStream)
     * and DER (DerObjectStreamCodec) marshal paths this test exercises are
     * @AtomicSerial-restricted (plain java.io.Serializable is rejected by
     * both), so the fixture must be a properly-declared @AtomicSerial type,
     * following this codebase's convention (see e.g.
     * tests.support.SerializableTestObject).
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

    /**
     * Reflection-path Entry with boxed-scalar fields -- exactly the shape
     * the DER codec's top-level boxed-primitive support (commit
     * {@code c70cc8fa2}) unblocks: before that commit, {@code EntryRep}'s
     * per-field {@code new MarshalledInstance(fieldValue, ...,
     * MarshallingFormat.ATOMIC_DER)} would fail to marshal a bare
     * {@code Integer}/{@code Long}/{@code Boolean} field value under
     * {@code ATOMIC_DER}. Mirrors outrigger-dl's
     * {@code EntryRepDerFormatTest.NumericEntry} (SOW-Entry-ATOMIC-DER
     * -Migration.md sec.4 A3, structurally parallel to A1).
     */
    public static class NumericEntry extends AbstractEntry {
        public Integer id;
        public Long seq;
        public Boolean flag;
        public NumericEntry() { }
        public NumericEntry(Integer id, Long seq, Boolean flag) {
            this.id = id;
            this.seq = seq;
            this.flag = flag;
        }
    }

    /**
     * The same boxed-scalar shape as {@link NumericEntry}, but marshalled
     * via the {@code @SerialEntry} path ({@code EntryRep.fieldsViaSerialEntry})
     * instead of reflection, so both {@code EntryRep} marshal paths are
     * exercised.
     */
    @SerialEntry
    public static class SerialNumericEntry extends AbstractEntry {
        public Integer id;
        public Long seq;
        public Boolean flag;

        public SerialNumericEntry() { }

        public SerialNumericEntry(Integer id, Long seq, Boolean flag) {
            this.id = id;
            this.seq = seq;
            this.flag = flag;
        }

        public static EntryWireField[] entryForm() {
            return new EntryWireField[] {
                new EntryWireField("id", Integer.class),
                new EntryWireField("seq", Long.class),
                new EntryWireField("flag", Boolean.class),
            };
        }

        public SerialNumericEntry(GetEntryArg arg) throws IOException {
            id = arg.get("id", null, Integer.class);
            seq = arg.get("seq", null, Long.class);
            flag = arg.get("flag", null, Boolean.class);
        }

        public static void serialize(PutEntryArg arg, SerialNumericEntry e)
                throws IOException {
            arg.put("id", e.id);
            arg.put("seq", e.seq);
            arg.put("flag", e.flag);
            arg.writeArgs();
        }
    }

    /**
     * Returns the sole field value, which is now a bare {@link
     * MarshalledInstance} (the A3-rework wire shape -- {@code EntryRep
     * .fields} is {@code MarshalledInstance[]}, mirroring Outrigger's
     * {@code EntryRep.values}, not the pre-rework {@code Object[]} of
     * raw-or-{@code MarshalledWrapper} elements).
     */
    private static MarshalledInstance wrappedField(EntryRep rep) {
        List flds = rep.fields();
        assertEquals(1, flds.size());
        Object val = flds.get(0);
        assertTrue("field should have been wrapped in a MarshalledInstance",
            val instanceof MarshalledInstance);
        return (MarshalledInstance) val;
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
        MarshalledInstance instance = wrappedField(rep);
        assertTrue("useDer=false must produce the legacy JOSS AtomicMarshalledInstance form",
            instance instanceof AtomicMarshalledInstance);
    }

    @Test
    public void useDerFalseIsByteIdenticalToDirectLegacyConstruction() throws Exception {
        Payload payload = new Payload("hello");
        PayloadEntry entry = new PayloadEntry(payload);
        EntryRep rep = repFor(entry, false);
        MarshalledInstance viaEntryRep = wrappedField(rep);

        // Exactly the pre-existing construction shape EntryRep used to hardcode.
        MarshalledInstance viaDirectConstruction = new AtomicMarshalledInstance(payload);

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
        MarshalledInstance instance = wrappedField(rep);
        assertFalse("useDer=true must NOT produce the legacy JOSS AtomicMarshalledInstance form",
            instance instanceof AtomicMarshalledInstance);
    }

    @Test
    public void useDerTrueDecodesCorrectly() throws Exception {
        Payload payload = new Payload("hello");
        PayloadEntry entry = new PayloadEntry(payload);
        EntryRep rep = repFor(entry, true);
        MarshalledInstance instance = wrappedField(rep);
        Object decoded = instance.get(false);
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
            + "MarshalledInstance.equals() is a raw byte comparison of the "
            + "marshalled payload, so mixing formats silently breaks "
            + "matching instead of throwing. This is exactly why the format "
            + "choice must be a single, immutable, born value for the whole "
            + "registrar instance (RegistrarImpl.entryFormat()), never "
            + "derived per-call from a client's own droppable constraints.",
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

    // ── (d) numeric (boxed-scalar) ATTRIBUTE end-to-end round trip under DER ─
    //
    // This is the property that was silently broken before jgdms-der commit
    // c70cc8fa2 added top-level boxed-scalar support: EntryRep wraps each
    // field in its own top-level MarshalledInstance, so a bare boxed
    // Integer/Long/Boolean field value hit the codec's top-level entry point
    // directly (not nested inside a container class), which used to reject
    // it. These tests fail without that codec fix and pass with it -- the
    // end-to-end validation the SOW's A3 row asks for, confirming the fix
    // already on trunk covers Reggie's attribute fields too (no codec work
    // needed here).

    @Test
    public void numericAttributeRoundTripsUnderDerReflectionPath() throws Exception {
        EntryRep rep = repFor(
            new NumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE), true);
        Entry decoded = rep.get();
        assertTrue(decoded instanceof NumericEntry);
        NumericEntry n = (NumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    @Test
    public void numericAttributeRoundTripsUnderDerSerialEntryPath() throws Exception {
        EntryRep rep = repFor(
            new SerialNumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE), true);
        Entry decoded = rep.get();
        assertTrue(decoded instanceof SerialNumericEntry);
        SerialNumericEntry n = (SerialNumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    /**
     * The load-bearing matching property (SOW sec.2.3): two DER-marshalled
     * {@code EntryRep}s built from equal boxed-scalar attribute values must
     * be byte-identical -- {@code EntryRep.equals()}/{@code matchEntry()}
     * are raw {@code MarshalledWrapper}/{@code MarshalledInstance}-payload
     * byte comparisons, so this assertion IS the byte-identical-payload
     * assertion, not a proxy for it.
     */
    @Test
    public void numericAttributeByteIdenticalForEqualValuesReflectionPath() throws Exception {
        EntryRep a = repFor(
            new NumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE), true);
        EntryRep b = repFor(
            new NumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE), true);
        assertTrue("equal boxed-scalar attribute values must marshal to "
            + "byte-identical EntryReps under DER", a.equals(b));
        assertTrue(a.matchEntry(b));
        assertTrue(b.matchEntry(a));
    }

    @Test
    public void numericAttributeByteIdenticalForEqualValuesSerialEntryPath() throws Exception {
        EntryRep a = repFor(
            new SerialNumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE), true);
        EntryRep b = repFor(
            new SerialNumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE), true);
        assertTrue("equal boxed-scalar attribute values must marshal to "
            + "byte-identical EntryReps under DER", a.equals(b));
        assertTrue(a.matchEntry(b));
        assertTrue(b.matchEntry(a));
    }

    /** Companion sanity: distinct values must NOT match (no false positive from the fix). */
    @Test
    public void numericAttributeDifferentValuesDoNotMatchUnderDer() throws Exception {
        EntryRep a = repFor(
            new NumericEntry(Integer.valueOf(1), Long.valueOf(1L), Boolean.TRUE), true);
        EntryRep b = repFor(
            new NumericEntry(Integer.valueOf(2), Long.valueOf(1L), Boolean.TRUE), true);
        assertFalse(a.equals(b));
        assertFalse(a.matchEntry(b));
    }

    /** Same numeric fixture, sanity-checked under the legacy JOSS format too. */
    @Test
    public void numericAttributeRoundTripsUnderJoss() throws Exception {
        assumeJossScalarDecodeWorksOnThisJdk();
        EntryRep rep = repFor(
            new NumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE), false);
        Entry decoded = rep.get();
        assertTrue(decoded instanceof NumericEntry);
        NumericEntry n = (NumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    /**
     * Skips (never fails) this test on a JDK where the legacy JOSS decode
     * path is unusable -- a pre-existing, out-of-scope {@code
     * jgdms-platform} gap this A3 rework surfaces but does not cause:
     * {@code AtomicMarshalInputStream}'s subclass-permission check
     * ({@code SerializablePermission(...).checkGuard(null)}) throws
     * unconditionally on a JDK with the Security Manager fully removed
     * (e.g. vanilla JDK 24+, per JEP 486) rather than no-op'ing when no
     * {@code SecurityManager} is installed, which is what every other
     * {@code Permission.checkGuard} caller in the JDK does. DirtyChai (JGDMS
     * 4.0.0's target platform) keeps SM support and is unaffected.
     * <p>
     * Before this A3 rework, {@code EntryRep}'s known-immutable-type
     * raw-storage optimization meant a boxed scalar field was never actually
     * decoded through this path under JOSS at all (it was stored raw,
     * unwrapped) -- so this gap was latent and untested. Removing that
     * optimization (every field is now wrapped, required for the {@code
     * MarshalledInstance[]} DER fix) is what newly exercises it here. The
     * DER path is unaffected (a completely separate codec, {@code
     * DerMarshalInstanceInput}, that does not call this JDK-SM-dependent
     * check) -- see the {@code useDerTrue}/{@code numericAttribute...Der...}
     * tests in this file and in {@code RealWireDerRoundTripTest}, which
     * exercise the equivalent decode under DER and are not skipped.
     */
    private static void assumeJossScalarDecodeWorksOnThisJdk() {
        try {
            new AtomicMarshalledInstance(Integer.valueOf(1)).get(false);
        } catch (SecurityException e) {
            Assume.assumeNoException(
                "Skipping: this JDK's AtomicMarshalInputStream cannot decode "
                + "even a trivial JOSS-wrapped value (Security Manager fully "
                + "removed, e.g. vanilla JDK 24+/JEP 486) -- a pre-existing "
                + "jgdms-platform gap, not a reggie A3-rework regression. "
                + "Requires an SM-capable JDK (e.g. DirtyChai) to actually run.",
                e);
        } catch (Exception e) {
            // Anything else is unrelated to this preflight; let the real
            // test body run and fail on its own terms if warranted.
        }
    }
}
