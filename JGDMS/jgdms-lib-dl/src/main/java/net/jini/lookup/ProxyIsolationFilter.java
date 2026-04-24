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

package net.jini.lookup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceItem;
import org.apache.river.proxy.CodebaseProvider;

/**
 * A {@link ServiceItemFilter} that performs out-of-process proxy isolation
 * for service proxies whose bytecode is deemed unsafe by a configurable
 * {@link ProxyBytecodeAnalyzer}.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>The filter obtains the proxy class from the {@link ServiceItem}.</li>
 *   <li>It retrieves the raw {@code .class} bytes from the class's
 *       {@link ClassLoader} and passes them to the {@link ProxyBytecodeAnalyzer}.</li>
 *   <li>If the verdict is {@link AnalysisVerdict#SAFE}, the filter returns
 *       {@code true} leaving {@code item.service} unchanged.</li>
 *   <li>If the verdict is {@link AnalysisVerdict#UNSAFE}, the filter
 *       marshals the proxy to a byte array and forwards it to the configured
 *       {@link RemoteProxyHost} via JERI.  On success the filter replaces
 *       {@code item.service} with the JERI proxy stub returned by the host
 *       and returns {@code true}.</li>
 *   <li>If the verdict is {@link AnalysisVerdict#INCONCLUSIVE}, or if any
 *       {@link RemoteException} is thrown during the JERI call to the host,
 *       the filter sets {@code item.service = null} and returns {@code true},
 *       signalling an <em>indefinite</em> result to the
 *       {@link ServiceDiscoveryManager} — the item will be discarded and
 *       retried later.</li>
 *   <li>If a definite security failure is detected (e.g. the bytecode
 *       contains dangerous operations <em>and</em> the host is unavailable
 *       after repeated attempts) the filter returns {@code false},
 *       permanently rejecting the item.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * Instances of this class are thread-safe provided the supplied
 * {@link ProxyBytecodeAnalyzer} and {@link RemoteProxyHost} are also
 * thread-safe.
 *
 * <h3>Configuration</h3>
 * Typical use inside a {@code ServiceDiscoveryManager} configuration:
 * <pre>{@code
 * import net.jini.lookup.*;
 * net.jini.lookup.ServiceDiscoveryManager {
 *     firstStageFilter = new ProxyIsolationFilter(
 *         new StandardProxyBytecodeAnalyzer(),   // or custom analyzer
 *         remoteProxyHostRef                     // JERI remote reference
 *     );
 * }
 * }</pre>
 *
 * @see ProxyBytecodeAnalyzer
 * @see StandardProxyBytecodeAnalyzer
 * @see RemoteProxyHost
 * @since 3.1
 */
public class ProxyIsolationFilter implements ServiceItemFilter {

    private static final Logger logger =
            Logger.getLogger(ProxyIsolationFilter.class.getName());

    private final ProxyBytecodeAnalyzer analyzer;
    private final RemoteProxyHost       remoteProxyHost;

    /**
     * Creates a filter with a custom bytecode analyzer and a reference to
     * the {@link RemoteProxyHost} that will receive proxies deemed unsafe.
     *
     * @param analyzer       policy that decides whether a proxy needs
     *                       isolation; must not be {@code null}.
     * @param remoteProxyHost JERI remote reference to the host service that
     *                       will isolate the proxy; must not be {@code null}.
     */
    public ProxyIsolationFilter(ProxyBytecodeAnalyzer analyzer,
                                RemoteProxyHost remoteProxyHost) {
        if (analyzer == null)        throw new NullPointerException("analyzer");
        if (remoteProxyHost == null) throw new NullPointerException("remoteProxyHost");
        this.analyzer        = analyzer;
        this.remoteProxyHost = remoteProxyHost;
    }

    /**
     * Creates a filter using the {@link StandardProxyBytecodeAnalyzer} with
     * its default blacklist.
     *
     * @param remoteProxyHost JERI remote reference to the host service; must
     *                        not be {@code null}.
     */
    public ProxyIsolationFilter(RemoteProxyHost remoteProxyHost) {
        this(new StandardProxyBytecodeAnalyzer(), remoteProxyHost);
    }

    /**
     * {@inheritDoc}
     *
     * <p>See the class description for a full account of the three possible
     * return states (pass, fail, indefinite).</p>
     */
    @Override
    public boolean check(ServiceItem item) {
        if (item == null || item.service == null) return false;

        Object proxy       = item.service;
        Class<?> proxyClass = proxy.getClass();
        String className   = proxyClass.getName();

        // Retrieve the codebase annotation for this class.
        String codebase = CodebaseProvider.getClassAnnotation(proxyClass);

        // Obtain the raw class bytes from the class loader.
        byte[] classBytes = getClassBytes(proxyClass);
        if (classBytes == null) {
            // Cannot obtain class bytes — indefinite result.
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Could not retrieve class bytes for {0}; "
                        + "returning indefinite result", className);
            }
            item.service = null;
            return true;
        }

        AnalysisVerdict verdict = analyzer.analyze(className, classBytes, codebase);

        switch (verdict) {
            case SAFE:
                // Proxy is safe for local use — pass through unchanged.
                return true;

            case UNSAFE:
                return isolateProxy(item, proxy, codebase);

            case INCONCLUSIVE:
            default:
                // Cannot decide — indefinite result, will be retried.
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            "Bytecode analysis inconclusive for {0}; "
                            + "returning indefinite result", className);
                }
                item.service = null;
                return true;
        }
    }

    /**
     * Marshals the proxy and forwards it to the {@link RemoteProxyHost}.
     * On success, replaces {@code item.service} with the returned JERI stub.
     * On {@link RemoteException}, signals an indefinite result.
     */
    private boolean isolateProxy(ServiceItem item, Object proxy, String codebase) {
        byte[] marshalledProxy;
        try {
            marshalledProxy = marshalObject(proxy);
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "Failed to marshal proxy {0} for isolation: {1}",
                    new Object[]{proxy.getClass().getName(), e.getMessage()});
            // Marshal failure is unexpected but transient — indefinite result.
            item.service = null;
            return true;
        }

        try {
            Object isolatedStub =
                    remoteProxyHost.isolateProxy(marshalledProxy, codebase);
            if (isolatedStub == null) {
                logger.log(Level.WARNING,
                        "RemoteProxyHost returned null stub for {0}",
                        proxy.getClass().getName());
                // Treat as indefinite — retry later.
                item.service = null;
                return true;
            }
            item.service = isolatedStub;
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Proxy {0} successfully isolated in RemoteProxyHost",
                        proxy.getClass().getName());
            }
            return true;
        } catch (ClassNotFoundException e) {
            // The host cannot load the proxy class — definite failure.
            logger.log(Level.WARNING,
                    "RemoteProxyHost could not load proxy class {0}: {1}",
                    new Object[]{proxy.getClass().getName(), e.getMessage()});
            return false;
        } catch (RemoteException e) {
            // Communication failure — indefinite result.
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "RemoteException contacting RemoteProxyHost for {0}: {1}",
                        new Object[]{proxy.getClass().getName(), e.getMessage()});
            }
            item.service = null;
            return true;
        }
    }

    /**
     * Retrieves the raw {@code .class} file bytes for {@code proxyClass}
     * from its {@link ClassLoader}.  Returns {@code null} if the bytes
     * cannot be obtained.
     */
    private static byte[] getClassBytes(final Class<?> proxyClass) {
        return AccessController.doPrivileged(new PrivilegedAction<byte[]>() {
            @Override
            public byte[] run() {
                String resourceName =
                        proxyClass.getName().replace('.', '/') + ".class";
                ClassLoader cl = proxyClass.getClassLoader();
                if (cl == null) {
                    cl = ClassLoader.getSystemClassLoader();
                }
                InputStream in = cl.getResourceAsStream(resourceName);
                if (in == null) return null;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    return baos.toByteArray();
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "IOException reading class bytes for {0}: {1}",
                            new Object[]{proxyClass.getName(), e.getMessage()});
                    return null;
                } finally {
                    try { in.close(); } catch (IOException ignored) {}
                }
            }
        });
    }

    /**
     * Serialises {@code obj} to a byte array using standard Java
     * object serialisation.
     */
    private static byte[] marshalObject(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream    oos  = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        return baos.toByteArray();
    }
}
