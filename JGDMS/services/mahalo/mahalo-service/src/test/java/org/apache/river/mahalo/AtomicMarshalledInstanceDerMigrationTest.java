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
package org.apache.river.mahalo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.io.MarshalFactory;
import net.jini.io.MarshalFactoryProvider;
import net.jini.io.MarshalledInstance;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test coverage for the two mahalo write sites converted from
 * {@code org.apache.river.api.io.AtomicMarshalledInstance} (JOSS) to
 * {@code net.jini.io.MarshalledInstance} + {@code MarshallingFormat.ATOMIC_DER}
 * by commit 221fe251c ("persist: convert 19 AtomicMarshalledInstance write
 * sites to DER"):
 *
 * <ul>
 *   <li>{@link StorableObject#StorableObject(Object)} / {@link StorableObject#get()}
 *       (the {@code toMI()} write site) -- wraps a single, non-substitutable
 *       reference (e.g. a {@code TransactionParticipant} proxy inside
 *       {@code ParticipantHandle}). Its widened catch converts an unresolvable
 *       {@code payloadFormat} into the method's already-documented
 *       {@code RemoteException} via {@code fatalError()}.</li>
 *   <li>{@code JoinStateManager.writeAttributes(Entry[], ObjectOutput)} /
 *       {@code JoinStateManager.readAttributes(ObjectInput)} -- wraps each
 *       service attribute in its own {@code MarshalledInstance} so a single
 *       bad entry doesn't sink the whole array. Its widened catch drops the
 *       offending entry and keeps recovering the rest, exactly like the
 *       pre-existing {@code IOException}/{@code ClassNotFoundException}
 *       handling either side of it.</li>
 * </ul>
 *
 * <p>Both methods under test in {@code JoinStateManager} are {@code private
 * static}; this test reaches them via reflection rather than duplicating
 * their wire format, so a future refactor of the method bodies is still
 * exercised through the real production code path.
 *
 * <p>The "legacy JOSS-only reader encounters DER data it can't handle"
 * scenario (jgdms-der missing from the reader's classpath) is reproduced
 * in-process by evicting {@link MarshalledInstance}'s {@code ServiceLoader}-
 * populated {@code MarshalFactoryProvider} registry for the duration of a
 * single test, then restoring it -- the same {@code factoryForFormat}
 * {@code IllegalStateException} that a genuinely DER-less second JVM would
 * throw (verified with a real two-process probe during the commit's
 * adversarial review; reproduced here in-process so it runs as part of the
 * committed suite).
 */
public class AtomicMarshalledInstanceDerMigrationTest {

    // -----------------------------------------------------------------------
    // Section A -- StorableObject.toMI() / StorableObject.get()
    // -----------------------------------------------------------------------

    /**
     * A real object, written through the production {@code toMI()} write
     * site, must round-trip and must actually have been encoded via DER
     * (not silently fallen back to JOSS -- that would defeat the whole
     * migration while still passing a format-blind round-trip check).
     *
     * <p>{@code StorableObject}'s constructor caches the live object
     * reference it was given ({@code this.obj = obj}), so calling
     * {@code get()} directly on a freshly-constructed instance would never
     * exercise the read side at all. A genuinely recovered
     * {@code StorableObject} -- e.g. one pulled back out of a
     * {@code ReliableLog} -- comes back with that transient cache reset to
     * {@code null}; {@link #reserialize} reproduces that by round-tripping
     * through ordinary Java serialization (exercising
     * {@code StorableObject.writeObject()}/{@code readObject()} too), so
     * {@code get()} is forced to actually decode {@code instance}.
     */
    @Test
    public void storableObjectRoundTripsRealObjectViaDerEncoding() throws Exception {
        String payload = "mahalo-participant-payload";

        StorableObject original = new StorableObject(payload);
        StorableObject recovered = reserialize(original);

        assertEquals("round trip through the real write+read site must preserve the value",
                payload, recovered.get());
        assertEquals("write site must actually encode via ATOMIC_DER, not silently fall back to JOSS",
                MarshallingFormat.ATOMIC_DER.getFormat(),
                payloadFormatOf(instanceFieldOf(recovered)));
    }

    /**
     * When the reader lacks the DER codec module (simulated by evicting the
     * DER {@link MarshalFactoryProvider} from {@link MarshalledInstance}'s
     * registry), {@code StorableObject.get()}'s widened
     * {@code catch (IllegalStateException e)} must convert the failure into
     * the method's own documented {@link RemoteException}, not let an
     * uncaught {@link IllegalStateException} escape.
     *
     * <p>As above, the instance under test must come back through a real
     * serialize/deserialize round trip first so {@code get()} is forced to
     * actually invoke {@code instance.get(false)} instead of returning a
     * cached live reference.
     */
    @Test
    public void storableObjectGetThrowsRemoteExceptionWhenLegacyReaderLacksDerCodec() throws Exception {
        StorableObject original = new StorableObject("payload-written-with-der-present");
        StorableObject recovered = reserialize(original);

        runWithDerProviderMissing(() -> {
            try {
                recovered.get();
                fail("expected RemoteException: a reader lacking the DER codec module must not "
                        + "crash with an uncaught IllegalStateException");
            } catch (RemoteException expected) {
                assertTrue("StorableObject.get() must report the underlying cause",
                        expected.getCause() instanceof IllegalStateException);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Section B -- JoinStateManager.writeAttributes() / readAttributes()
    // -----------------------------------------------------------------------

    /**
     * Multiple real attributes, written through the production
     * {@code writeAttributes()} write site and recovered through the
     * production {@code readAttributes()} loop, must round-trip intact, and
     * the wire bytes must actually carry the {@code ATOMIC_DER} format (not
     * a silent JOSS fallback).
     */
    @Test
    public void writeAttributesEncodesEachEntryUsingAtomicDerFormatAndRoundTrips() throws Exception {
        Entry[] attrs = { new TestAttr("alpha", 10), new TestAttr("beta", 20) };

        byte[] wire = writeAttributesToBytes(attrs);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(wire));
        Entry[] recovered = readAttributesViaReflection(ois);
        assertArrayEquals("round trip through the real write+read site must preserve every entry",
                attrs, recovered);

        // Confirm the wire bytes really are DER: peel the first entry off independently
        // (bypassing readAttributes' get()) and inspect its payloadFormat directly.
        ObjectInputStream raw = new ObjectInputStream(new ByteArrayInputStream(wire));
        raw.readInt(); // element count
        Object first = raw.readObject();
        assertTrue(first instanceof MarshalledInstance);
        assertEquals("write site must actually encode via ATOMIC_DER, not silently fall back to JOSS",
                MarshallingFormat.ATOMIC_DER.getFormat(),
                payloadFormatOf((MarshalledInstance) first));
    }

    /**
     * Reproduces the fault-isolation contract the commit's read-side catch
     * widening protects: when the reader lacks the DER codec, a legacy
     * JOSS-encoded entry sitting alongside newer DER-encoded entries in the
     * same log record must still be recovered, and the unrecoverable DER
     * entries must be silently dropped -- {@code readAttributes()} must not
     * throw and abort the whole recovery loop (which is exactly what an
     * unhandled {@link IllegalStateException} from
     * {@code MarshalledInstance.get()} would otherwise do, per element two
     * and three sinking element one along with them).
     */
    @Test
    public void readAttributesIsolatesDerFailureFromJossSurvivors() throws Exception {
        TestAttr legacy = new TestAttr("legacy-joss", 1);
        TestAttr newA = new TestAttr("new-der-a", 2);
        TestAttr newB = new TestAttr("new-der-b", 3);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeInt(3);
        // A pre-migration log record would have this entry in legacy JOSS form.
        oos.writeObject(new MarshalledInstance(legacy, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.JOSS, null)));
        // These two match what writeAttributes() emits post-migration.
        oos.writeObject(new MarshalledInstance(newA, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null)));
        oos.writeObject(new MarshalledInstance(newB, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null)));
        oos.flush();
        byte[] wire = bos.toByteArray();

        runWithDerProviderMissing(() -> {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(wire));
            Entry[] recovered = readAttributesViaReflection(ois);
            assertEquals("only the JOSS-encoded legacy entry can be decoded without the DER "
                    + "codec; the two DER entries must be dropped, not crash the loop",
                    1, recovered.length);
            assertEquals(legacy, recovered[0]);
        });
    }

    // -----------------------------------------------------------------------
    // Reflection helpers -- reach the private write sites / private state
    // without duplicating production wire logic.
    // -----------------------------------------------------------------------

    /**
     * Round-trips {@code so} through ordinary Java serialization, reproducing
     * what a real recovered {@code StorableObject} looks like coming back out
     * of persistence: its transient {@code obj} cache is {@code null}, and
     * only the wire {@code instance} field is populated.
     */
    private static StorableObject reserialize(StorableObject so) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(so);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return (StorableObject) ois.readObject();
        }
    }

    private static byte[] writeAttributesToBytes(Entry[] attrs) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Method m = JoinStateManager.class.getDeclaredMethod(
                "writeAttributes", Entry[].class, ObjectOutput.class);
        m.setAccessible(true);
        try {
            m.invoke(null, (Object) attrs, oos);
        } catch (InvocationTargetException e) {
            throwCause(e);
        }
        oos.flush();
        return bos.toByteArray();
    }

    private static Entry[] readAttributesViaReflection(ObjectInput in) throws Exception {
        Method m = JoinStateManager.class.getDeclaredMethod("readAttributes", ObjectInput.class);
        m.setAccessible(true);
        try {
            return (Entry[]) m.invoke(null, in);
        } catch (InvocationTargetException e) {
            throwCause(e);
            throw new AssertionError("unreachable");
        }
    }

    private static void throwCause(InvocationTargetException e) throws Exception {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) throw (Exception) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw e;
    }

    private static MarshalledInstance instanceFieldOf(StorableObject so) throws Exception {
        Field f = StorableObject.class.getDeclaredField("instance");
        f.setAccessible(true);
        return (MarshalledInstance) f.get(so);
    }

    private static String payloadFormatOf(MarshalledInstance mi) throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        return (String) f.get(mi);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs {@code body} with {@link MarshalledInstance}'s {@code ServiceLoader}-
     * populated {@code MarshalFactoryProvider} registry emptied out, so that any
     * attempt to resolve {@code MarshallingFormat.ATOMIC_DER} during {@code body}
     * fails exactly the way it would on a reader that genuinely doesn't have
     * {@code jgdms-der} on its classpath ({@code factoryForFormat} throws
     * {@code IllegalStateException}). The real registry is restored afterwards,
     * even if {@code body} throws.
     */
    private static void runWithDerProviderMissing(ThrowingRunnable body) throws Exception {
        Field providersField = MarshalledInstance.class.getDeclaredField("providers");
        providersField.setAccessible(true);

        // Prime the real ServiceLoader-discovered registry first, and sanity-check
        // that jgdms-der is genuinely resolvable here -- otherwise this whole test
        // class would be vacuously exercising nothing.
        MarshalFactory real = MarshalledInstance.chooseMarshalFactory(
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        assertTrue("jgdms-der must be on mahalo-service's test classpath for this "
                + "in-process simulation of its absence to be meaningful",
                real.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("der"));

        @SuppressWarnings("unchecked")
        Map<String, MarshalFactoryProvider> saved =
                (Map<String, MarshalFactoryProvider>) providersField.get(null);
        providersField.set(null, Collections.emptyMap());
        try {
            body.run();
        } finally {
            providersField.set(null, saved);
        }
    }

    // -----------------------------------------------------------------------
    // Test fixture -- a minimal @AtomicSerial Entry, DER-encodable per the
    // repository's ObjectCodec contract (custom classes need serialForm() /
    // serialize(PutArg,T) / a (GetArg) constructor; there is no
    // arbitrary-Serializable-via-reflection fallback).
    // -----------------------------------------------------------------------

    @AtomicSerial
    public static final class TestAttr implements Entry {
        private static final long serialVersionUID = 1L;
        private static final String LABEL = "label";
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm(LABEL, String.class),
                new SerialForm(VALUE, Integer.class)
            };
        }

        public static void serialize(PutArg arg, TestAttr a) throws IOException {
            arg.put(LABEL, a.label);
            arg.put(VALUE, a.value);
            arg.writeArgs();
        }

        public String label;
        public Integer value;

        /** Entry convention: public no-arg constructor. */
        public TestAttr() {
        }

        public TestAttr(String label, Integer value) {
            this.label = label;
            this.value = value;
        }

        public TestAttr(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(LABEL, null, String.class), arg.get(VALUE, null, Integer.class));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestAttr)) return false;
            TestAttr other = (TestAttr) o;
            return Objects.equals(label, other.label) && Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, value);
        }

        @Override
        public String toString() {
            return "TestAttr[" + label + "=" + value + "]";
        }
    }
}
