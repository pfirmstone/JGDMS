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

import java.io.IOException;
import java.util.Collection;

import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.loader.ProxyCodebaseSpi;

/**
 * Test {@link ProxyCodebaseSpi} that RECORDS the {@code streamLoader} passed to
 * {@link #substitute(Class, ClassLoader)} so a test can assert exactly which loader
 * {@code DerProxySerializer.create(...)} threaded from the marshalling stream.
 *
 * <p><strong>Not registered globally.</strong> There is deliberately NO
 * {@code META-INF/services/net.jini.loader.ProxyCodebaseSpi} on the test classpath naming this
 * class; it is discovered only through a purpose-built custom {@code ClassLoader} whose
 * {@code getResources} is overridden by {@link DerMarshalLoaderThreadingTest}. This keeps every
 * other DER test on the default no-substitution provider and avoids any cross-test coupling.
 *
 * <p>{@link #resolve} mirrors the platform default no-op provider byte-for-byte so that, even if a
 * decode path did reach it, behaviour would be identical to the built-in fallback.
 */
public final class CapturingProxyCodebaseProvider implements ProxyCodebaseSpi {

    /** The last {@code streamLoader} observed by {@link #substitute}; reset by the test. */
    public static volatile ClassLoader lastStreamLoader;
    /** Whether {@link #substitute} has been reached at all; reset by the test. */
    public static volatile boolean substituteCalled;

    /** Public no-arg constructor required by {@code Service}'s reflective instantiation. */
    public CapturingProxyCodebaseProvider() {}

    /** Resets the recorded state before a test exercises the marshalling path. */
    public static void reset() {
        lastStreamLoader = null;
        substituteCalled = false;
    }

    @Override
    public Object resolve(CodebaseAccessor bootstrapProxy, MarshalledInstance smartProxy,
                          ClassLoader parentLoader, ClassLoader verifierLoader, Collection context)
            throws IOException, ClassNotFoundException {
        // Identical to the platform default no-op ProxyCodebaseSpi.resolve.
        return smartProxy.get(parentLoader, true, verifierLoader, context);
    }

    @Override
    public boolean substitute(Class serviceClass, ClassLoader streamLoader) {
        substituteCalled = true;
        lastStreamLoader = streamLoader;
        return true;
    }
}
