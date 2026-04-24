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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.activation.ActivationExporter;
import net.jini.activation.arg.ActivationID;
import net.jini.export.Exporter;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * Activatable JERI service that hosts service proxies in an isolated JVM.
 *
 * <p>When activated, this service accepts marshalled proxy bytes via its
 * {@link RemoteProxyHost} interface, deserialises the proxy into an isolated
 * per-codebase {@link ClassLoader}, exports the proxy object via JERI using
 * a {@link BasicJeriExporter} over a {@link TcpServerEndpoint}, and returns
 * the resulting JERI proxy stub to the caller.</p>
 *
 * <p>Once a proxy is hosted here, all method invocations from the client JVM
 * travel over the JERI transport to this JVM, preventing direct local
 * execution of potentially unsafe proxy bytecode.</p>
 *
 * <h3>Per-codebase ClassLoader isolation</h3>
 * A separate {@link URLClassLoader} is maintained for each unique codebase
 * string.  Proxies sharing the same codebase are loaded into the same
 * {@link ClassLoader}; proxies from different codebases are fully isolated
 * from each other.
 *
 * <h3>Activation</h3>
 * This class has a two-argument constructor
 * {@code (ActivationID activationID, String[] data)} that satisfies the
 * Phoenix activation group contract.  When running non-activatably (e.g. in
 * a test harness) use the zero-argument constructor instead; in that case a
 * plain {@link BasicJeriExporter} is used.
 *
 * <h3>Thread safety</h3>
 * This class is thread-safe.
 *
 * @see RemoteProxyHost
 * @see ProxyIsolationFilter
 * @since 3.1
 */
public class ProxyHostActivatable implements RemoteProxyHost {

    private static final Logger logger =
            Logger.getLogger(ProxyHostActivatable.class.getName());

    /** JERI exporter used to export this service and each hosted proxy. */
    private final Exporter exporter;

    /**
     * Cache of per-codebase ClassLoaders.  The key is the normalised
     * codebase string (or {@code ""} for the bootstrap class loader).
     */
    private final ConcurrentMap<String, ClassLoader> classLoaderCache =
            new ConcurrentHashMap<String, ClassLoader>();

    /**
     * Constructs a non-activatable instance for use in testing or embedding.
     * The service is <em>not</em> exported by this constructor; call
     * {@link #export()} explicitly when ready to accept calls.
     *
     * @throws RemoteException if constructing the exporter fails.
     */
    public ProxyHostActivatable() throws RemoteException {
        this.exporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicILFactory(null, null,
                        ProxyHostActivatable.class.getClassLoader()),
                false,
                true);
    }

    /**
     * Constructs an activatable instance.  This constructor signature is
     * required by the Phoenix activation group.  After construction the
     * service is automatically exported using an {@link ActivationExporter}
     * wrapping a {@link BasicJeriExporter}.
     *
     * @param activationID the activation identifier assigned to this service
     *                     by the activation system.
     * @param data         activation data strings (currently unused; may be
     *                     {@code null} or empty).
     * @throws RemoteException if the service cannot be exported.
     */
    public ProxyHostActivatable(ActivationID activationID, String[] data)
            throws RemoteException {
        this.exporter = new ActivationExporter(
                activationID,
                new BasicJeriExporter(
                        TcpServerEndpoint.getInstance(0),
                        new AtomicILFactory(null, null,
                                ProxyHostActivatable.class.getClassLoader()),
                        false,
                        true));
        export();
    }

    /**
     * Exports this service via the configured {@link Exporter} and returns
     * the JERI proxy stub.  Called automatically from the activatable
     * constructor; must be called manually when using the no-arg constructor.
     *
     * @return the JERI remote proxy for this service.
     * @throws ExportException if the service cannot be exported.
     */
    public RemoteProxyHost export() throws ExportException {
        return (RemoteProxyHost) exporter.export(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deserialises the proxy contained in {@code marshalledProxy} using an
     * isolated {@link ClassLoader} for the given {@code codebase}, exports
     * the proxy via a new {@link BasicJeriExporter}, and returns the
     * resulting JERI stub.</p>
     *
     * @throws RemoteException      if export of the hosted proxy fails
     *                              (indefinite — retry is appropriate).
     * @throws ClassNotFoundException if a required class cannot be found in
     *                              {@code codebase} (definite failure).
     */
    @Override
    public Object isolateProxy(byte[] marshalledProxy, String codebase)
            throws RemoteException, ClassNotFoundException {
        if (marshalledProxy == null)
            throw new NullPointerException("marshalledProxy");

        ClassLoader proxyLoader;
        try {
            proxyLoader = getOrCreateClassLoader(codebase);
        } catch (java.net.MalformedURLException e) {
            throw new RemoteException("Malformed codebase URL: " + codebase, e);
        }

        Object proxy = unmarshalProxy(marshalledProxy, proxyLoader);

        return exportProxy(proxy, proxyLoader);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns an existing {@link ClassLoader} for {@code codebase}, creating
     * one if it does not yet exist.
     */
    private ClassLoader getOrCreateClassLoader(String codebase)
            throws MalformedURLException {
        String key = (codebase == null) ? "" : codebase.trim();
        ClassLoader existing = classLoaderCache.get(key);
        if (existing != null) return existing;

        ClassLoader created = buildClassLoader(key);
        ClassLoader raced = classLoaderCache.putIfAbsent(key, created);
        return (raced != null) ? raced : created;
    }

    /**
     * Creates a new {@link URLClassLoader} for the given (normalised)
     * codebase string.  If the codebase is empty the system class loader
     * is used.
     */
    private static ClassLoader buildClassLoader(String codebase)
            throws MalformedURLException {
        if (codebase.isEmpty()) {
            return ClassLoader.getSystemClassLoader();
        }
        StringTokenizer st = new StringTokenizer(codebase);
        URL[] urls = new URL[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            urls[i] = new URL(st.nextToken());
        }
        return new URLClassLoader(urls,
                ProxyHostActivatable.class.getClassLoader());
    }

    /**
     * Deserialises the proxy bytes using the supplied {@link ClassLoader}.
     */
    private static Object unmarshalProxy(byte[] marshalledProxy,
                                         ClassLoader loader)
            throws RemoteException, ClassNotFoundException {
        ClassLoader prev =
                Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(marshalledProxy)) {
                @Override
                protected Class<?> resolveClass(
                        java.io.ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    try {
                        return Class.forName(desc.getName(), false, loader);
                    } catch (ClassNotFoundException e) {
                        return super.resolveClass(desc);
                    }
                }
            };
            return ois.readObject();
        } catch (IOException e) {
            throw new RemoteException("Failed to unmarshal proxy", e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /**
     * Exports the given proxy object via a new {@link BasicJeriExporter} and
     * returns the JERI stub.  The proxy is exported using an
     * {@link AtomicILFactory} so that all interfaces implemented by the proxy
     * are accessible via JERI reflection.
     */
    private static Object exportProxy(Object proxy,
                                       ClassLoader proxyLoader)
            throws RemoteException {
        if (!(proxy instanceof java.rmi.Remote)) {
            // Proxy does not implement Remote; verify it has at least one
            // interface before wrapping it in a RemoteProxyWrapper for export.
            Class<?>[] ifaces = getAllInterfaces(proxy.getClass());
            if (ifaces.length == 0) {
                throw new ExportException(
                        "Proxy " + proxy.getClass().getName()
                        + " does not implement any interface that can be "
                        + "exported via JERI");
            }
        }

        Exporter proxyExporter = new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new AtomicILFactory(null, null, proxyLoader),
                false,
                true);

        if (proxy instanceof java.rmi.Remote) {
            return proxyExporter.export((java.rmi.Remote) proxy);
        }

        // If not Remote, wrap via a RemoteProxyWrapper
        RemoteProxyWrapper wrapper =
                new RemoteProxyWrapper(proxy);
        return proxyExporter.export(wrapper);
    }

    /**
     * Returns all interfaces declared by the given class and its superclasses.
     */
    private static Class<?>[] getAllInterfaces(Class<?> cls) {
        java.util.List<Class<?>> list = new java.util.ArrayList<Class<?>>();
        for (Class<?> iface : cls.getInterfaces()) {
            list.add(iface);
        }
        if (Proxy.isProxyClass(cls)) {
            return list.toArray(new Class[0]);
        }
        Class<?> parent = cls.getSuperclass();
        while (parent != null && parent != Object.class) {
            for (Class<?> iface : parent.getInterfaces()) {
                if (!list.contains(iface)) list.add(iface);
            }
            parent = parent.getSuperclass();
        }
        return list.toArray(new Class[0]);
    }

    /**
     * Adapter that wraps a non-{@link java.rmi.Remote} proxy so that it can
     * be exported via JERI.  All method calls are forwarded by reflection.
     */
    private static final class RemoteProxyWrapper
            implements java.rmi.Remote {

        private final Object delegate;

        RemoteProxyWrapper(Object delegate) {
            this.delegate = delegate;
        }

        /**
         * Forwards a method call to the delegate by name and parameter types.
         *
         * @param methodName      the name of the method to invoke.
         * @param parameterTypes  the parameter types of the method.
         * @param args            the arguments to pass.
         * @return the return value of the delegate method.
         * @throws RemoteException          if the invocation fails remotely.
         * @throws ReflectiveOperationException if the method cannot be found
         *                                  or invoked.
         */
        public Object invoke(String methodName,
                             Class<?>[] parameterTypes,
                             Object[] args)
                throws RemoteException, ReflectiveOperationException {
            try {
                java.lang.reflect.Method m =
                        delegate.getClass().getMethod(methodName, parameterTypes);
                return m.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RemoteException)
                    throw (RemoteException) cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException) cause;
                if (cause instanceof Error)
                    throw (Error) cause;
                throw new RemoteException("Proxy invocation failed", cause);
            }
        }
    }
}
