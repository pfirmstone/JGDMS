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
package au.net.zeus.jgdms.api.hello;

import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip / contract test for the Hello World service-proxy boilerplate that
 * is now <em>generated</em> from {@code @JiniService(proxy = DYNAMIC)} by the
 * service-proxy annotation processor.
 *
 * <p>For the DYNAMIC shape (JGDMS-STD-009 §6 shape 1) the processor generates
 * <em>no</em> proxy class, <em>no</em> backend interface, and <em>no</em>
 * ILFactory — the client proxy <b>is</b> the JERI-exported
 * {@link java.lang.reflect.Proxy} dynamic stub, and its admin interfaces are
 * supplied by the reusable framework factory
 * {@link net.jini.jeri.DynamicILFactory}, a {@link net.jini.jeri.AtomicILFactory}
 * subclass whose {@code getRemoteInterfaces} override <em>appends</em> the
 * non-{@link Remote} admin interfaces
 * ({@link Administrable}/{@link JoinAdmin}/{@link DestroyAdmin}) to the exported
 * stub's interface AND server-dispatch sets.  The JGDMS service framework installs
 * it by default, so a DYNAMIC service needs neither codegen nor config.
 *
 * <p>This test exports a {@link HelloService} implementation through JERI using
 * {@link net.jini.jeri.DynamicILFactory} with the same admin interface set the
 * framework default supplies, and asserts that the resulting stub has exactly the
 * fat interface set the DYNAMIC shape promises: it is a
 * {@link java.lang.reflect.Proxy}; it implements the public {@link HelloService}
 * API, the appended admin interfaces, the {@link Remote} bootstrap accessors, and
 * {@link RemoteMethodControl}; and a loopback call to {@link HelloService#sayHello}
 * forwards to the exported implementation.
 *
 * <p>The export is a real in-JVM JERI loopback over TCP (no mocking), so the test
 * needs the JDK default {@code RMIClassLoaderSpi} — configured via the surefire
 * {@code argLine} in this module's pom.
 */
public class HelloServiceProxyRoundTripTest {

    /**
     * The exported DYNAMIC stub must be a {@link java.lang.reflect.Proxy} — the
     * whole point of shape 1 is that there is no generated proxy class; the
     * runtime dynamic proxy is the client proxy.
     */
    @Test
    public void testExportedStubIsDynamicProxy() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object proxy = exporter.export(new HelloServiceFixture());
            assertTrue("DYNAMIC export must yield a java.lang.reflect.Proxy stub",
                    Proxy.isProxyClass(proxy.getClass()));
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * The exported DYNAMIC stub must carry the full fat interface set: the public
     * API, the non-Remote admin interfaces appended by
     * {@link net.jini.jeri.DynamicILFactory#getRemoteInterfaces}, the Remote
     * bootstrap accessors picked up by {@code super.getRemoteInterfaces}, and
     * {@link RemoteMethodControl} (every JERI dynamic proxy is constrainable).
     */
    @Test
    public void testExportedStubImplementsFatInterfaceSet() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object proxy = exporter.export(new HelloServiceFixture());
            for (Class<?> required : new Class<?>[]{
                    HelloService.class,
                    Administrable.class, JoinAdmin.class, DestroyAdmin.class,
                    ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
                    ServiceIDAccessor.class, CodebaseAccessor.class,
                    RemoteMethodControl.class}) {
                assertTrue(required.getName()
                        + " must be implemented by the exported DYNAMIC stub",
                        required.isInstance(proxy));
            }
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * A loopback call on the exported DYNAMIC stub forwards
     * {@link HelloService#sayHello} to the backing implementation over a real
     * in-JVM JERI round-trip.
     */
    @Test
    public void testStubForwardsSayHello() throws Exception {
        Exporter exporter = newExporter();
        try {
            HelloService proxy = (HelloService) exporter.export(new HelloServiceFixture());
            assertEquals("Hello, World!", proxy.sayHello("World"));
        } finally {
            exporter.unexport(true);
        }
    }

    // --------------------------------------------------------------- helpers

    /**
     * Builds a plain-TCP {@link BasicJeriExporter} whose invocation-layer factory
     * is a {@link net.jini.jeri.DynamicILFactory} carrying the Jini admin
     * interfaces — the same wiring the framework default installs for the DYNAMIC
     * shape (see {@code JiniServiceParameters}).
     */
    private static Exporter newExporter() {
        return new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new net.jini.jeri.DynamicILFactory(
                        null, null,
                        HelloServiceProxyRoundTripTest.class.getClassLoader(),
                        new Class[]{
                            Administrable.class, JoinAdmin.class, DestroyAdmin.class
                        }),
                false, true);
    }

    /**
     * Minimal remote fixture that implements the public {@link HelloService} API,
     * the admin interfaces, and the {@link Remote} bootstrap accessors — exactly
     * the interface set a real {@code AbstractJiniService} exposes.  Exporting it
     * through {@link net.jini.jeri.DynamicILFactory} produces the fat DYNAMIC stub
     * under test.  {@code sayHello} returns a greeting so the forwarding test can
     * observe the loopback call; the infrastructure methods are never invoked by
     * these tests and throw.
     */
    public static final class HelloServiceFixture
            implements HelloService,
                       Administrable, JoinAdmin, DestroyAdmin,
                       ServiceProxyAccessor, ServiceAttributesAccessor,
                       ServiceIDAccessor, CodebaseAccessor {

        private static UnsupportedOperationException nope() {
            return new UnsupportedOperationException("fixture method not invoked");
        }

        @Override public String sayHello(String name) { return "Hello, " + name + "!"; }
        @Override public Object getServiceProxy() { throw nope(); }
        @Override public Entry[] getServiceAttributes() { throw nope(); }
        @Override public ServiceID serviceID() { throw nope(); }
        @Override public String getClassAnnotation() { throw nope(); }
        @Override public String getCertFactoryType() { throw nope(); }
        @Override public String getCertPathEncoding() { throw nope(); }
        @Override public byte[] getEncodedCerts() { throw nope(); }
        @Override public String getCodebaseDigestAlgorithm() { throw nope(); }
        @Override public byte[] getCodebaseDigest() { throw nope(); }
        @Override public int[] getDigestOffsets() { throw nope(); }
        @Override public Object getAdmin() { throw nope(); }
        @Override public Entry[] getLookupAttributes() { throw nope(); }
        @Override public void addLookupAttributes(Entry[] a) { throw nope(); }
        @Override public void modifyLookupAttributes(Entry[] t, Entry[] a) { throw nope(); }
        @Override public String[] getLookupGroups() { throw nope(); }
        @Override public void addLookupGroups(String[] g) { throw nope(); }
        @Override public void removeLookupGroups(String[] g) { throw nope(); }
        @Override public void setLookupGroups(String[] g) { throw nope(); }
        @Override public LookupLocator[] getLookupLocators() { throw nope(); }
        @Override public void addLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void removeLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void setLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void destroy() { throw nope(); }
    }
}
