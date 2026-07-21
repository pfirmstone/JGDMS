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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.Remote;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.Exporter;
import org.apache.river.outrigger.proxy.OutriggerServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * The fail-clean guard test for a refused recovery
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} sec.3 item 2, the
 * board's HIGH): booting a real {@code PersistentOutriggerImpl} against a
 * store whose recovery format guard refuses (a JOSS-born store) must
 * <ul>
 * <li>fail with the CHECKED {@link IncompatibleStoreException} (the
 *     unchecked path used to bypass the startup cleanup block);</li>
 * <li>never have exported a live endpoint ({@code exporter.export()} now
 *     runs only AFTER successful store recovery);</li>
 * <li>leave no surviving non-daemon threads;</li>
 * <li>close (not destroy) the store.</li>
 * </ul>
 */
public class RecoveryFailCleanTest {

    private static final String DER = MarshallingFormat.ATOMIC_DER.getFormat();
    private static final String JOSS = MarshallingFormat.JOSS.getFormat();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ---- fixtures handed to the server through the configuration file ----

    /** Recording exporter: knows whether export/unexport ever happened. */
    public static final class RecordingExporter implements Exporter {
        final AtomicBoolean exportCalled = new AtomicBoolean();
        final AtomicBoolean unexportCalled = new AtomicBoolean();

        public Remote export(Remote impl) {
            exportCalled.set(true);
            InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    switch (method.getName()) {
                        case "setConstraints": return proxy;
                        case "getConstraints": return null;
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "RecordingExporterProxy";
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
                RecoveryFailCleanTest.class.getClassLoader(),
                new Class<?>[]{OutriggerServer.class, RemoteMethodControl.class},
                handler);
        }

        public boolean unexport(boolean force) {
            unexportCalled.set(true);
            return true;
        }
    }

    /**
     * A store standing in for a JOSS-born snaplogstore: its recovered
     * snapshot format is JOSS, so dispatching the recovery format guard
     * refuses the store (checked) before any LogOps is returned. The
     * real dispatch mechanics (guard before consumeLogs, store pristine)
     * are covered by snaplogstore's BornFormatGuardTest against the real
     * BackEnd; this fake isolates the SERVER-side fail-clean contract.
     */
    public static final class JossBornStore implements Store {
        final AtomicBoolean closeCalled = new AtomicBoolean();
        final AtomicBoolean destroyCalled = new AtomicBoolean();

        public LogOps setupStore(Recover space, String entryFormat)
                throws IOException {
            assertEquals("server must be born ATOMIC_DER", DER, entryFormat);
            space.recoverEntryFormat(JOSS); // throws IncompatibleStoreException
            throw new AssertionError(
                "recoverEntryFormat must refuse a JOSS-born store");
        }

        public void destroy() { destroyCalled.set(true); }
        public void close() { closeCalled.set(true); }
    }

    private static volatile RecordingExporter exporter;
    private static volatile JossBornStore store;

    /** Called from the configuration file. */
    public static Exporter testExporter() { return exporter; }

    /** Called from the configuration file. */
    public static Store testStore() { return store; }

    @Before
    public void fresh() {
        exporter = new RecordingExporter();
        store = new JossBornStore();
    }

    private String writeConfig(String body) throws IOException {
        File f = tmp.newFile("outrigger-test.config");
        Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return f.getAbsolutePath();
    }

    private static Set<Thread> liveThreads() {
        Set<Thread> live = new HashSet<Thread>();
        Thread[] threads = new Thread[Thread.activeCount() * 2 + 32];
        int n = Thread.enumerate(threads);
        for (int i = 0; i < n; i++) {
            if (threads[i] != null && threads[i].isAlive()) live.add(threads[i]);
        }
        return live;
    }

    /**
     * Waits (bounded) for every non-daemon thread NOT present in
     * {@code before} to terminate; returns the stragglers.
     */
    private static Set<Thread> awaitNoNewNonDaemonThreads(
            Set<Thread> before, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        Set<Thread> stragglers;
        do {
            stragglers = new HashSet<Thread>();
            for (Thread t : liveThreads()) {
                if (!before.contains(t) && !t.isDaemon()) stragglers.add(t);
            }
            if (stragglers.isEmpty()) return stragglers;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return stragglers;
    }

    @Test
    public void refusedRecoveryIsFailLoudAndFailClean() throws Exception {
        String config = writeConfig(
            "import org.apache.river.outrigger.RecoveryFailCleanTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    store = RecoveryFailCleanTest.testStore();\n"
            + "    serverExporter = RecoveryFailCleanTest.testExporter();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");

        Set<Thread> before = liveThreads();

        PersistentOutriggerImpl wrapper =
            new PersistentOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("starting against a JOSS-born store must be refused");
        } catch (IncompatibleStoreException expected) {
            // Fail-LOUD: checked refusal, with an operator-actionable
            // message naming the offline converter path.
            assertTrue("refusal must name the recovered format",
                expected.getMessage().contains(JOSS));
            assertTrue("refusal must point the operator at the offline "
                + "converter", expected.getMessage().contains("convert"));
        }

        // Fail-CLEAN (i): a refused store never had a live endpoint --
        // export happens only after successful store recovery.
        assertFalse("exporter.export() must never have been called for a "
            + "refused store", exporter.exportCalled.get());

        // Fail-CLEAN (ii): the store was closed (not destroyed).
        assertTrue("cleanup must close the refused store",
            store.closeCalled.get());
        assertFalse("cleanup must never destroy the refused store",
            store.destroyCalled.get());

        // Fail-CLEAN (iii): no non-daemon thread started during the
        // refused startup survives (TxnMonitor + pool, starter, etc.).
        Set<Thread> stragglers = awaitNoNewNonDaemonThreads(before, 30000);
        assertTrue("non-daemon threads survived a refused recovery: "
            + stragglers, stragglers.isEmpty());
    }

    /**
     * Ordering regression net: if export ever moves back before store
     * recovery, the refusal must at minimum unexport the endpoint. (With
     * the current ordering export is never called, so this is vacuous --
     * it exists to catch a partial revert that calls export early but
     * keeps the cleanup.)
     */
    @Test
    public void ifExportedAtAllThenUnexported() throws Exception {
        String config = writeConfig(
            "import org.apache.river.outrigger.RecoveryFailCleanTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    store = RecoveryFailCleanTest.testStore();\n"
            + "    serverExporter = RecoveryFailCleanTest.testExporter();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");

        PersistentOutriggerImpl wrapper =
            new PersistentOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("starting against a JOSS-born store must be refused");
        } catch (IncompatibleStoreException expected) {
        }
        if (exporter.exportCalled.get()) {
            assertTrue("an exported endpoint must be unexported on refusal",
                exporter.unexportCalled.get());
        }
    }
}
