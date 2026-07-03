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

package net.jini.jeri;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import net.jini.core.constraint.MethodConstraints;

/**
 * A general, reusable invocation-layer factory for dynamic-proxy exports that must
 * force <em>additional</em> interfaces -- typically non-{@link Remote} admin
 * interfaces such as {@link net.jini.admin.Administrable},
 * {@link net.jini.admin.JoinAdmin} and
 * {@link org.apache.river.admin.DestroyAdmin} -- onto the exported stub's proxy
 * and dispatch sets.
 *
 * <p>It is the atomic-serialization sibling of {@link ProxyTrustILFactory}: where
 * {@code ProxyTrustILFactory} hard-codes appending the single
 * {@link net.jini.security.proxytrust.ProxyTrust} interface (and requires the impl
 * to be a {@code ServerProxyTrust}), {@code DynamicILFactory} is
 * <em>parameterized</em> by an arbitrary set of extra interfaces supplied at
 * construction, and imposes <em>no</em> marker-interface requirement on the remote
 * object.  The extra interfaces are appended to whatever
 * {@link #getRemoteInterfaces super.getRemoteInterfaces} returns, exactly as
 * {@code ProxyTrustILFactory} appends {@code ProxyTrust}.  That single override
 * feeds BOTH the exported stub's interface set (the client cast set) and the
 * server invocation-dispatcher set, because
 * {@code AbstractILFactory.getInvocationDispatcherMethods} draws its methods only
 * from {@code getRemoteInterfaces}.
 *
 * <p>Because it extends {@link AtomicILFactory}, proxies produced by this factory
 * enforce integrity at the object layer (DER / atomic input validation); the
 * general appended-interface behaviour is layered on that hardened base, not on
 * the legacy JOSS {@code BasicILFactory}.
 *
 * <p>The JGDMS service framework installs {@code DynamicILFactory} as the default
 * exporter for {@code DYNAMIC} (and {@code SMART}) services, supplying the Jini
 * admin interfaces as the extra set, so a dynamic service gets full admin dispatch
 * with neither per-service code generation nor configuration.
 *
 * @author  Peter.
 * @since 3.1.1
 * @see ProxyTrustILFactory
 * @see AtomicILFactory
 */
public class DynamicILFactory extends AtomicILFactory {

    /**
     * The additional (typically non-{@code Remote}) interfaces to force onto the
     * exported stub's proxy and dispatch sets.  Never {@code null} (an empty array
     * is stored for a {@code null} argument); defensively copied in and out.
     */
    private final Class[] extra;

    /**
     * Creates a <code>DynamicILFactory</code> with the specified server
     * constraints, permission class, class loader, and extra interfaces.  The
     * server constraints, permission class, and loader carry exactly the
     * {@link AtomicILFactory#AtomicILFactory(MethodConstraints, Class, ClassLoader)
     * AtomicILFactory} semantics; {@code extra} is the set of additional interfaces
     * appended to every exported stub via {@link #getRemoteInterfaces}.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permissionClass   the permission class, or <code>null</code>
     * @param loader            the class loader
     * @param extra             the additional interfaces to append to the exported
     *                          stub, or <code>null</code> for none
     * @throws IllegalArgumentException if the permission class is abstract, is not a
     *         subclass of {@link java.security.Permission}, or does not have a public
     *         constructor that has either one <code>String</code> parameter or one
     *         {@link java.lang.reflect.Method} parameter and has no declared exceptions
     * @throws NullPointerException if loader is null
     */
    public DynamicILFactory(MethodConstraints serverConstraints,
                            Class permissionClass,
                            ClassLoader loader,
                            Class[] extra)
    {
        super(serverConstraints, permissionClass, loader);
        this.extra = copy(extra);
    }

    /**
     * Creates a <code>DynamicILFactory</code> with the specified server
     * constraints, proxy or service implementation class, and extra interfaces.
     * The server constraints and {@code proxyOrServiceImplClass} carry exactly the
     * {@link AtomicILFactory#AtomicILFactory(MethodConstraints, Class)
     * AtomicILFactory} semantics -- the permission class is {@code null} and the
     * class loader is that of {@code proxyOrServiceImplClass}; {@code extra} is the
     * set of additional interfaces appended to every exported stub via
     * {@link #getRemoteInterfaces}.
     *
     * @param serverConstraints       the server constraints, or <code>null</code>
     * @param proxyOrServiceImplClass the smart-proxy implementation class or the
     *                                service interface class (supplies the class
     *                                loader); must not be <code>null</code>
     * @param extra                   the additional interfaces to append to the
     *                                exported stub, or <code>null</code> for none
     * @throws SecurityException if the caller doesn't have {@link RuntimePermission}
     *         "getClassLoader"
     * @throws NullPointerException if proxyOrServiceImplClass is null
     */
    public DynamicILFactory(MethodConstraints serverConstraints,
                            Class proxyOrServiceImplClass,
                            Class[] extra)
    {
        super(serverConstraints, proxyOrServiceImplClass);
        this.extra = copy(extra);
    }

    private static Class[] copy(Class[] a) {
        if (a == null) {
            return new Class[0];
        }
        Class[] c = new Class[a.length];
        System.arraycopy(a, 0, c, 0, a.length);
        return c;
    }

    /**
     * Returns a new array containing the remote interfaces that should be
     * implemented by the proxy: the result of
     * {@link AtomicILFactory#getRemoteInterfaces super.getRemoteInterfaces},
     * followed by the extra interfaces supplied at construction.
     *
     * <p>Unlike {@link ProxyTrustILFactory#getRemoteInterfaces}, this imposes no
     * marker-interface requirement on {@code impl}: the extra interfaces are
     * general (e.g. the Jini admin interfaces) and are appended unconditionally.
     * Both {@code super.getRemoteInterfaces(impl)} and the extra set are
     * null-guarded.
     *
     * @param impl the remote object being exported
     * @return the remote interface set with the extra interfaces appended
     * @throws ExportException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    protected Class[] getRemoteInterfaces(Remote impl) throws ExportException {
        Class[] ifs = super.getRemoteInterfaces(impl);
        if (extra == null || extra.length == 0) {
            return ifs;
        }
        if (ifs == null) {
            return copy(extra);
        }
        Class[] all = new Class[ifs.length + extra.length];
        System.arraycopy(ifs, 0, all, 0, ifs.length);
        System.arraycopy(extra, 0, all, ifs.length, extra.length);
        return all;
    }
}
