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
package org.apache.river.norm.lookup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Objects;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.norm.DerFormatTestSupport;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for {@link JoinState}'s attribute persistence write site
 * (the {@code writeAttributes}/{@code readAttributes} pair converted from
 * {@code AtomicMarshalledInstance} (JOSS) to
 * {@code MarshallingFormat.ATOMIC_DER} by commit 221fe251c), and for the
 * {@code IllegalStateException} catch that commit added to
 * {@code readAttributes}'s per-attribute recovery loop.
 *
 * <p>{@code writeAttributes}/{@code readAttributes} are {@code private static}
 * methods -- accessed here via {@link DerFormatTestSupport#invokePrivateStatic}
 * rather than duplicating their logic, so these tests exercise the actual
 * production write/read code, not a reimplementation of it.
 */
public class JoinStateAttributesDerFormatTest {

    /** Minimal @AtomicSerial Entry fixture -- both the JOSS and DER codecs
     *  MarshalledInstance can select are @AtomicSerial-restricted, so the
     *  attribute payload must be a properly-declared @AtomicSerial type
     *  (same requirement documented in reggie-dl's EntryRepDerFormatTest). */
    @AtomicSerial
    public static class TestAttr implements Entry {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, TestAttr o) throws IOException {
            arg.put(VALUE, o.value);
            arg.writeArgs();
        }

        public String value;

        public TestAttr() { }
        public TestAttr(String value) { this.value = value; }

        public TestAttr(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestAttr && Objects.equals(value, ((TestAttr) o).value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    private static byte[] writeAttributes(Entry[] attrs) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        DerFormatTestSupport.invokePrivateStatic(JoinState.class, "writeAttributes",
            new Class<?>[]{ Entry[].class, ObjectOutputStream.class },
            (Object) attrs, oos);
        oos.flush();
        return bout.toByteArray();
    }

    private static Entry[] readAttributes(byte[] bytes) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (Entry[]) DerFormatTestSupport.invokePrivateStatic(JoinState.class, "readAttributes",
            new Class<?>[]{ ObjectInputStream.class }, ois);
    }

    // ── (a) the write site actually uses ATOMIC_DER, and decodes correctly ──

    @Test
    public void writeAttributesTagsEachEntryWithAtomicDerFormat() throws Exception {
        byte[] bytes = writeAttributes(new Entry[]{ new TestAttr("a") });

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        assertEquals(1, ois.readInt());
        Object written = ois.readObject();

        assertTrue("JoinState.writeAttributes must write a MarshalledInstance",
            written instanceof MarshalledInstance);
        assertEquals("JoinState.writeAttributes must use MarshallingFormat.ATOMIC_DER "
            + "(not the legacy JOSS default)",
            MarshallingFormat.ATOMIC_DER.getFormat(),
            DerFormatTestSupport.payloadFormatOf((MarshalledInstance) written));
    }

    @Test
    public void writeThenReadRoundTripsRealPersistedAttributes() throws Exception {
        Entry[] attrs = { new TestAttr("a"), new TestAttr("b"), new TestAttr("c") };

        byte[] bytes = writeAttributes(attrs);
        Entry[] recovered = readAttributes(bytes);

        assertArrayEquals("DER-encoded attributes must decode back to the "
            + "original values via JoinState's own readAttributes()",
            attrs, recovered);
    }

    // ── (b) the widened IllegalStateException catch in readAttributes ───────

    @Test
    public void readAttributesDropsUnresolvableFormatEntryButKeepsTheRest() throws Exception {
        // "good": a real ATOMIC_DER-tagged instance that decodes normally.
        MarshalledInstance good = new MarshalledInstance(new TestAttr("keep"),
            Collections.EMPTY_SET, new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        // "bad": also a real, successfully-constructed ATOMIC_DER instance, then
        // tampered post-construction so its payloadFormat resolves to no
        // registered MarshalFactoryProvider -- the same failure
        // MarshalledInstance.get() hits if jgdms-der is missing from the
        // runtime classpath (see DerFormatTestSupport javadoc).
        MarshalledInstance bad = new MarshalledInstance(new TestAttr("drop"),
            Collections.EMPTY_SET, new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        DerFormatTestSupport.makeFormatUnresolvable(bad);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeInt(2);
        oos.writeObject(good);
        oos.writeObject(bad);
        oos.flush();

        Entry[] recovered = readAttributes(bout.toByteArray());

        assertEquals("the IllegalStateException from the unresolvable-format entry "
            + "must be caught and that entry dropped, without losing the other "
            + "entry in the same snapshot",
            1, recovered.length);
        assertEquals(new TestAttr("keep"), recovered[0]);
    }

    @Test
    public void readAttributesToleratesEveryEntryBeingUnresolvable() throws Exception {
        MarshalledInstance bad1 = new MarshalledInstance(new TestAttr("x"),
            Collections.EMPTY_SET, new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        MarshalledInstance bad2 = new MarshalledInstance(new TestAttr("y"),
            Collections.EMPTY_SET, new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        DerFormatTestSupport.makeFormatUnresolvable(bad1);
        DerFormatTestSupport.makeFormatUnresolvable(bad2);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeInt(2);
        oos.writeObject(bad1);
        oos.writeObject(bad2);
        oos.flush();

        // Must not throw -- the whole recovery loop degrades gracefully to an
        // empty (but non-null) result, exactly the fault-isolation contract
        // documented on JoinState.readAttributes.
        Entry[] recovered = readAttributes(bout.toByteArray());
        assertEquals(0, recovered.length);
    }
}
