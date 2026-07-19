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
package org.apache.river.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;

import org.apache.river.resource.testspi.FixtureSpi;
import org.junit.Assume;
import org.junit.Test;

/**
 * FIX&nbsp;2 regression for the {@link Service} {@code LazyIterator} null-loader asymmetry.
 *
 * <p>When {@code loader == null}, {@code hasNext()} scanned {@code ClassLoader.getSystemResources}
 * (finding a provider declared on the system/application classpath) but {@code next()} resolved the
 * class with {@code LoadClass.forName(cn, true, null)} -- i.e. the BOOTSTRAP loader, which cannot
 * see a system-classpath provider -- so the lookup died with a spurious
 * {@code ServiceConfigurationError} ("Provider ... not found"). The fix resolves against
 * {@code ClassLoader.getSystemClassLoader()} when {@code loader == null}, matching the resource
 * scan and the documented "system loader if null" contract.
 */
public class ServiceNullLoaderTest {

    private static final String SPI_RESOURCE =
            "META-INF/services/" + FixtureSpi.class.getName();

    private static boolean visibleToSystemResources() throws IOException {
        Enumeration<URL> e = ClassLoader.getSystemResources(SPI_RESOURCE);
        return e != null && e.hasMoreElements();
    }

    /**
     * {@code Service.providers(FixtureSpi.class, null)} must FIND and LOAD the system-classpath
     * provider. Before the fix this threw a {@code ServiceConfigurationError}; after it, the
     * provider instantiates.
     */
    @Test
    public void nullLoaderResolvesSystemClasspathProvider() throws Exception {
        // Only meaningful when the surefire fork exposes test-classes via the system loader
        // (the default). Skip rather than false-fail under exotic classloader isolation.
        Assume.assumeTrue("provider must be visible via getSystemResources for this scenario",
                visibleToSystemResources());

        Iterator<FixtureSpi> it = Service.providers(FixtureSpi.class, null);
        assertTrue("hasNext must see the system-classpath provider file", it.hasNext());
        FixtureSpi provider = it.next();   // pre-fix: ServiceConfigurationError (bootstrap CNFE)
        assertNotNull("null-loader lookup must LOAD the provider, not just find its name", provider);
        assertEquals("fixture", provider.id());
    }
}
