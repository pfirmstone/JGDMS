/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.der.marshal;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIX&nbsp;1 regression: {@link DerMarshalInstanceOutput#writeObject(Object)} must thread the
 * marshalling stream's real context loader (the marshalled object's own defining loader, exactly
 * as JOSS {@code AtomicMarshalledInstance.getLoader(accessor)} sources it) into
 * {@code DerProxySerializer.create(...)} -- NOT {@code null} (the latent bug that made
 * {@code Service.providers(ProxyCodebaseSpi.class, null)} throw a spurious
 * {@code ServiceConfigurationError} when the provider lived in a non-bootstrap loader) and NOT the
 * thread-context loader (the Warres ambient-resolution failure).
 *
 * <p>The test marshals a downloadable {@link DynamicProxyCodebaseAccessor} smart-proxy shape
 * (also {@link RemoteMethodControl}) whose defining loader is a bespoke child loader that -- and
 * only that loader -- exposes a {@link CapturingProxyCodebaseProvider} through its
 * {@code META-INF/services}. The provider records the {@code streamLoader} it is handed. The test
 * asserts that recorded loader is (a) non-null, (b) identical to the proxy's own defining loader
 * (so {@code Service.providers} found AND loaded the provider through it -- the reggie failure
 * path now succeeds), and (c) NOT the deliberately-distinct thread-context loader in force during
 * the marshal.
 */
public class DerMarshalLoaderThreadingTest {

    private static final String SPI_RESOURCE = "META-INF/services/net.jini.loader.ProxyCodebaseSpi";

    /** Minimal {@code @AtomicSerial} handler so the bare proxy can ride the [8]/carrier encode. */
    @AtomicSerial
    public static class Handler implements InvocationHandler {
        private final String tag;

        public Handler(String tag) { this.tag = tag; }

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("tag", String.class) };
        }

        public static void serialize(PutArg arg, Handler h) throws IOException {
            arg.put("tag", h.tag);
            arg.writeArgs();
        }

        public Handler(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("tag", null, String.class));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // Never invoked on the encode path exercised here.
            switch (method.getName()) {
                case "equals":   return proxy == (args == null ? null : args[0]);
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "Handler[" + tag + "]";
                default:         return null;
            }
        }
    }

    /**
     * A child loader that exposes ONLY {@link CapturingProxyCodebaseProvider} for the
     * ProxyCodebaseSpi service file (delegating all other resources / class loading to its
     * parent). Used both as the marshalled proxy's defining loader and as the loader that
     * {@code Service.providers} scans for the provider.
     */
    private static final class ProviderExposingLoader extends ClassLoader {
        private final URL serviceUrl;

        ProviderExposingLoader(ClassLoader parent, URL serviceUrl) {
            super(parent);
            this.serviceUrl = serviceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SPI_RESOURCE.equals(name)) {
                return Collections.enumeration(Collections.singletonList(serviceUrl));
            }
            return super.getResources(name);
        }
    }

    @Test
    public void marshalThreadsObjectDefiningLoaderNotNullNotTccl() throws Exception {
        CapturingProxyCodebaseProvider.reset();

        // A services file naming the (parent-loadable) capturing provider, exposed ONLY via the
        // custom loader below -- never registered on the shared test classpath.
        File svcFile = File.createTempFile("ProxyCodebaseSpi-", ".services");
        svcFile.deleteOnExit();
        Files.write(svcFile.toPath(),
                (CapturingProxyCodebaseProvider.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
        URL svcUrl = svcFile.toURI().toURL();

        ClassLoader parent = getClass().getClassLoader();
        ProviderExposingLoader defining = new ProviderExposingLoader(parent, svcUrl);

        // A downloadable dynamic-proxy smart-proxy shape, DEFINED BY the custom loader so its
        // own class loader is `defining` (this is what getLoader(obj) must return and thread).
        Object proxy = Proxy.newProxyInstance(
                defining,
                new Class<?>[]{ DynamicProxyCodebaseAccessor.class, RemoteMethodControl.class },
                new Handler("svc"));
        assertSame(defining, proxy.getClass().getClassLoader(),
                "precondition: the proxy's defining loader must be the custom loader");

        // A deliberately DISTINCT thread-context loader must NOT be what gets threaded.
        ClassLoader throwawayTccl = new ClassLoader(parent) {};
        ClassLoader savedTccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(throwawayTccl);
        try {
            MarshalledInstance mi = new DerMarshalledInstance(proxy, Collections.emptyList());
            assertNotNull(mi, "marshalling a downloadable smart proxy must succeed (no CNFE)");
        } finally {
            Thread.currentThread().setContextClassLoader(savedTccl);
        }

        assertTrue(CapturingProxyCodebaseProvider.substituteCalled,
                "Service.providers must have FOUND and LOADED the provider via the threaded loader "
                + "(substitute reached) -- proving the null-loader CNFE path is gone");
        ClassLoader captured = CapturingProxyCodebaseProvider.lastStreamLoader;
        assertNotNull(captured, "threaded streamLoader must be non-null (was null: the bug)");
        assertSame(defining, captured,
                "threaded streamLoader must be the object's own defining loader (JOSS getLoader provenance)");
        assertNotSame(throwawayTccl, captured,
                "threaded streamLoader must NOT be the thread-context loader (Warres discipline)");
    }

    /** Unit-level: {@link DerMarshalFactory} threads its loader into {@link DerMarshalInstanceOutput}. */
    @Test
    public void factoryThreadsLoaderIntoOutput() throws Exception {
        ClassLoader marker = new ClassLoader(getClass().getClassLoader()) {};
        DerMarshalFactory factory = new DerMarshalFactory(true, marker);
        OutputStream sink = new ByteArrayOutputStream();
        DerMarshalInstanceOutput out =
                (DerMarshalInstanceOutput) factory.createMarshalOutput(sink, null, List.of());
        assertSame(marker, out.streamLoader(),
                "the factory's loader must reach DerMarshalInstanceOutput (no more null)");
    }
}
