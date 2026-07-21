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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.Remote;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.outrigger.proxy.EntryRep;
import org.apache.river.outrigger.proxy.OutriggerServer;
import org.apache.river.outrigger.proxy.StorableObject;
import org.apache.river.outrigger.proxy.StorableResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Registration-time handback format check
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} decision 5 / sec.3
 * item 5), exercised against a real booted {@code PersistentOutriggerImpl}
 * with a recording store: a {@link MarshalledInstance} handback whose
 * payload format is not ATOMIC_DER is loudly rejected, and -- the
 * placement pin -- a rejected registration leaves <em>no lease, no
 * watcher, no log record</em>. Null handbacks and null-content
 * MarshalledInstances (no payload at all) are accepted, as are
 * constraint-built DER handbacks.
 */
public class HandbackFormatRejectTest {

    private static final String DER = MarshallingFormat.ATOMIC_DER.getFormat();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Public entry type for real EntryRep templates. */
    public static class TestEntry implements Entry {
        private static final long serialVersionUID = 1L;
        public String value;
        public TestEntry() {}
    }

    // ---- fixtures handed to the server through the configuration file ----

    /** No-op LogOps that counts durable registration records. */
    public static final class RecordingLogOps implements LogOps {
        final AtomicInteger registerOps = new AtomicInteger();

        public void bootOp(long time, long sessionId) { }
        public void joinStateOp(StorableObject state) { }
        public void writeOp(StorableResource entry, Long txnId) { }
        public void writeOp(StorableResource[] entries, Long txnId) { }
        public void takeOp(Uuid cookie, Long txnId) { }
        public void takeOp(Uuid[] cookies, Long txnId) { }
        public void registerOp(StorableResource registration, String type,
                StorableObject[] templates) {
            registerOps.incrementAndGet();
        }
        public void renewOp(Uuid cookie, long expiration) { }
        public void cancelOp(Uuid cookie, boolean expired) { }
        public void prepareOp(Long txnId, StorableObject transaction) { }
        public void commitOp(Long txnId) { }
        public void abortOp(Long txnId) { }
        public void uuidOp(Uuid uuid) { }
    }

    /** An empty (fresh, DER-born) store returning the recording LogOps. */
    public static final class EmptyRecordingStore implements Store {
        final RecordingLogOps log = new RecordingLogOps();

        public LogOps setupStore(Recover space, String entryFormat)
                throws IOException {
            assertEquals(DER, entryFormat);
            return log; // empty store: nothing to recover, no guard fires
        }
        public void destroy() { }
        public void close() { }
    }

    /** In-VM exporter: hands back a constrainable OutriggerServer proxy. */
    public static final class FakeExporter implements Exporter {
        public Remote export(Remote impl) {
            InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    switch (method.getName()) {
                        case "setConstraints": return proxy;
                        case "getConstraints": return null;
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "FakeExportedProxy";
                        default: {
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt.isPrimitive() && rt != void.class) return 0;
                            return null;
                        }
                    }
                }
            };
            return (Remote) Proxy.newProxyInstance(
                HandbackFormatRejectTest.class.getClassLoader(),
                new Class<?>[]{OutriggerServer.class, RemoteMethodControl.class},
                handler);
        }
        public boolean unexport(boolean force) { return true; }
    }

    /**
     * Inert discovery manager: keeps the boot path off the network and off
     * the multicast discovery provider stack (not on this module's test
     * classpath). Initially member of no groups with no locators, as
     * JoinStateManager.startManager requires.
     */
    public static final class FakeDiscoveryManager implements
            DiscoveryManagement, DiscoveryGroupManagement,
            DiscoveryLocatorManagement {
        public void addDiscoveryListener(DiscoveryListener l) { }
        public void removeDiscoveryListener(DiscoveryListener l) { }
        public ServiceRegistrar[] getRegistrars() {
            return new ServiceRegistrar[0];
        }
        public void discard(ServiceRegistrar proxy) { }
        public void terminate() { }
        public String[] getGroups() { return new String[0]; }
        public void setGroups(String[] groups) { }
        public void addGroups(String[] groups) { }
        public void removeGroups(String[] groups) { }
        public LookupLocator[] getLocators() { return new LookupLocator[0]; }
        public void setLocators(LookupLocator[] locators) { }
        public void addLocators(LookupLocator[] locators) { }
        public void removeLocators(LookupLocator[] locators) { }
    }

    private static volatile EmptyRecordingStore store;
    private static volatile FakeExporter exporter;

    /** Called from the configuration file. */
    public static Store testStore() { return store; }

    /** Called from the configuration file. */
    public static Exporter testExporter() { return exporter; }

    /** Called from the configuration file. */
    public static FakeDiscoveryManager testDiscoveryManager() {
        return new FakeDiscoveryManager();
    }

    private PersistentOutriggerImpl wrapper;

    @Before
    public void boot() throws Exception {
        store = new EmptyRecordingStore();
        exporter = new FakeExporter();
        File f = tmp.newFile("outrigger-handback-test.config");
        Files.write(f.toPath(), (
            "import org.apache.river.outrigger.HandbackFormatRejectTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    store = HandbackFormatRejectTest.testStore();\n"
            + "    serverExporter = HandbackFormatRejectTest.testExporter();\n"
            + "    discoveryManager = "
            + "HandbackFormatRejectTest.testDiscoveryManager();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n").getBytes(StandardCharsets.UTF_8));
        wrapper = new PersistentOutriggerImpl(
            new String[]{f.getAbsolutePath()}, null);
        wrapper.start();
    }

    @After
    public void shutdown() throws Exception {
        if (wrapper != null) {
            try {
                wrapper.destroy();
            } catch (Throwable t) {
                // keep teardown best-effort; thread wait below still runs
            }
            // Give the DestroyThread time to tear everything down so the
            // surefire fork can exit (non-daemon threads would wedge it).
            long deadline = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < deadline
                    && anyOutriggerThreadAlive()) {
                Thread.sleep(100);
            }
            wrapper = null;
        }
    }

    private static boolean anyOutriggerThreadAlive() {
        Thread[] threads = new Thread[Thread.activeCount() * 2 + 32];
        int n = Thread.enumerate(threads);
        for (int i = 0; i < n; i++) {
            Thread t = threads[i];
            if (t != null && t.isAlive() && !t.isDaemon()) {
                String name = t.getName();
                if (name.contains("TxnMonitor") || name.contains("Reaper")
                    || name.contains("OperationJournal")
                    || name.contains("ExpirationOpQueue")
                    || name.contains("DestroyThread")) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------ helpers ------------------------

    private static RemoteEventListener fakeListener() {
        return (RemoteEventListener) Proxy.newProxyInstance(
            HandbackFormatRejectTest.class.getClassLoader(),
            new Class<?>[]{RemoteEventListener.class},
            new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] args) {
                    switch (m.getName()) {
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "FakeListener";
                        default: return null;
                    }
                }
            });
    }

    private OutriggerServerImpl delegate() throws Exception {
        Field f = OutriggerServerWrapper.class.getDeclaredField("delegate");
        f.setAccessible(true);
        return (OutriggerServerImpl) f.get(wrapper);
    }

    private Map<?, ?> eventRegistrations() throws Exception {
        Field f = OutriggerServerImpl.class
            .getDeclaredField("eventRegistrations");
        f.setAccessible(true);
        return (Map<?, ?>) f.get(delegate());
    }

    private static MarshalledInstance jossMI() throws IOException {
        return new MarshalledInstance("joss-payload"); // bare ctor = JOSS
    }

    private static MarshalledInstance derMI() throws IOException {
        return new MarshalledInstance("der-payload", Collections.EMPTY_SET,
            new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
    }

    private EntryRep template() throws Exception {
        TestEntry e = new TestEntry();
        e.value = "tmpl";
        return new EntryRep(e, MarshallingFormat.ATOMIC_DER);
    }

    private void assertNoTrace() throws Exception {
        assertTrue("a rejected registration must leave no watcher/lease",
            eventRegistrations().isEmpty());
        assertEquals("a rejected registration must leave no durable log "
            + "record", 0, store.log.registerOps.get());
    }

    // ------------------------ notify ------------------------

    @Test
    public void jossHandbackRejectedByNotifyLeavingNoTrace() throws Exception {
        try {
            wrapper.notify(null, null, fakeListener(), 1000L, jossMI());
            fail("a JOSS-payload handback must be rejected at registration");
        } catch (IllegalArgumentException expected) {
            assertTrue("rejection must name the offending format",
                expected.getMessage().contains("JOSS"));
            assertTrue("rejection must show the DER construction",
                expected.getMessage().contains("ATOMIC_DER"));
        }
        assertNoTrace();
    }

    @Test
    public void derHandbackAcceptedByNotify() throws Exception {
        EventRegistration reg =
            wrapper.notify(null, null, fakeListener(), 1000L, derMI());
        assertNotNull(reg);
        assertEquals("accepted registration must be recorded",
            1, eventRegistrations().size());
        assertEquals("accepted non-txn registration must be logged",
            1, store.log.registerOps.get());
    }

    @Test
    public void nullHandbackAcceptedByNotify() throws Exception {
        EventRegistration reg =
            wrapper.notify(null, null, fakeListener(), 1000L, null);
        assertNotNull(reg);
        assertEquals(1, eventRegistrations().size());
    }

    @Test
    public void nullContentMIAcceptedByNotify() throws Exception {
        // A MarshalledInstance containing null carries no payload at all;
        // the platform labels it with the JOSS token but there is nothing
        // to decode -- not a downgrade vector, so it is accepted.
        EventRegistration reg = wrapper.notify(null, null, fakeListener(),
            1000L, new MarshalledInstance((Object) null));
        assertNotNull(reg);
        assertEquals(1, eventRegistrations().size());
    }

    // ------------- registerForAvailabilityEvent -------------

    @Test
    public void jossHandbackRejectedByRegisterLeavingNoTrace() throws Exception {
        try {
            wrapper.registerForAvailabilityEvent(
                new EntryRep[]{template()}, null, false, fakeListener(),
                1000L, jossMI());
            fail("a JOSS-payload handback must be rejected at registration");
        } catch (IllegalArgumentException expected) {
            assertTrue("rejection must name the offending format",
                expected.getMessage().contains("JOSS"));
        }
        assertNoTrace();
    }

    @Test
    public void derHandbackAcceptedByRegister() throws Exception {
        EventRegistration reg = wrapper.registerForAvailabilityEvent(
            new EntryRep[]{template()}, null, false, fakeListener(),
            1000L, derMI());
        assertNotNull(reg);
        assertEquals(1, eventRegistrations().size());
        assertEquals(1, store.log.registerOps.get());
    }

    @Test
    public void nullHandbackAcceptedByRegister() throws Exception {
        EventRegistration reg = wrapper.registerForAvailabilityEvent(
            new EntryRep[]{template()}, null, false, fakeListener(),
            1000L, null);
        assertNotNull(reg);
        assertEquals(1, eventRegistrations().size());
    }
}
