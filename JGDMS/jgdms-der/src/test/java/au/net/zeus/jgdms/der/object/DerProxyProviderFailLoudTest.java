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
package au.net.zeus.jgdms.der.object;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.DynamicProxyCodebaseAccessor;
import org.apache.river.resource.ServiceConfigurationError;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.Collections;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIX&nbsp;3 (FAIL-LOUD): when a {@code ProxyCodebaseSpi} provider IS declared in
 * {@code META-INF/services} but cannot be loaded/instantiated,
 * {@code DerProxySerializer.getProvider} must surface a clear
 * {@link ServiceConfigurationError} -- naming the SPI and the loader used -- and must NOT quietly
 * fall back to the no-substitution provider (which would silently drop codebase substitution: a
 * capability downgrade). This asserts the failure is raised, is diagnostic, and preserves the
 * underlying cause.
 */
public class DerProxyProviderFailLoudTest {

    private static final String SPI_RESOURCE = "META-INF/services/net.jini.loader.ProxyCodebaseSpi";
    private static final String BOGUS_PROVIDER = "com.example.NonExistentProxyCodebaseProvider";

    /** A loader that advertises a NON-LOADABLE provider name for the ProxyCodebaseSpi service file only. */
    private static final class BogusServiceLoader extends ClassLoader {
        private final URL serviceUrl;

        BogusServiceLoader(ClassLoader parent, URL serviceUrl) {
            super(parent);
            this.serviceUrl = serviceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            // Return ONLY the bogus file for the SPI path (do not union the parent's real
            // providers, so the unloadable name is the one Service tries to instantiate).
            if (SPI_RESOURCE.equals(name)) {
                return Collections.enumeration(Collections.singletonList(serviceUrl));
            }
            return super.getResources(name);
        }
    }

    @Test
    public void listedButUnloadableProviderFailsLoudNotSilentDowngrade() throws Exception {
        File svcFile = File.createTempFile("BogusProxyCodebaseSpi-", ".services");
        svcFile.deleteOnExit();
        Files.write(svcFile.toPath(), (BOGUS_PROVIDER + "\n").getBytes(StandardCharsets.UTF_8));
        URL svcUrl = svcFile.toURI().toURL();

        ClassLoader parent = getClass().getClassLoader();
        BogusServiceLoader bogusLoader = new BogusServiceLoader(parent, svcUrl);

        // A downloadable dynamic-proxy shape so create() reaches getProvider(streamLoader).
        DynamicProxyCodebaseAccessor proxy = (DynamicProxyCodebaseAccessor) Proxy.newProxyInstance(
                parent,
                new Class<?>[]{ DynamicProxyCodebaseAccessor.class, RemoteMethodControl.class },
                (p, m, a) -> null);

        ServiceConfigurationError err = assertThrows(ServiceConfigurationError.class,
                () -> DerProxySerializer.create(proxy, bogusLoader, Collections.emptyList()),
                "a listed-but-unloadable provider must fail loud, not downgrade to the no-op provider");

        String msg = err.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("net.jini.loader.ProxyCodebaseSpi"),
                "diagnostic must name the SPI; was: " + msg);
        assertTrue(msg.contains("loader ["),
                "diagnostic must name the loader used for the lookup; was: " + msg);
        assertTrue(msg.contains("no-substitution"),
                "diagnostic must state the refusal to downgrade to the no-substitution provider; was: " + msg);
        assertNotNull(err.getCause(), "the underlying ServiceConfigurationError must be preserved as the cause");
    }
}
