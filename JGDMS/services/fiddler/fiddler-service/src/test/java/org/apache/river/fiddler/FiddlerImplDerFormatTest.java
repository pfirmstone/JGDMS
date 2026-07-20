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
package org.apache.river.fiddler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;

import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test coverage for the 4 {@code AtomicMarshalledInstance}-&gt;DER write
 * sites converted in {@link FiddlerImpl} by commit 221fe251c ("persist:
 * convert 19 AtomicMarshalledInstance write sites to DER"). Fiddler-service
 * had zero committed test sources before this file (the commit message
 * explicitly calls this out as a pre-existing gap; the 19-site migration was
 * previously verified only by manual round-trip probes, not by any
 * automated test in this repo).
 *
 * <p>The 4 sites, all in {@link FiddlerImpl}, each construct the
 * registration's remote-listener field (or, for site 2, a discovered
 * registrar; for site 4, a service attribute) as
 * {@code new MarshalledInstance(x, Collections.EMPTY_SET,
 * new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))} in place
 * of the removed {@code AtomicMarshalledInstance(x)}:
 * <ol>
 *   <li>{@code RegistrationInfo.serialize(PutArg, RegistrationInfo)} --
 *       the {@code "listener"} field, reached via the @AtomicSerial
 *       {@code serialize}/{@code GetArg}-constructor pair (exercised here
 *       through {@link AtomicMarshalOutputStream} / {@link
 *       org.apache.river.api.io.AtomicMarshalInputStream}, the same streams
 *       {@code AtomicExternalRoundTripTest} uses to drive
 *       {@code serialize(PutArg)}. Both halves are round-tripped for this
 *       site, but the read half requires running this module's tests with
 *       {@code JAVA_HOME} pointed at the SM-capable DirtyChai JDK, not
 *       vanilla JDK 25 -- see the site-1 test comment below for
 *       details.).</li>
 *   <li>{@code RegistrationInfo.addToDiscoveredRegs(Map)} -- marshals a
 *       newly-discovered {@code ServiceRegistrar}, a public instance method
 *       callable directly.</li>
 *   <li>{@code RegistrationInfo.writeObject(ObjectOutputStream)} /
 *       {@code readObject(ObjectInputStream)} -- the transient
 *       {@code listener} field's *plain java.io Serialization* path, which
 *       is the one {@link FiddlerImpl#takeSnapshot} / {@code
 *       recoverSnapshot} actually use for the service's persistent
 *       snapshot ({@code new ObjectOutputStream(out)} /
 *       {@code new ObjectInputStream(in)} -- no AtomicSerial-aware stream
 *       involved at that outer level; {@code writeObject}/{@code
 *       readObject} are standard {@code Serializable} custom-serialization
 *       hooks, always honoured by any {@code ObjectOutputStream}
 *       subclass).</li>
 *   <li>{@code FiddlerImpl.marshalAttributes}/{@code unmarshalAttributes}
 *       -- private static helpers (reached here via reflection, since
 *       neither takes any state from a live {@code FiddlerImpl} instance:
 *       the unused first parameter is a leftover of an older signature).
 *       </li>
 * </ol>
 *
 * <p>Unlike {@code EntryRepDerFormatTest} (reggie-dl), this module does not
 * need an explicit test-scope {@code jgdms-der} dependency: fiddler-service
 * already pulls {@code jgdms-der} in at {@code compile} scope transitively
 * via {@code jgdms-url-integrity -> jgdms-jeri -> jgdms-der}, so the DER
 * {@code MarshalFactoryProvider} is on this module's main runtime classpath
 * unconditionally (verified via {@code mvn dependency:tree}), not just the
 * test classpath.
 *
 * <h2>Testing the widened-catch / graceful-degradation contract</h2>
 * None of the 4 sites' read-side recovery blocks were touched by commit
 * 221fe251c's {@code IllegalStateException} catch-widening (that widening
 * only applied to 5 *other* files' recovery loops -- outrigger/mahalo
 * {@code JoinStateManager}, norm {@code JoinState}, outrigger {@code
 * StorableReference}, mahalo {@code StorableObject}). Every fiddler read
 * site that decodes one of these 4 write sites' payloads already caught
 * {@code Throwable} before this migration ({@code RegistrationInfo.check},
 * {@code RegistrationInfo.readObject}, {@code unmarshalAttributes} --
 * {@code addToDiscoveredRegs} has no matching decode step of its own, see
 * below), so there was nothing to widen here. This suite still exercises
 * that pre-existing {@code catch(Throwable)} against a *genuine*
 * {@code IllegalStateException} (not a stand-in), reproducing exactly the
 * "reader lacks the codec" condition
 * {@code net.jini.io.MarshalledInstance#get} raises it for: {@code
 * factoryForFormat} throws {@code IllegalStateException} when {@code
 * ServiceLoader} finds no {@code MarshalFactoryProvider} for a payload's
 * format. Since {@code jgdms-der} is unconditionally present on this
 * module's own classpath (see above), and {@code MarshalledInstance}
 * rejects (at construction, with a checked {@code UnsupportedConstraintException})
 * any attempt to *write* with a format that has no registered provider --
 * so a hand-rolled bogus {@code MarshallingFormat} can never reach the
 * read side -- the only faithful way to reproduce "a legacy/downgraded
 * reader without jgdms-der decodes real DER bytes" in a single JVM is to
 * write normally (provider present) and then, only for the read step,
 * reflectively remove the DER entry from {@code MarshalledInstance}'s
 * private {@code providers} cache (restored in a {@code finally}). That is
 * exactly the condition the real cross-JVM probe described in 221fe251c's
 * commit message reproduced with a second, jgdms-der-less JVM.
 */
public class FiddlerImplDerFormatTest {

    // ── fixtures ─────────────────────────────────────────────────────────

    /**
     * Minimal @AtomicSerial RemoteEventListener. Both the JOSS
     * (AtomicMarshalOutputStream) and DER marshal paths this test exercises
     * are @AtomicSerial-restricted (plain java.io.Serializable is rejected
     * by both), so this fixture follows this codebase's convention (see
     * e.g. reggie-dl's EntryRepDerFormatTest.Payload).
     */
    @AtomicSerial
    public static final class TestListener implements RemoteEventListener, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String TAG = "tag";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(TAG, String.class) };
        }

        public static void serialize(PutArg arg, TestListener l) throws IOException {
            arg.put(TAG, l.tag);
            arg.writeArgs();
        }

        public final String tag;

        public TestListener(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(TAG, null, String.class));
        }

        public TestListener(String tag) { this.tag = tag; }

        @Override
        public void notify(RemoteEvent event) throws UnknownEventException, RemoteException {
            throw new UnsupportedOperationException("not invoked by this test");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestListener && tag.equals(((TestListener) o).tag);
        }

        @Override
        public int hashCode() { return tag.hashCode(); }
    }

    /**
     * Minimal @AtomicSerial ServiceRegistrar double for exercising
     * {@code RegistrationInfo.addToDiscoveredRegs}. Only {@link #getServiceID()}
     * and identity (equals/hashCode by ServiceID) are meaningful here; every
     * other interface method is unused by the marshal/unmarshal path under
     * test and throws.
     */
    @AtomicSerial
    public static final class TestRegistrar implements ServiceRegistrar, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ID = "id";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(ID, ServiceID.class) };
        }

        public static void serialize(PutArg arg, TestRegistrar r) throws IOException {
            arg.put(ID, r.id);
            arg.writeArgs();
        }

        public final ServiceID id;

        public TestRegistrar(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(ID, null, ServiceID.class));
        }

        public TestRegistrar(ServiceID id) { this.id = id; }

        @Override public ServiceID getServiceID() { return id; }

        @Override public ServiceRegistration register(ServiceItem item, long leaseDuration) {
            throw new UnsupportedOperationException();
        }
        @Override public Object lookup(ServiceTemplate tmpl) {
            throw new UnsupportedOperationException();
        }
        @Override public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) {
            throw new UnsupportedOperationException();
        }
        @SuppressWarnings("deprecation")
        @Override public EventRegistration notify(ServiceTemplate tmpl, int transitions,
                RemoteEventListener listener, java.rmi.MarshalledObject handback, long leaseDuration) {
            throw new UnsupportedOperationException();
        }
        @Override public Class[] getEntryClasses(ServiceTemplate tmpl) {
            throw new UnsupportedOperationException();
        }
        @Override public Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field) {
            throw new UnsupportedOperationException();
        }
        @Override public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) {
            throw new UnsupportedOperationException();
        }
        @Override public LookupLocator getLocator() {
            throw new UnsupportedOperationException();
        }
        @Override public String[] getGroups() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestRegistrar && id.equals(((TestRegistrar) o).id);
        }
        @Override
        public int hashCode() { return id.hashCode(); }
    }

    /** Minimal @AtomicSerial Entry, for {@code marshalAttributes}/{@code unmarshalAttributes}. */
    @AtomicSerial
    public static final class TestAttr implements Entry, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String VALUE = "value";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(VALUE, String.class) };
        }

        public static void serialize(PutArg arg, TestAttr a) throws IOException {
            arg.put(VALUE, a.value);
            arg.writeArgs();
        }

        public final String value;

        public TestAttr(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(VALUE, null, String.class));
        }

        public TestAttr(String value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestAttr && value.equals(((TestAttr) o).value);
        }
        @Override
        public int hashCode() { return value.hashCode(); }
    }

    // ── reflection helpers ──────────────────────────────────────────────

    private interface ThrowingRunnable { void run() throws Exception; }

    private static String payloadFormatOf(MarshalledInstance mi) throws Exception {
        Field f = MarshalledInstance.class.getDeclaredField("payloadFormat");
        f.setAccessible(true);
        return (String) f.get(mi);
    }

    /**
     * Runs {@code r} with the DER {@code MarshalFactoryProvider} removed from
     * {@link MarshalledInstance}'s private, lazily-loaded {@code providers}
     * cache -- simulating, within a single JVM, exactly the condition a
     * legacy/downgraded reader without {@code jgdms-der} on its classpath
     * would hit when decoding real DER bytes: {@code factoryForFormat}
     * throws {@code IllegalStateException}. The original cache is always
     * restored, even if {@code r} throws.
     */
    private static void withoutDerProvider(ThrowingRunnable r) throws Exception {
        Method providersMethod = MarshalledInstance.class.getDeclaredMethod("providers");
        providersMethod.setAccessible(true);
        Field providersField = MarshalledInstance.class.getDeclaredField("providers");
        providersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> original = (Map<String, Object>) providersMethod.invoke(null);
        assertTrue("DER provider must be registered on this module's classpath "
                + "(jgdms-der is a transitive compile dependency) before this test "
                + "can remove it to simulate a reader that lacks it",
                original.containsKey(MarshallingFormat.ATOMIC_DER.getFormat()));

        Map<String, Object> stripped = new LinkedHashMap<>(original);
        stripped.remove(MarshallingFormat.ATOMIC_DER.getFormat());
        providersField.set(null, stripped);
        try {
            r.run();
        } finally {
            providersField.set(null, original);
        }
    }

    /** Captures LogRecords emitted to FiddlerImpl.problemLogger while {@code r} runs. */
    private static List<LogRecord> captureProblemLog(ThrowingRunnable r) throws Exception {
        final java.util.ArrayList<LogRecord> captured = new java.util.ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { captured.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Level originalLevel = FiddlerImpl.problemLogger.getLevel();
        FiddlerImpl.problemLogger.setLevel(Level.ALL);
        FiddlerImpl.problemLogger.addHandler(handler);
        try {
            r.run();
        } finally {
            FiddlerImpl.problemLogger.removeHandler(handler);
            FiddlerImpl.problemLogger.setLevel(originalLevel);
        }
        return captured;
    }

    private static FiddlerImpl.RegistrationInfo newRegInfo(RemoteEventListener listener) {
        return new FiddlerImpl.RegistrationInfo(
                UuidFactory.generate(),
                new String[0],
                new LookupLocator[0],
                UuidFactory.generate(),
                System.currentTimeMillis() + 60_000L,
                1L,
                null,
                listener);
    }

    // ── Site 1: RegistrationInfo.serialize(PutArg,...) "listener" field ────
    // (reached via the @AtomicSerial serialize/GetArg-constructor pair, the
    // path any AtomicSerial-aware stream -- JOSS or DER -- uses; this is
    // NOT the path FiddlerImpl's own snapshot persistence uses, see site 3.)
    //
    // Both halves are exercised here. Reading this back through
    // AtomicMarshalInputStream requires the SM-capable DirtyChai JDK, not
    // vanilla JDK 25: reconstructing *any* MarshalledInstance via its
    // @AtomicSerial GetArg constructor unconditionally calls
    // MarshalledInstance#check(GetArg), which calls
    // new DeSerializationPermission("MARSHALL").checkGuard(null). On
    // vanilla JDK 25, java.security.Permission#checkGuard now
    // unconditionally throws SecurityException("checking permissions is
    // not supported"), per JEP 486's SecurityManager removal -- confirmed
    // with a standalone java.security.Permission#checkGuard probe outside
    // this module/class entirely, so this is a JDK/platform-level fact,
    // not a fiddler bug. DirtyChai (this codebase's SM-capable JDK build,
    // used by sibling JGDMS modules for exactly this reason) restores the
    // pre-JEP-486 behaviour -- checkGuard is a no-op when no
    // SecurityManager is installed, which is the case here (no SM is
    // installed process-wide by this test) -- so no SM install/uninstall
    // is needed to exercise this path; run this module's tests with
    // JAVA_HOME pointed at the DirtyChai build. RegistrationInfo's *real*
    // persistence path (site 3, below) never hits this at all regardless
    // of JDK: it decodes the nested MarshalledInstance via plain java.io
    // Serialization (writeObject/readObject), not the GetArg constructor.

    @Test
    public void site1_atomicSerializeListenerFieldRoundTrips() throws Exception {
        FiddlerImpl.RegistrationInfo regInfo = newRegInfo(new TestListener("site1"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        // Must not throw: this is the exact line 221fe251c changed --
        // arg.put("listener", ... new MarshalledInstance(r.listener,
        // Collections.EMPTY_SET, new InvocationConstraints(
        // MarshallingFormat.ATOMIC_DER, null))) -- construction requires
        // the DER MarshalFactoryProvider to actually encode the payload
        // (MarshalledInstance#chooseMarshalFactory), so a successful write
        // here already proves the DER codec is reachable and functioning
        // for this write site.
        oos.writeObject(regInfo);
        oos.flush();

        assertTrue("serialize() must produce non-empty output", baos.size() > 0);

        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(baos.toByteArray()), null, false, null, null, false);
        FiddlerImpl.RegistrationInfo restored =
                (FiddlerImpl.RegistrationInfo) ois.readObject();

        assertEquals("listener must round-trip through the @AtomicSerial "
                + "serialize(PutArg)/GetArg-constructor pair",
                new TestListener("site1"), restored.listener);
    }

    // ── Site 2: RegistrationInfo.addToDiscoveredRegs ────────────────────────
    // No matching read-side decode step exists for this site within Fiddler
    // itself: discoveredRegsMap's MarshalledInstance values are handed back
    // to remote clients as-is (see FiddlerImpl#getRegistrars), decoded (or
    // not) by the client -- so there is no local IllegalStateException
    // recovery path to test here, only that the write produces a correctly
    // DER-tagged, decodable instance.

    @Test
    public void site2_addToDiscoveredRegsMarshalsRegistrarAsDerTagged() throws Exception {
        FiddlerImpl.RegistrationInfo regInfo = newRegInfo(null);
        TestRegistrar registrar = new TestRegistrar(new ServiceID(1L, 2L));
        Map<ServiceRegistrar, FiddlerImpl.LocatorGroupsStruct> in = new HashMap<>();
        in.put(registrar, new FiddlerImpl.LocatorGroupsStruct(null, new String[0]));

        Map out = regInfo.addToDiscoveredRegs(in);

        assertEquals(1, out.size());
        assertTrue("registrar must be recorded in discoveredRegsMap",
                regInfo.discoveredRegsMap.containsKey(registrar));
        MarshalledInstance mi = regInfo.discoveredRegsMap.get(registrar);
        assertEquals(MarshallingFormat.ATOMIC_DER.getFormat(), payloadFormatOf(mi));
        assertEquals("the marshalled registrar must decode back to an equal instance",
                registrar, mi.get(false));
    }

    // ── Site 3: RegistrationInfo.writeObject/readObject (plain java.io) ────
    // This is the path FiddlerImpl#takeSnapshot / #recoverSnapshot actually
    // use (plain new ObjectOutputStream(out) / new ObjectInputStream(in)),
    // i.e. this is the persistence format for real.

    @Test
    public void site3_plainWriteObjectListenerFieldIsDerTaggedAndDecodes() throws Exception {
        FiddlerImpl.RegistrationInfo regInfo = newRegInfo(new TestListener("site3"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(regInfo);
        }
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        FiddlerImpl.RegistrationInfo restored = (FiddlerImpl.RegistrationInfo) ois.readObject();

        assertEquals("listener must round-trip through FiddlerImpl's actual "
                + "snapshot serialization mechanism (plain ObjectOutputStream, "
                + "custom writeObject/readObject)",
                new TestListener("site3"), restored.listener);
    }

    @Test
    public void site3_plainReadObjectGracefullyDropsListenerWhenDerProviderMissing() throws Exception {
        FiddlerImpl.RegistrationInfo regInfo = newRegInfo(new TestListener("site3-hostile"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(regInfo);
        }
        final byte[] bytes = baos.toByteArray();

        final FiddlerImpl.RegistrationInfo[] restored = new FiddlerImpl.RegistrationInfo[1];
        List<LogRecord> logged = captureProblemLog(() -> withoutDerProvider(() -> {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            // Must not throw: RegistrationInfo.readObject()'s catch(Throwable)
            // around mi.get(false) must swallow the IllegalStateException --
            // exactly the scenario a real deployment hits recovering an old
            // snapshot after jgdms-der is (hypothetically) removed from the
            // classpath, or a downgraded build reads a newer snapshot.
            restored[0] = (FiddlerImpl.RegistrationInfo) ois.readObject();
        }));

        assertNotNull(restored[0]);
        assertNull("listener must be null, not thrown, when recovery fails",
                restored[0].listener);
        assertFalse(logged.isEmpty());
        assertTrue(logged.stream().anyMatch(r -> r.getThrown() instanceof IllegalStateException));
    }

    // ── Site 4: FiddlerImpl.marshalAttributes / unmarshalAttributes ────────
    // Both private static; reached via reflection (neither uses its unused
    // FiddlerImpl parameter).

    private static Method marshalAttributesMethod() throws Exception {
        Method m = FiddlerImpl.class.getDeclaredMethod(
                "marshalAttributes", FiddlerImpl.class, Entry[].class);
        m.setAccessible(true);
        return m;
    }

    private static Method unmarshalAttributesMethod() throws Exception {
        Method m = FiddlerImpl.class.getDeclaredMethod(
                "unmarshalAttributes", FiddlerImpl.class, Object[].class);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void site4_marshalAttributesProducesDerTaggedDecodableInstances() throws Exception {
        Entry[] attrs = { new TestAttr("a1"), new TestAttr("a2") };
        MarshalledInstance[] marshalled =
                (MarshalledInstance[]) marshalAttributesMethod().invoke(null, (Object) null, attrs);

        assertEquals(2, marshalled.length);
        for (int i = 0; i < marshalled.length; i++) {
            assertEquals(MarshallingFormat.ATOMIC_DER.getFormat(), payloadFormatOf(marshalled[i]));
            assertEquals(attrs[i], marshalled[i].get(false));
        }

        Object[] recovered = (Object[]) unmarshalAttributesMethod()
                .invoke(null, (Object) null, (Object) marshalled);
        assertArrayEquals(attrs, recovered);
    }

    @Test
    public void site4_unmarshalAttributesGracefullyDropsOnlyUndecodableEntriesWhenDerProviderMissing()
            throws Exception {
        // 2 DER-tagged attrs (via marshalAttributes, site 4's write path) +
        // 1 plain-JOSS-tagged attr (format bypasses the provider map
        // entirely, see MarshalledInstance#factoryForFormat) built while the
        // provider is present.
        MarshalledInstance derA = new MarshalledInstance(
                new TestAttr("der-a"), Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        MarshalledInstance derB = new MarshalledInstance(
                new TestAttr("der-b"), Collections.EMPTY_SET,
                new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        MarshalledInstance joss = new MarshalledInstance(new TestAttr("joss-c"), Collections.EMPTY_SET);
        assertEquals(MarshallingFormat.JOSS.getFormat(), payloadFormatOf(joss));

        Object[] marshalled = { derA, derB, joss };
        final Object[][] recovered = new Object[1][];
        List<LogRecord> logged = captureProblemLog(() -> withoutDerProvider(() -> {
            // Must not throw, and must not let the first (or second)
            // undecodable DER entry abort the loop before it reaches the
            // still-decodable JOSS entry -- the per-attribute
            // fault-isolation contract 221fe251c's commit message describes.
            recovered[0] = (Object[]) unmarshalAttributesMethod()
                    .invoke(null, (Object) null, (Object) marshalled);
        }));

        assertEquals("only the JOSS-tagged entry should survive; both "
                + "DER-tagged entries must be dropped gracefully, not thrown",
                1, recovered[0].length);
        assertEquals(new TestAttr("joss-c"), recovered[0][0]);
        assertEquals("both undecodable DER entries must be individually logged",
                2, logged.stream().filter(r -> r.getThrown() instanceof IllegalStateException).count());
    }
}
