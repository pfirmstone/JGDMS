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
import java.rmi.MarshalException;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.core.entry.EntryWireField;
import net.jini.core.entry.GetEntryArg;
import net.jini.core.entry.PutEntryArg;
import net.jini.core.entry.SerialEntry;
import net.jini.entry.AbstractEntry;
import net.jini.io.UnsupportedConstraintException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip and cross-format tests for the born-format-aware
 * {@link EntryRep} constructors added for the Outrigger {@code ATOMIC_DER}
 * entry migration (SOW-Entry-ATOMIC-DER-Migration.md, task A1). Mirrors the
 * {@code org.apache.river.reggie.proxy.EntryRepDerFormatTest} pattern
 * ({@code useDer} there, an explicit {@link MarshallingFormat} here).
 *
 * <p>These tests live in the {@code org.apache.river.outrigger.proxy}
 * package to access {@link EntryRep}'s package/module-visible API.
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml) so that {@code MarshalledInstance
 * .get()}'s {@code ServiceLoader}-based {@code MarshalFactoryProvider}
 * dispatch can actually decode {@code MarshallingFormat.ATOMIC_DER}-tagged
 * payloads.
 */
public class EntryRepDerFormatTest {

    /** Minimal plain (non-{@code @SerialEntry}) Entry with one marshallable field. */
    public static class Note extends AbstractEntry {
        public String text;
        public Note() { }
        public Note(String text) { this.text = text; }
    }

    /**
     * Reflection-path Entry with boxed-scalar fields -- exactly the shape
     * the DER codec's top-level boxed-primitive support (commit
     * {@code c70cc8fa2}) unblocks: before that commit, {@code EntryRep}'s
     * per-field {@code new MarshalledInstance(fieldValue, ...,
     * MarshallingFormat.ATOMIC_DER)} would fail to marshal a bare
     * {@code Integer}/{@code Long}/{@code Boolean} field value under
     * {@code ATOMIC_DER}.
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
     * via the {@code @SerialEntry} path ({@code EntryRep.marshalSerialEntry})
     * instead of reflection, so both {@code EntryRep} marshal paths named in
     * task A1 (SOW-Entry-ATOMIC-DER-Migration.md sec.4) are exercised.
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

    // ── (a) format selection is honored, and legacy JOSS is unchanged ───────

    @Test
    public void legacyCtorAndExplicitJossCtorAreByteIdentical() throws Exception {
        // The pre-existing public EntryRep(Entry) constructor must remain
        // exactly the JOSS path -- no regression from adding the
        // format-aware overload.
        EntryRep viaLegacyCtor = new EntryRep(new Note("hello"));
        EntryRep viaExplicitJoss = new EntryRep(new Note("hello"), MarshallingFormat.JOSS);
        assertEquals("legacy EntryRep(Entry) must still produce the JOSS format",
            viaLegacyCtor, viaExplicitJoss);
    }

    @Test
    public void derFormatDecodesCorrectly() throws Exception {
        EntryRep rep = new EntryRep(new Note("hello"), MarshallingFormat.ATOMIC_DER);
        Entry decoded = rep.entry();
        assertTrue(decoded instanceof Note);
        assertEquals("hello", ((Note) decoded).text);
    }

    @Test
    public void jossFormatDecodesCorrectly() throws Exception {
        EntryRep rep = new EntryRep(new Note("hello"), MarshallingFormat.JOSS);
        Entry decoded = rep.entry();
        assertTrue(decoded instanceof Note);
        assertEquals("hello", ((Note) decoded).text);
    }

    // ── (b) the core design insight: mixing formats breaks matching ────────

    @Test
    public void mixingFormatsBreaksEqualsAndMatches() throws Exception {
        // Two EntryReps for the *logically identical* Entry value, one built
        // under each format -- exactly what a mixed-format store would hold
        // if born-immutability were not enforced (SOW-Entry-ATOMIC-DER
        // -Migration.md sec.2.3). Both formats decode to the same value, but
        // the wire bytes differ (JOSS impl-dependent serialization vs. DER
        // canonical encoding), so EntryRep.equals()/matches() -- raw
        // MarshalledInstance-byte comparisons -- must never consider them
        // the same, and must never throw either: silent no-match is the
        // documented, intentional failure shape this born-format guarantee
        // exists to prevent from ever arising within one space.
        EntryRep jossRep = new EntryRep(new Note("hello"), MarshallingFormat.JOSS);
        EntryRep derRep = new EntryRep(new Note("hello"), MarshallingFormat.ATOMIC_DER);

        assertFalse(
            "EntryRep.equals() must not consider a JOSS-encoded and a "
            + "DER-encoded EntryRep for the same logical value equal",
            jossRep.equals(derRep));
        assertFalse(
            "EntryRep.matches() must not match a DER-encoded template "
            + "against a JOSS-encoded stored entry for the same logical value",
            derRep.matches(jossRep));
        assertFalse(
            "...nor the reverse pairing",
            jossRep.matches(derRep));
    }

    // ── sanity: same format on both sides still matches ─────────────────────

    @Test
    public void sameFormatOnBothSidesStillMatches() throws Exception {
        EntryRep a = new EntryRep(new Note("hello"), MarshallingFormat.ATOMIC_DER);
        EntryRep b = new EntryRep(new Note("hello"), MarshallingFormat.ATOMIC_DER);
        assertTrue(a.equals(b));
        assertTrue(a.matches(b));
        assertTrue(b.matches(a));
    }

    @Test
    public void sameJossFormatOnBothSidesStillMatches() throws Exception {
        EntryRep a = new EntryRep(new Note("hello"), MarshallingFormat.JOSS);
        EntryRep b = new EntryRep(new Note("hello"), MarshallingFormat.JOSS);
        assertTrue(a.equals(b));
        assertTrue(a.matches(b));
        assertTrue(b.matches(a));
    }

    // ── null-value fields: format is inert when there's nothing to marshal ──

    // ── (c) fail-loud, never a silent JOSS fallback ─────────────────────────

    /**
     * Documents the fail-loud mechanism {@code EntryRep}'s per-field
     * marshalling relies on ({@code MarshalledInstance}'s constraint-driven
     * constructor, {@code net.jini.io.MarshalledInstance
     * #chooseMarshalFactory}): a required {@link MarshallingFormat} with no
     * registered codec never silently falls back to JOSS -- it raises
     * {@link UnsupportedConstraintException} (wrapped here as the
     * {@link MarshalException} {@code EntryRep} already documents throwing
     * for un-marshallable field values). This is the same shape of failure
     * a genuinely DER-incapable legacy client/classpath (one missing
     * {@code jgdms-der}'s {@code MarshalFactoryProvider}) would hit against
     * a DER-configured space -- simulated here with an unregistered format
     * identifier rather than by removing {@code jgdms-der} from this test
     * module's own classpath (needed by the other tests in this class for
     * DER decode), so the scope is the mechanism, not a full end-to-end
     * classpath-isolation rig.
     */
    @Test
    public void unsupportedFormatFailsLoudNeverSilentJossFallback() {
        MarshallingFormat noSuchCodec = new MarshallingFormat("test-only/no-such-codec");
        try {
            new EntryRep(new Note("hello"), noSuchCodec);
            fail("marshalling under a format with no registered codec must "
                + "fail loud, never silently fall back to JOSS");
        } catch (MarshalException e) {
            Throwable cause = e.getCause();
            assertTrue(
                "expected the fail-loud UnsupportedConstraintException, got " + cause,
                cause instanceof UnsupportedConstraintException);
        }
    }

    @Test
    public void nullFieldMatchesAcrossFormats() throws Exception {
        // A wildcard/null field never reaches MarshalledInstance construction
        // (EntryRep stores a null slot instead), so it is naturally
        // format-independent -- unlike a populated field, which is not.
        EntryRep jossRep = new EntryRep(new Note(null), MarshallingFormat.JOSS);
        EntryRep derRep = new EntryRep(new Note(null), MarshallingFormat.ATOMIC_DER);
        assertTrue(jossRep.equals(derRep));
        assertTrue(jossRep.matches(derRep));
    }

    // ── (d) numeric (boxed-scalar) entry end-to-end round trip under DER ───
    //
    // This is the property that was silently broken before jgdms-der commit
    // c70cc8fa2 added top-level boxed-scalar support: EntryRep wraps each
    // field in its OWN top-level MarshalledInstance, so a bare boxed
    // Integer/Long/Boolean field value hit the codec's top-level entry
    // point directly (not nested inside a container class), which used to
    // reject it. These tests fail without that codec fix and pass with it
    // -- the end-to-end validation this SOW's board review asked for.

    @Test
    public void numericEntryRoundTripsUnderDerReflectionPath() throws Exception {
        EntryRep rep = new EntryRep(
            new NumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE),
            MarshallingFormat.ATOMIC_DER);
        Entry decoded = rep.entry();
        assertTrue(decoded instanceof NumericEntry);
        NumericEntry n = (NumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    @Test
    public void numericEntryRoundTripsUnderDerSerialEntryPath() throws Exception {
        EntryRep rep = new EntryRep(
            new SerialNumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE),
            MarshallingFormat.ATOMIC_DER);
        Entry decoded = rep.entry();
        assertTrue(decoded instanceof SerialNumericEntry);
        SerialNumericEntry n = (SerialNumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    /**
     * The load-bearing matching property (SOW sec.2.3): two DER-marshalled
     * {@code EntryRep}s built from equal boxed-scalar field values must be
     * byte-identical -- {@code EntryRep.equals()}/{@code matches()} are raw
     * {@code MarshalledInstance}-payload-byte comparisons (final,
     * {@code MarshalledInstance.equals}), so this assertion IS the
     * byte-identical-payload assertion, not a proxy for it.
     */
    @Test
    public void numericEntryByteIdenticalForEqualValuesReflectionPath() throws Exception {
        EntryRep a = new EntryRep(
            new NumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE),
            MarshallingFormat.ATOMIC_DER);
        EntryRep b = new EntryRep(
            new NumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE),
            MarshallingFormat.ATOMIC_DER);
        assertTrue("equal boxed-scalar entry values must marshal to "
            + "byte-identical EntryReps under DER", a.equals(b));
        assertTrue(a.matches(b));
        assertTrue(b.matches(a));
    }

    @Test
    public void numericEntryByteIdenticalForEqualValuesSerialEntryPath() throws Exception {
        EntryRep a = new EntryRep(
            new SerialNumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE),
            MarshallingFormat.ATOMIC_DER);
        EntryRep b = new EntryRep(
            new SerialNumericEntry(Integer.valueOf(7), Long.valueOf(1234567890123L), Boolean.FALSE),
            MarshallingFormat.ATOMIC_DER);
        assertTrue("equal boxed-scalar entry values must marshal to "
            + "byte-identical EntryReps under DER", a.equals(b));
        assertTrue(a.matches(b));
        assertTrue(b.matches(a));
    }

    /** Companion sanity: distinct values must NOT match (no false positive from the fix). */
    @Test
    public void numericEntryDifferentValuesDoNotMatchUnderDer() throws Exception {
        EntryRep a = new EntryRep(
            new NumericEntry(Integer.valueOf(1), Long.valueOf(1L), Boolean.TRUE),
            MarshallingFormat.ATOMIC_DER);
        EntryRep b = new EntryRep(
            new NumericEntry(Integer.valueOf(2), Long.valueOf(1L), Boolean.TRUE),
            MarshallingFormat.ATOMIC_DER);
        assertFalse(a.equals(b));
        assertFalse(a.matches(b));
    }

    /** Same numeric fixture, sanity-checked under the legacy JOSS format too. */
    @Test
    public void numericEntryRoundTripsUnderJoss() throws Exception {
        EntryRep rep = new EntryRep(
            new NumericEntry(Integer.valueOf(42), Long.valueOf(9001L), Boolean.TRUE),
            MarshallingFormat.JOSS);
        Entry decoded = rep.entry();
        assertTrue(decoded instanceof NumericEntry);
        NumericEntry n = (NumericEntry) decoded;
        assertEquals(Integer.valueOf(42), n.id);
        assertEquals(Long.valueOf(9001L), n.seq);
        assertEquals(Boolean.TRUE, n.flag);
    }

    // ── (e) Finding B regression: unchecked marshal failures must surface
    //        as MarshalException, not escape the constructor uncaught ──────

    /**
     * A plain, non-{@code @AtomicSerial} {@code Serializable} value class --
     * the DER codec's {@code DerObjectStreamCodec} rejects any such type
     * with an unchecked {@link UnsupportedOperationException}
     * ("@AtomicSerial-restricted"), never an {@link java.io.IOException}.
     */
    public static class PlainValue implements java.io.Serializable {
        public final int x;
        public PlainValue(int x) { this.x = x; }
    }

    /** Reflection-path Entry whose one field cannot marshal under DER. */
    public static class BadFieldEntry extends AbstractEntry {
        public PlainValue value;
        public BadFieldEntry() { }
        public BadFieldEntry(PlainValue value) { this.value = value; }
    }

    /**
     * Board-review fix (Finding B): before this fix, {@code EntryRep}'s
     * reflection-path per-field marshal loop only {@code catch
     * (IOException)}, so the codec's {@code UnsupportedOperationException}
     * for a non-DER-encodable field value escaped the constructor
     * unchecked -- violating its documented "throws only MarshalException"
     * contract. This test fails with an uncaught
     * {@code UnsupportedOperationException} before the fix and passes
     * (catching the properly-wrapped {@link MarshalException}) after.
     */
    @Test
    public void unmarshallableFieldSurfacesAsMarshalExceptionNotUncheckedEscape() {
        try {
            new EntryRep(new BadFieldEntry(new PlainValue(1)), MarshallingFormat.ATOMIC_DER);
            fail("a non-@AtomicSerial field value must fail marshalling under "
                + "ATOMIC_DER, not silently succeed");
        } catch (MarshalException expected) {
            Throwable cause = expected.getCause();
            assertTrue(
                "expected the underlying UnsupportedOperationException as "
                + "the MarshalException's cause, got " + cause,
                cause instanceof UnsupportedOperationException);
        }
    }
}
