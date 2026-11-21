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

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import org.apache.river.outrigger.proxy.EntryRep;
import org.apache.river.outrigger.proxy.OutriggerServer;
import org.apache.river.outrigger.snaplogstore.LogStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * R1: can one remote write leave a persistent space unable to restart?
 *
 * <p>The write path logs a rep before it applies it ({@code OutriggerServerImpl.write}: "nothing can
 * go wrong, log this addition"), and recovery aborts the start-up on any exception while replaying
 * the log. A rep whose {@code superclasses} holds a null element passed the unmarshal checks, was
 * logged, then failed when the type tree took the null as a class name; on restart the same replay
 * step failed again (design 2026-11-21, Q3 reason 1, a HYPOTHESIS until this test).
 *
 * <p>Real parts: the DER wire path, a real {@link PersistentOutriggerImpl}, the real snaplogstore
 * ({@link LogStore}) and its recovery. Stand-ins: the exporter (a recording proxy, no network), the
 * lookup groups (none) and the join-state log record, which is dropped (see
 * {@link StoreWithoutJoinState}). The test sits in package {@code org.apache.river.outrigger} because
 * {@code PersistentOutriggerImpl} is package-private.
 *
 * <p>Each run reports what happened at each step on standard output ("R1 OBSERVED") so that a failure
 * on unfixed code shows which step differs from the prediction.
 *
 * <p>Runtime tier: moderate (starts two servers; a few seconds).
 */
public class PoisonSuperclassesRestartTest {

    public static class Wide implements Entry {
        private static final long serialVersionUID = 1L;
        public String a;
        public String b;
        public Wide() {}
        public Wide(String a, String b) { this.a = a; this.b = b; }
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ---- fixtures handed to the server through the configuration file ----

    /** Answers every call on the stand-in proxy with a default. */
    private static final class DefaultAnswers implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setConstraints": return proxy;
                case "getConstraints": return null;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ProxyExporterProxy";
                default: {
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return Boolean.FALSE;
                    if (rt.isPrimitive() && rt != void.class) return 0;
                    return null;
                }
            }
        }
    }

    /** An exporter that exports nothing: a constrainable proxy that answers every call with a default. */
    public static final class ProxyExporter implements Exporter {
        public Remote export(Remote impl) {
            return (Remote) Proxy.newProxyInstance(
                PoisonSuperclassesRestartTest.class.getClassLoader(),
                new Class<?>[]{OutriggerServer.class, RemoteMethodControl.class},
                new DefaultAnswers());
        }

        public boolean unexport(boolean force) {
            return true;
        }
    }

    private static volatile Exporter exporter;
    private static volatile LogStore lastStore;

    /** Called from the configuration file. */
    public static Exporter testExporter() { return exporter; }

    /** Called from the configuration file ({@code this} is the configuration): the real store, remembered so the test can close it. */
    public static Store openStore(Configuration config) throws ConfigurationException {
        LogStore opened = new LogStore(config);
        lastStore = opened;
        return new StoreWithoutJoinState(opened);
    }

    /**
     * The real store, minus the join-state record. MEASURED at 1c4f13726 (one harness, this one): with the
     * server's own default attributes the join state cannot be written, because
     * {@code net.jini.lookup.entry.ServiceInfo} has no DER form ({@code UnsupportedOperationException} from
     * {@code JoinStateManager.writeAttributes}), and {@code LogOutputFile} runs every op on an executor whose
     * {@code submit} discards the failure. The record is left half written, the next record is appended to it,
     * and recovery then fails with an {@code OptionalDataException}. That is a separate defect that fails every
     * restart, with or without a poison write. Dropping the record keeps the log readable, so this test
     * isolates the write under test.
     */
    private static final class StoreWithoutJoinState implements Store {
        private final LogStore real;

        StoreWithoutJoinState(LogStore real) { this.real = real; }

        public LogOps setupStore(Recover space, String entryFormat) throws IOException {
            final LogOps log = real.setupStore(space, entryFormat);
            return (LogOps) Proxy.newProxyInstance(LogOps.class.getClassLoader(), new Class<?>[] { LogOps.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("joinStateOp")) return null;
                        try {
                            return method.invoke(log, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                });
        }

        public void destroy() throws IOException { real.destroy(); }

        public void close() throws IOException { real.close(); }
    }

    // ---- the wire path ----

    /** The production wire path: @AtomicSerial DER encode, then the EntryRep(GetArg) decode. */
    private static EntryRep wire(EntryRep rep) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos);
        out.writeObject(rep);
        out.flush();
        out.close();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                ResolutionContext.of(EntryRep.class.getClassLoader()));
        return in.readObject(EntryRep.class);
    }

    /** What a hostile client sends: a Wide rep whose superclasses holds a single null name. */
    private static EntryRep nullSuperclassRep() throws Exception {
        EntryRep rep = new EntryRep(new Wide("p", "x"));
        set(rep, "superclasses", new String[] { null });
        set(rep, "hashes", new long[] { 0L });
        return rep;
    }

    private static void set(EntryRep rep, String field, Object value) throws Exception {
        Field f = EntryRep.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(rep, value);
    }

    // ---- the log, read back through the real store ----

    /** Reads what the real store replays: one summary line per recovered write. */
    private static final class RecoveryProbe implements Recover {
        final List<String> writes = new ArrayList<String>();

        public void recoverSessionId(long sessionId) { }
        public void recoverJoinState(StoredObject state) throws Exception { }
        public void recoverWrite(StoredResource entry, Long txnId) throws Exception {
            EntryRep rep = new EntryRep();
            entry.restore(rep);
            writes.add(Arrays.toString(rep.superclasses()));
        }
        public void recoverTake(Uuid cookie, Long txnId) throws Exception { }
        public void recoverRegister(StoredResource registration, String type,
                StoredObject[] templates) throws Exception { }
        public void recoverTransaction(Long txnId, StoredObject transaction) throws Exception { }
        public void recoverUuid(Uuid uuid) { }
        public void recoverEntryFormat(String format) { }
    }

    private String writeConfig(String dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("import org.apache.river.outrigger.PoisonSuperclassesRestartTest;\n");
        sb.append("org.apache.river.outrigger {\n");
        sb.append("    persistenceDirectory = \"").append(dir).append("\";\n");
        sb.append("    store = PoisonSuperclassesRestartTest.openStore(this);\n");
        sb.append("    serverExporter = PoisonSuperclassesRestartTest.testExporter();\n");
        sb.append("    initialLookupGroups = new String[]{};\n");
        sb.append("}\n");
        File f = tmp.newFile("outrigger-restart.config");
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return f.getAbsolutePath();
    }

    private static PersistentOutriggerImpl boot(String config) throws Exception {
        PersistentOutriggerImpl server = new PersistentOutriggerImpl(new String[] { config }, null);
        server.start();
        return server;
    }

    private static void destroyQuietly(PersistentOutriggerImpl server) {
        try {
            server.destroy();
        } catch (Throwable ignored) {
            // best effort: ends the server's threads so the test JVM can exit
        }
    }

    private static String text(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) sb.append(part);
        return sb.toString();
    }

    private static InvalidObjectException invalidObject(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InvalidObjectException) return (InvalidObjectException) c;
        }
        return null;
    }

    /** What happened at each step of one run. */
    private static final class Outcome {
        Throwable refusedAtWire;
        Throwable writeFailure;
        boolean reachedServer;
        List<String> replayed = new ArrayList<String>();
        Throwable restartFailure;

        String observed() {
            return text("R1 OBSERVED: refusedAtWire=", describe(refusedAtWire),
                    "; reachedServer=", reachedServer,
                    "; writeFailure=", describe(writeFailure),
                    "; writesReplayed=", replayed,
                    "; restartFailure=", describe(restartFailure));
        }

        private static String describe(Throwable t) {
            return t == null ? "none" : t.getClass().getName();
        }
    }

    /**
     * Boots a server on a fresh directory, writes one honest entry and then (if {@code poison}) one rep
     * with a null superclass name through the DER wire path, closes the store, replays the log through
     * the real store, and boots a second server on the same directory.
     */
    private Outcome run(boolean poison) throws Exception {
        exporter = new ProxyExporter();
        String dir = new File(tmp.getRoot(), "space").getAbsolutePath().replace('\\', '/');
        String config = writeConfig(dir);
        Outcome outcome = new Outcome();
        List<PersistentOutriggerImpl> started = new ArrayList<PersistentOutriggerImpl>();
        try {
            PersistentOutriggerImpl first = boot(config);
            started.add(first);
            first.write(wire(new EntryRep(new Wide("h", "x"))), null, Lease.FOREVER);

            if (poison) {
                EntryRep decoded = null;
                try {
                    decoded = wire(nullSuperclassRep());
                } catch (Exception e) {
                    outcome.refusedAtWire = e;
                }
                if (decoded != null) {
                    outcome.reachedServer = true;
                    try {
                        first.write(decoded, null, Lease.FOREVER);
                    } catch (Throwable t) {
                        outcome.writeFailure = t;
                    }
                }
            }
            lastStore.close();

            RecoveryProbe probe = new RecoveryProbe();
            LogStore reopened = new LogStore(ConfigurationProvider.getInstance(new String[] { config }));
            reopened.setupStore(probe, MarshallingFormat.ATOMIC_DER.getFormat());
            reopened.close();
            outcome.replayed = probe.writes;

            try {
                started.add(boot(config));
            } catch (Throwable t) {
                outcome.restartFailure = t;
            }
        } finally {
            for (int i = started.size() - 1; i >= 0; i--) destroyQuietly(started.get(i));
        }
        System.out.println(outcome.observed());
        return outcome;
    }

    /**
     * R1: a rep with a null superclass name must be refused at unmarshal, so the log never holds it and the
     * space restarts. On unfixed code the rep is accepted, logged, fails in the type tree, and the replay
     * of the log fails the restart (the prediction this test checks).
     */
    @Test
    public void r1_nullSuperclassWriteLeavesTheSpaceRestartable() throws Exception {
        Outcome o = run(true);
        String observed = o.observed();

        assertNotNull(text("the rep must be refused at unmarshal. ", observed), o.refusedAtWire);
        assertTrue(text("the refusal must be an InvalidObjectException. ", observed),
                invalidObject(o.refusedAtWire) != null);
        assertEquals(text("the refused rep must not reach the server. ", observed), false, o.reachedServer);
        assertNull(text("no server write fails. ", observed), o.writeFailure);
        assertEquals(text("the log must hold only the honest write. ", observed), 1, o.replayed.size());
        assertNull(text("the space must restart. ", observed), o.restartFailure);
    }

    /** R1 control: honest write only. The log holds it and the space restarts. Reads the same either way. */
    @Test
    public void r1control_honestWriteSurvivesARestart() throws Exception {
        Outcome o = run(false);
        String observed = o.observed();

        assertNull(observed, o.writeFailure);
        assertEquals(observed, 1, o.replayed.size());
        assertEquals(observed, "[]", o.replayed.get(0));
        assertNull(observed, o.restartFailure);
    }
}
