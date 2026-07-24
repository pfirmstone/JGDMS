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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.rmi.Remote;
import net.jini.config.ConfigurationException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.DiscoveryLocatorManagement;
import net.jini.discovery.DiscoveryManagement;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ServerEndpoint;
import org.apache.river.outrigger.proxy.OutriggerServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * DER-only configuration rejections
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} sec.3 item 1 +
 * sec.9.1): the retained {@code useDerForEntries} entry fails loudly at
 * startup if set to anything but {@code true} (never silently ignored),
 * and the best-effort deployer {@code serverExporter} override check
 * refuses an introspectable {@code BasicJeriExporter} whose invocation
 * layer factory is not an {@code AtomicDerILFactory}.
 */
public class DerOnlyConfigRejectTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * A ServerEndpoint stand-in: never listened on -- the exporter check
     * refuses at init(), long before export. (TcpServerEndpoint cannot be
     * used here: its class initialization requires an SM-capable JVM.)
     */
    public static final class FakeServerEndpoint implements ServerEndpoint {
        public InvocationConstraints checkConstraints(InvocationConstraints c) {
            return InvocationConstraints.EMPTY;
        }
        public Endpoint enumerateListenEndpoints(ListenContext ctx)
                throws IOException {
            throw new UnsupportedOperationException("never exported");
        }
    }

    /** Called from the configuration file: a stale JOSS-IL exporter. */
    public static Exporter jossIlExporter() {
        return new BasicJeriExporter(new FakeServerEndpoint(),
            new AtomicILFactory(null, null,
                OutriggerServer.class.getClassLoader()),
            false, true);
    }

    /**
     * Called from the configuration file: an in-VM exporter handing back a
     * constrainable {@code OutriggerServer} proxy, so the acceptance test
     * below can boot the service fully without a network endpoint (mirrors
     * {@code HandbackFormatRejectTest.FakeExporter}).
     */
    public static Exporter inVmExporter() {
        return new Exporter() {
            public Remote export(Remote impl) {
                InvocationHandler handler = new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        switch (method.getName()) {
                            case "setConstraints": return proxy;
                            case "getConstraints": return null;
                            case "equals": return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
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
                    DerOnlyConfigRejectTest.class.getClassLoader(),
                    new Class<?>[]{OutriggerServer.class,
                                   RemoteMethodControl.class},
                    handler);
            }
            public boolean unexport(boolean force) { return true; }
        };
    }

    /**
     * Inert discovery manager keeping the boot path off the network
     * (mirrors {@code HandbackFormatRejectTest.FakeDiscoveryManager}).
     * Named public type so the configuration language's static type check
     * sees a {@code DiscoveryManagement} implementation.
     */
    public static final class InertDiscoveryManager implements
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

    /** Called from the configuration file. */
    public static InertDiscoveryManager inertDiscoveryManager() {
        return new InertDiscoveryManager();
    }

    private String writeConfig(String body) throws Exception {
        File f = tmp.newFile("outrigger-config-reject.config");
        Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return f.getAbsolutePath();
    }

    @Test
    public void useDerForEntriesFalseIsRefusedLoudly() throws Exception {
        String config = writeConfig(
            "org.apache.river.outrigger {\n"
            + "    useDerForEntries = false;\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");
        TransientOutriggerImpl wrapper =
            new TransientOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("useDerForEntries=false must be refused, never silently "
                + "ignored");
        } catch (ConfigurationException expected) {
            assertTrue("refusal must be actionable and name the entry",
                expected.getMessage().contains("useDerForEntries"));
            assertTrue("refusal must name the DER-only regime",
                expected.getMessage().contains("ATOMIC_DER"));
        }
    }

    /**
     * U1a-review F5 rider (U1c): the retained {@code useDerForEntries}
     * entry explicitly set to {@code true} is ACCEPTED -- the service
     * boots fully (sec.9.1 resolution: retained and reject-if-JOSS; a
     * DER-affirming config must never be refused).
     */
    @Test
    public void useDerForEntriesTrueIsAccepted() throws Exception {
        String config = writeConfig(
            "import org.apache.river.outrigger.DerOnlyConfigRejectTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    useDerForEntries = true;\n"
            + "    serverExporter = DerOnlyConfigRejectTest.inVmExporter();\n"
            + "    discoveryManager = "
            + "DerOnlyConfigRejectTest.inertDiscoveryManager();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");
        TransientOutriggerImpl wrapper =
            new TransientOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start(); // must not throw: true is the (only) accepted value
        } finally {
            try {
                wrapper.destroy();
            } catch (Throwable t) {
                // teardown is best-effort; thread wait below still runs
            }
            // Wait for the DestroyThread to tear non-daemon threads down so
            // the surefire fork can exit.
            long deadline = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < deadline
                    && anyOutriggerThreadAlive()) {
                Thread.sleep(100);
            }
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

    @Test
    public void nonDerBasicJeriExporterIsRefusedLoudly() throws Exception {
        String config = writeConfig(
            "import org.apache.river.outrigger.DerOnlyConfigRejectTest;\n"
            + "import net.jini.export.Exporter;\n"
            + "org.apache.river.outrigger {\n"
            + "    serverExporter = DerOnlyConfigRejectTest.jossIlExporter();\n"
            + "    initialLookupGroups = new String[]{};\n"
            + "}\n");
        TransientOutriggerImpl wrapper =
            new TransientOutriggerImpl(new String[]{config}, null);
        try {
            wrapper.start();
            fail("a deployer serverExporter with a non-DER invocation "
                + "layer must be refused where introspectable");
        } catch (ConfigurationException expected) {
            assertTrue("refusal must name the required factory",
                expected.getMessage().contains("AtomicDerILFactory"));
        }
    }
}
