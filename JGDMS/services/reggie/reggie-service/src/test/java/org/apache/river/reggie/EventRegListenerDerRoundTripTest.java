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
package org.apache.river.reggie;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collections;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalFactoryProvider;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip and fault-isolation tests for the reggie-service write site
 * converted by 221fe251c ("persist: convert 19 AtomicMarshalledInstance
 * write sites to DER"): {@link RegistrarImpl.EventReg}'s {@code listener}
 * field, which is now wrapped in a {@link MarshalledInstance} constructed
 * with {@code InvocationConstraints(MarshallingFormat.ATOMIC_DER, null)}
 * instead of the legacy {@code org.apache.river.api.io.AtomicMarshalledInstance}
 * (JOSS).
 *
 * <p>{@code EventReg} has two independent write sites that both construct the
 * listener's {@code MarshalledInstance} identically, and this class exercises
 * both:
 * <ul>
 * <li>{@code EventReg.serialize(PutArg, EventReg)} / {@code EventReg(GetArg)}
 *     -- the {@code @AtomicSerial} pair, driven here via
 *     {@link AtomicMarshalOutputStream}/{@link AtomicMarshalInputStream}.
 * <li>{@code EventReg.writeObject(ObjectOutputStream)} /
 *     {@code readObject(ObjectInputStream)} -- the classic
 *     {@code java.io.Serializable} pair, driven here via plain
 *     {@link ObjectOutputStream}/{@link ObjectInputStream}. This is the pair
 *     actually exercised by {@code RegistrarImpl.takeSnapshot}, which persists
 *     {@code eventByID} values (i.e. {@code EventReg} records) through a bare
 *     {@code new ObjectOutputStream(out)} -- so this is the live production
 *     persistence path for event registrations.
 * </ul>
 *
 * <p>These tests live in the {@code org.apache.river.reggie} package because
 * {@link RegistrarImpl} and its nested {@code EventReg} class are
 * package-private.
 *
 * <p>Requires {@code jgdms-der} on the test runtime classpath (declared
 * test-scope in this module's pom.xml) so that {@code MarshalledInstance
 * .get()}'s {@code ServiceLoader}-based {@code MarshalFactoryProvider}
 * dispatch can actually decode {@code MarshallingFormat.ATOMIC_DER}-tagged
 * payloads.
 *
 * <p>Scoping note: {@code EventReg.tmpl} (type {@code Template}, from
 * reggie-dl) is left {@code null} in every fixture built here. {@code
 * Template} is {@code @AtomicSerial} but does NOT implement {@code
 * java.io.Serializable}, so a non-null {@code tmpl} would break the classic
 * {@code writeObject}'s {@code stream.defaultWriteObject()} call with a
 * {@code NotSerializableException} that is entirely unrelated to the DER
 * migration under test here (pre-existing gap, not introduced by 221fe251c
 * and not touched by these tests -- these tests isolate the listener/DER
 * write site specifically).
 *
 * <p>Environment note: the {@code atomicSerial*} tests reconstruct the
 * listener's {@code MarshalledInstance} via its {@code GetArg} constructor
 * (through {@code AtomicMarshalInputStream}), which calls {@code
 * DeSerializationPermission("MARSHALL").checkGuard(null)}. That is a no-op
 * with no {@code SecurityManager} installed on an SM-capable JDK, but on a
 * JDK build with SecurityManager support fully stripped (e.g. a vanilla
 * distro OpenJDK 25) {@code java.security.Permission.checkGuard}'s default
 * implementation throws {@code SecurityException} unconditionally instead.
 * Run these tests under an SM-capable JDK (e.g. the DirtyChai build) with
 * {@code -Djava.security.manager=allow} -- already baked into this module's
 * pom.xml surefire {@code argLine}, matching jgdms-platform's convention.
 */
public class EventRegListenerDerRoundTripTest {

    /**
     * Minimal @AtomicSerial RemoteEventListener double. Both the JOSS
     * (AtomicMarshalOutputStream) and DER (DerObjectStreamCodec) marshal
     * paths a MarshalledInstance may choose are @AtomicSerial-restricted
     * once a MarshallingFormat is required (plain java.io.Serializable
     * classes are rejected by the DER codec), so the fixture must be a
     * properly-declared @AtomicSerial type, following this codebase's
     * convention (see reggie-dl's EntryRepDerFormatTest.Payload).
     */
    @AtomicSerial
    public static class TestListener implements RemoteEventListener, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String NAME = "name";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(NAME, String.class) };
        }

        public static void serialize(PutArg arg, TestListener t) throws IOException {
            arg.put(NAME, t.name);
            arg.writeArgs();
        }

        public final String name;

        public TestListener(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(NAME, null, String.class));
        }

        public TestListener(String name) { this.name = name; }

        @Override
        public void notify(RemoteEvent theEvent) { /* not exercised by these tests */ }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestListener && name.equals(((TestListener) o).name);
        }

        @Override
        public int hashCode() { return name.hashCode(); }
    }

    private static RegistrarImpl.EventReg freshEventReg() {
        return new RegistrarImpl.EventReg(
                42L,
                UuidFactory.generate(),
                null,                          // tmpl -- see class javadoc scoping note
                7,
                new TestListener("listener-under-test"),
                "handback-value",
                System.currentTimeMillis() + 60_000L,
                false);
    }

    /** Reflective peek at MarshalledInstance's private payloadFormat field. */
    private static String payloadFormatOf(MarshalledInstance mi) throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        return (String) f.get(mi);
    }

    // ── write site 1: classic writeObject/readObject (the live takeSnapshot path) ──

    @Test
    public void classicSerializationRoundTripsListener() throws Exception {
        RegistrarImpl.EventReg original = freshEventReg();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        RegistrarImpl.EventReg recovered;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            recovered = (RegistrarImpl.EventReg) ois.readObject();
        }

        assertNotNull("listener must have been recovered, not dropped",
                recovered.listener);
        assertEquals("classic writeObject/readObject must round-trip the listener",
                original.listener, recovered.listener);
        assertEquals(original.eventID, recovered.eventID);
        assertEquals(original.leaseID, recovered.leaseID);
        assertEquals(original.transitions, recovered.transitions);
        assertEquals(original.handback, recovered.handback);
    }

    // ── write site 2: @AtomicSerial serialize(PutArg,...)/EventReg(GetArg) ──

    @Test
    public void atomicSerialRoundTripsListener() throws Exception {
        RegistrarImpl.EventReg original = freshEventReg();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(original);
        oos.flush();

        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(baos.toByteArray()), null, false, null, null, false);
        RegistrarImpl.EventReg recovered = (RegistrarImpl.EventReg) ois.readObject();

        assertNotNull("listener must have been recovered, not dropped",
                recovered.listener);
        assertEquals("@AtomicSerial serialize(PutArg,...)/EventReg(GetArg) must round-trip the listener",
                original.listener, recovered.listener);
        assertEquals(original.eventID, recovered.eventID);
        assertEquals(original.leaseID, recovered.leaseID);
        assertEquals(original.transitions, recovered.transitions);
        assertEquals(original.handback, recovered.handback);
    }

    // ── mechanism check: the exact MarshalledInstance construction both write
    //    sites use (`new MarshalledInstance(listener, EMPTY_SET,
    //    new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))`)
    //    really is DER-tagged (not JOSS) and survives its own byte round-trip. ──

    @Test
    public void listenerMarshalledInstanceIsDerTaggedAndRoundTrips() throws Exception {
        TestListener listener = new TestListener("mechanism-check");
        MarshalledInstance mi = new MarshalledInstance(listener,
                Collections.EMPTY_SET,
                new net.jini.core.constraint.InvocationConstraints(
                        net.jini.core.constraint.MarshallingFormat.ATOMIC_DER, null));

        assertEquals("write site must tag the listener's MarshalledInstance as ATOMIC_DER",
                "JGDMS-STD-006/ATOMIC-DER", payloadFormatOf(mi));
        assertFalse("write site must NOT use the legacy JOSS format",
                MarshalledInstance.FORMAT_JOSS.equals(payloadFormatOf(mi)));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(mi);
        }
        MarshalledInstance recoveredMi;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            recoveredMi = (MarshalledInstance) ois.readObject();
        }
        assertEquals("JGDMS-STD-006/ATOMIC-DER", payloadFormatOf(recoveredMi));
        assertEquals("a DER-capable reader must decode the DER-tagged payload back "
                + "to an equivalent listener",
                listener, recoveredMi.get(false));
    }

    // ── graceful degradation: a legacy JOSS-only reader (no DER provider resolvable)
    //    must not crash on DER-tagged data -- the IllegalStateException from
    //    MarshalledInstance.get()'s factoryForFormat must be swallowed by EventReg's
    //    existing catch(Throwable) recovery, same contract 221fe251c's commit message
    //    documents for the sibling mahalo/outrigger/norm read-side loops. ──

    private Object savedProviders;
    private boolean providersSaved;

    @After
    public void restoreProviders() throws Exception {
        if (providersSaved) {
            Field f = MarshalledInstance.class.getDeclaredField("providers");
            f.setAccessible(true);
            f.set(null, savedProviders);
            providersSaved = false;
        }
    }

    /**
     * Simulates "jgdms-der missing from the runtime classpath" in-process by
     * temporarily emptying MarshalledInstance's lazily-loaded, ServiceLoader
     * -populated provider cache. After this call, factoryForFormat(ATOMIC_DER)
     * finds no registered MarshalFactoryProvider and throws IllegalStateException
     * -- exactly the failure mode the 221fe251c catch-widening exists to handle.
     * Restored automatically in an @After hook so later tests still see the real
     * jgdms-der provider.
     */
    @SuppressWarnings("unchecked")
    private void makeDerProviderUnresolvable() throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("providers");
        f.setAccessible(true);
        savedProviders = f.get(null);
        providersSaved = true;
        f.set(null, Collections.<String, MarshalFactoryProvider>emptyMap());
    }

    @Test
    public void classicReadObjectGracefullyDropsListenerWhenDerUnresolvable() throws Exception {
        RegistrarImpl.EventReg original = freshEventReg();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        makeDerProviderUnresolvable();

        RegistrarImpl.EventReg recovered;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            recovered = (RegistrarImpl.EventReg) ois.readObject();
        }

        assertNull("readObject's catch(Throwable) must drop the listener, not "
                + "propagate the IllegalStateException from an unresolvable "
                + "MarshallingFormat.ATOMIC_DER provider",
                recovered.listener);
        // The rest of the record must survive intact -- only the listener is dropped.
        assertEquals(original.eventID, recovered.eventID);
        assertEquals(original.leaseID, recovered.leaseID);
    }

    /**
     * FINDING (not a bug in 221fe251c's DER change itself, but a pre-existing
     * gap it inherited): {@code EventReg.readListener(GetArg)} DOES catch the
     * {@code IllegalStateException} from an unresolvable
     * {@code MarshallingFormat.ATOMIC_DER} provider and returns {@code null}
     * for the listener, exactly like the classic {@code readObject} path does
     * (see {@link #classicReadObjectGracefullyDropsListenerWhenDerUnresolvable()}).
     * But unlike the classic path -- where {@code listener} is a plain field
     * assignment inside an already-allocated instance -- the {@code GetArg}
     * constructor pipeline feeds that {@code null} straight into {@code
     * EventReg}'s simple constructor (RegistrarImpl.java:944), which
     * unconditionally throws {@code NullPointerException("Listener cannot be
     * null")}. So for this write site's AtomicSerial/GetArg read path, the
     * "widened catch" recovers the exception internally but the record-level
     * null-listener invariant check immediately re-fails construction anyway
     * -- the graceful per-record drop-and-continue behaviour 221fe251c's
     * commit message describes for the sibling mahalo/outrigger/norm loops is
     * NOT actually achieved here. This test pins down and documents that
     * current (buggy) behaviour rather than silently asserting the intended
     * one; see the task report for the corresponding follow-up recommendation.
     */
    @Test
    public void atomicSerialReadThrowsInsteadOfGracefullyDroppingListenerWhenDerUnresolvable()
            throws Exception {
        RegistrarImpl.EventReg original = freshEventReg();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(original);
        oos.flush();

        makeDerProviderUnresolvable();

        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(baos.toByteArray()), null, false, null, null, false);
        try {
            ois.readObject();
            fail("expected NullPointerException: readListener()'s graceful null "
                    + "is rejected by EventReg's own listener-non-null constructor "
                    + "guard, defeating the catch-widening for this write site's "
                    + "GetArg-driven read path -- see this test's javadoc");
        } catch (NullPointerException expected) {
            assertEquals("Listener cannot be null", expected.getMessage());
        }
    }
}
