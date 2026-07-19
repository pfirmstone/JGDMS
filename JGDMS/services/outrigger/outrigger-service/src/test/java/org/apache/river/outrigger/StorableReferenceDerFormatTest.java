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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip and fault-isolation tests for {@link StorableReference}'s
 * {@code writeExternal}/{@code readExternal}/{@code get} write site -- one of
 * the 19 {@code AtomicMarshalledInstance}-&gt;DER write-site conversions from
 * commit 221fe251c ("persist: convert 19 AtomicMarshalledInstance write
 * sites to DER"). That commit switched the wire form from the JOSS
 * {@code AtomicMarshalledInstance} to a canonical {@link MarshalledInstance}
 * constructed with {@code InvocationConstraints(MarshallingFormat.ATOMIC_DER,
 * null)}.
 *
 * <p>Unlike the other 4 read-side recovery loops touched by that commit,
 * {@link StorableReference} wraps a single, non-substitutable reference
 * rather than an array, so it doesn't fit the per-record catch-and-drop
 * shape; instead {@link StorableReference#get} converts a caught
 * {@link IllegalStateException} into the method's own already-documented
 * checked {@link IOException}, which this test verifies directly.
 *
 * <p>{@link StorableReference} is package-private but its constructor,
 * {@code writeExternal}, {@code readExternal}, and {@code get} methods are
 * public, so this test (living in the same package) exercises the real
 * production code with no reflection needed for the class under test
 * (reflection is used only to reach into {@link MarshalledInstance}'s
 * private {@code payloadFormat} field to simulate an unresolvable format).
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml) so {@code MarshalledInstance.get()}'s
 * {@code ServiceLoader}-based {@code MarshalFactoryProvider} dispatch can
 * decode {@code MarshallingFormat.ATOMIC_DER}-tagged payloads.
 */
public class StorableReferenceDerFormatTest {

    /**
     * Minimal @AtomicSerial stand-in for the remote proxy a
     * StorableReference normally wraps -- the DER codec (au.net.zeus.jgdms.der)
     * requires @AtomicSerial-declared classes; plain java.io.Serializable is
     * rejected.
     */
    @AtomicSerial
    public static class Ref implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ID = "id";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(ID, String.class) };
        }

        public static void serialize(PutArg arg, Ref r) throws IOException {
            arg.put(ID, r.id);
            arg.writeArgs();
        }

        public final String id;

        public Ref(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(ID, null, String.class));
        }

        public Ref(String id) { this.id = id; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ref && java.util.Objects.equals(id, ((Ref) o).id);
        }
        @Override
        public int hashCode() { return id == null ? 0 : id.hashCode(); }
    }

    private static Field payloadFormatField() throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        return f;
    }

    private static String payloadFormatOf(MarshalledInstance mi) throws Exception {
        return (String) payloadFormatField().get(mi);
    }

    private static void setPayloadFormat(MarshalledInstance mi, String format) throws Exception {
        payloadFormatField().set(mi, format);
    }

    private static Field instanceField() throws Exception {
        Field f = StorableReference.class.getDeclaredField("instance");
        f.setAccessible(true);
        return f;
    }

    private static byte[] externalize(StorableReference ref) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        ref.writeExternal(oos);
        oos.flush();
        return bout.toByteArray();
    }

    private static StorableReference deExternalize(byte[] bytes) throws IOException, ClassNotFoundException {
        StorableReference ref = new StorableReference();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        ref.readExternal(ois);
        return ref;
    }

    // ── (a) real round-trip through the actual write site ───────────────────

    @Test
    public void roundTripsRealReferenceThroughDerEncoding() throws Exception {
        Ref original = new Ref("proxy-123");
        StorableReference stored = new StorableReference(original);

        byte[] bytes = externalize(stored);
        StorableReference restored = deExternalize(bytes);

        Object decoded = restored.get(null);
        assertEquals("the stored reference must round-trip through the DER write site",
            original, decoded);
    }

    @Test
    public void writeSiteProducesDerTaggedInstanceNotJoss() throws Exception {
        StorableReference stored = new StorableReference(new Ref("proxy-456"));
        externalize(stored); // populates the private `instance` field as a side effect

        MarshalledInstance mi = (MarshalledInstance) instanceField().get(stored);
        assertEquals("write site must tag the ATOMIC_DER payload format",
            "JGDMS-STD-006/ATOMIC-DER", payloadFormatOf(mi));
        assertNotEquals("write site must NOT use the legacy JOSS format",
            MarshalledInstance.FORMAT_JOSS, payloadFormatOf(mi));
    }

    // ── (b) dual-read: a legacy JOSS-encoded record still decodes ───────────

    @Test
    public void getStillDecodesLegacyJossEncodedRecord() throws Exception {
        Ref legacy = new Ref("legacy-proxy");
        // Simulate a record persisted by the pre-migration code path: a
        // MarshalledInstance built with no format constraint, which
        // MarshalledInstance's own chooseMarshalFactory(null) resolves to the
        // built-in JOSS factory.
        MarshalledInstance jossMi = new MarshalledInstance(legacy, Collections.EMPTY_SET,
                (InvocationConstraints) null);
        assertEquals(MarshalledInstance.FORMAT_JOSS, payloadFormatOf(jossMi));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeObject(jossMi);
        oos.flush();

        StorableReference restored = deExternalize(bout.toByteArray());
        Object decoded = restored.get(null);

        assertEquals("a legacy JOSS-encoded reference must still be recoverable "
            + "(dual-read upgrade path, no flag-day)", legacy, decoded);
    }

    // ── (c) widened IllegalStateException -> documented IOException ─────────

    @Test
    public void getWrapsUnresolvableFormatAsIOExceptionNotIllegalStateException() throws Exception {
        Ref ref = new Ref("undecodable");
        MarshalledInstance mi = new MarshalledInstance(ref, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        // Simulate "jgdms-der missing from the classpath" (the scenario the
        // commit's read-side catch-widening targets) by retagging the
        // instance's payloadFormat with an identifier no
        // MarshalFactoryProvider is registered for. This drives
        // MarshalledInstance.get()'s factoryForFormat down the exact same
        // "no provider found" branch that throws IllegalStateException when
        // jgdms-der is absent, without requiring an actual second-JVM/no-
        // jgdms-der classpath fork.
        setPayloadFormat(mi, "BOGUS/NO-SUCH-PROVIDER");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeObject(mi);
        oos.flush();

        StorableReference restored = deExternalize(bout.toByteArray());
        try {
            restored.get(null);
            fail("expected get() to surface the unresolvable payload format as an IOException");
        } catch (IllegalStateException notExpected) {
            fail("StorableReference.get() must not let the raw IllegalStateException "
                + "escape -- it must be caught and rethrown as the method's own "
                + "documented checked IOException. Got: " + notExpected);
        } catch (IOException expected) {
            assertTrue("the widened catch must preserve the original IllegalStateException "
                + "as the IOException's cause",
                expected.getCause() instanceof IllegalStateException);
            assertTrue(expected.getCause().getMessage().contains("BOGUS/NO-SUCH-PROVIDER"));
        }
    }
}
