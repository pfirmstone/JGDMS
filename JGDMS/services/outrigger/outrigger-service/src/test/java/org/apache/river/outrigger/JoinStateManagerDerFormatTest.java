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
import java.lang.reflect.Method;
import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip and fault-isolation tests for {@link JoinStateManager}'s
 * {@code writeAttributes}/{@code readAttributes} write site -- one of the 19
 * {@code AtomicMarshalledInstance}-&gt;DER write-site conversions from commit
 * 221fe251c ("persist: convert 19 AtomicMarshalledInstance write sites to
 * DER"). That commit switched the per-attribute wire form from the JOSS
 * {@code AtomicMarshalledInstance} to a canonical
 * {@link MarshalledInstance} constructed with
 * {@code InvocationConstraints(MarshallingFormat.ATOMIC_DER, null)}, and
 * widened {@code readAttributes}'s per-attribute recovery loop to also catch
 * {@link IllegalStateException} (thrown unchecked by
 * {@code MarshalledInstance.get()}'s {@code factoryForFormat} when the
 * decoded instance's {@code payloadFormat} has no registered
 * {@code MarshalFactoryProvider} -- e.g. {@code jgdms-der} missing from the
 * runtime classpath) alongside the pre-existing
 * {@code IOException}/{@code ClassNotFoundException} catches, so a single
 * unrecoverable attribute is dropped rather than aborting the whole
 * recovery.
 *
 * <p>{@code writeAttributes}/{@code readAttributes} are {@code private
 * static} methods of {@link JoinStateManager}; this test lives in the same
 * package and invokes them via reflection rather than duplicating their
 * logic, so it exercises the actual production write site.
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml) so {@code MarshalledInstance.get()}'s
 * {@code ServiceLoader}-based {@code MarshalFactoryProvider} dispatch can
 * decode {@code MarshallingFormat.ATOMIC_DER}-tagged payloads.
 */
public class JoinStateManagerDerFormatTest {

    /**
     * Minimal @AtomicSerial attribute payload -- the DER codec
     * (au.net.zeus.jgdms.der) requires @AtomicSerial-declared classes;
     * plain java.io.Serializable is rejected. Follows the same shape as
     * other DER round-trip fixtures in this codebase (e.g. reggie-dl's
     * EntryRepDerFormatTest.Payload).
     */
    @AtomicSerial
    public static class Attr implements Entry {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, Attr a) throws IOException {
            arg.put(VALUE, a.value);
            arg.writeArgs();
        }

        public String value;

        public Attr() {}

        public Attr(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        public Attr(String value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Attr && java.util.Objects.equals(value, ((Attr) o).value);
        }
        @Override
        public int hashCode() { return value == null ? 0 : value.hashCode(); }
    }

    // ── reflection helpers onto JoinStateManager's private static write site ──

    private static void writeAttributes(Entry[] attrs, ObjectOutputStream out) throws Exception {
        Method m = JoinStateManager.class.getDeclaredMethod(
                "writeAttributes", Entry[].class, ObjectOutputStream.class);
        m.setAccessible(true);
        try {
            m.invoke(null, (Object) attrs, out);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static Entry[] readAttributes(ObjectInputStream in) throws Exception {
        Method m = JoinStateManager.class.getDeclaredMethod("readAttributes", ObjectInputStream.class);
        m.setAccessible(true);
        try {
            return (Entry[]) m.invoke(null, in);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static Exception unwrap(java.lang.reflect.InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Exception) return (Exception) cause;
        throw new AssertionError(cause);
    }

    private static String payloadFormatOf(MarshalledInstance mi) throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        return (String) f.get(mi);
    }

    private static void setPayloadFormat(MarshalledInstance mi, String format) throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        f.set(mi, format);
    }

    // ── (a) real round-trip through the actual write site ───────────────────

    @Test
    public void roundTripsRealAttributesThroughDerEncoding() throws Exception {
        Entry[] attrs = new Entry[]{ new Attr("one"), new Attr("two"), new Attr("three") };

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        writeAttributes(attrs, oos);
        oos.flush();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
        Entry[] recovered = readAttributes(ois);

        assertArrayEquals("all attributes must round-trip through the DER write site",
            attrs, recovered);
    }

    @Test
    public void writeSiteProducesDerTaggedInstancesNotJoss() throws Exception {
        Entry[] attrs = new Entry[]{ new Attr("hello") };

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        writeAttributes(attrs, oos);
        oos.flush();

        // Read back the raw stream contents (not via readAttributes) to inspect
        // the wire form directly: an int count, then one MarshalledInstance.
        ObjectInputStream raw = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
        int count = raw.readInt();
        assertEquals(1, count);
        Object o = raw.readObject();
        assertTrue("write site must emit a MarshalledInstance", o instanceof MarshalledInstance);
        MarshalledInstance mi = (MarshalledInstance) o;
        assertEquals("write site must tag the ATOMIC_DER payload format",
            "JGDMS-STD-006/ATOMIC-DER", payloadFormatOf(mi));
        assertNotEquals("write site must NOT use the legacy JOSS format",
            MarshalledInstance.FORMAT_JOSS, payloadFormatOf(mi));
    }

    // ── (b) dual-read: legacy JOSS-encoded records still decode ─────────────

    @Test
    public void readAttributesStillDecodesLegacyJossEncodedRecords() throws Exception {
        Attr legacy = new Attr("legacy");
        // Simulate a record persisted by the pre-migration code path: a
        // MarshalledInstance built with no format constraint, which
        // MarshalledInstance's own chooseMarshalFactory(null) resolves to the
        // built-in JOSS factory (see net.jini.io.MarshalledInstance#requiredFormat).
        MarshalledInstance jossMi = new MarshalledInstance(legacy, Collections.EMPTY_SET,
                (InvocationConstraints) null);
        assertEquals(MarshalledInstance.FORMAT_JOSS, payloadFormatOf(jossMi));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeInt(1);
        oos.writeObject(jossMi);
        oos.flush();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
        Entry[] recovered = readAttributes(ois);

        assertArrayEquals("a legacy JOSS-encoded attribute must still be recoverable "
            + "(dual-read upgrade path, no flag-day)", new Entry[]{ legacy }, recovered);
    }

    // ── (c) widened IllegalStateException catch: fault isolation ────────────

    @Test
    public void readAttributesDropsUnresolvableFormatButKeepsOthers() throws Exception {
        Attr good1 = new Attr("good1");
        Attr bad = new Attr("bad");
        Attr good2 = new Attr("good2");

        MarshalledInstance miGood1 = new MarshalledInstance(good1, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        MarshalledInstance miBad = new MarshalledInstance(bad, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        MarshalledInstance miGood2 = new MarshalledInstance(good2, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));

        // Simulate "jgdms-der missing from the classpath" (the scenario the
        // commit's read-side catch-widening targets) by retagging the middle
        // instance's payloadFormat with an identifier no MarshalFactoryProvider
        // is registered for. This drives MarshalledInstance.get()'s
        // factoryForFormat down the exact same "no provider found" branch that
        // throws IllegalStateException when jgdms-der is absent, without
        // requiring an actual second-JVM/no-jgdms-der classpath fork.
        setPayloadFormat(miBad, "BOGUS/NO-SUCH-PROVIDER");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeInt(3);
        oos.writeObject(miGood1);
        oos.writeObject(miBad);
        oos.writeObject(miGood2);
        oos.flush();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
        Entry[] recovered = readAttributes(ois);

        assertArrayEquals("the widened IllegalStateException catch must drop only the "
            + "unresolvable attribute and keep recovering the rest of the array "
            + "(fault-isolation contract of the per-attribute recovery loop)",
            new Entry[]{ good1, good2 }, recovered);
    }

    @Test
    public void unresolvableFormatThrowsIllegalStateExceptionDirectlyFromGet() throws Exception {
        // Sanity check underpinning the test above: confirm that decoding a
        // MarshalledInstance whose payloadFormat has no registered provider
        // really does throw IllegalStateException (not some other exception
        // type) from MarshalledInstance.get() -- i.e. that readAttributes's
        // extra catch clause is reachable and not dead code.
        Attr a = new Attr("x");
        MarshalledInstance mi = new MarshalledInstance(a, Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        setPayloadFormat(mi, "BOGUS/NO-SUCH-PROVIDER");
        try {
            mi.get(false);
            fail("expected IllegalStateException for an unresolvable payloadFormat");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("BOGUS/NO-SUCH-PROVIDER"));
        }
    }
}
