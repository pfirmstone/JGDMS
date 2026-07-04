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
 * and/or dispatch sets.
 *
 * <p>It is the atomic-serialization sibling of {@link ProxyTrustILFactory}: where
 * {@code ProxyTrustILFactory} hard-codes appending the single
 * {@link net.jini.security.proxytrust.ProxyTrust} interface (and requires the impl
 * to be a {@code ServerProxyTrust}), {@code DynamicILFactory} is
 * <em>parameterized</em> by arbitrary sets of extra interfaces supplied at
 * construction, and imposes <em>no</em> marker-interface requirement on the remote
 * object.
 *
 * <p>Because it extends {@link AtomicILFactory}, proxies produced by this factory
 * enforce integrity at the object layer (DER / atomic input validation); the
 * general appended-interface behaviour is layered on that hardened base, not on
 * the legacy JOSS {@code BasicILFactory}.
 *
 * <p>The JGDMS service framework installs {@code DynamicILFactory} as the default
 * exporter for {@code DYNAMIC} (and {@code SMART}) services, supplying the Jini
 * admin interfaces as the extra sets, so a dynamic service gets full admin dispatch
 * with neither per-service code generation nor configuration.
 *
 * <h2>Two disjoint extra-interface sets</h2>
 * <p>{@link AbstractILFactory} feeds the exported stub from two places: the client
 * proxy's cast set comes from {@link #getProxyInterfaces} (which combines
 * {@link #getRemoteInterfaces} with the extra proxy interfaces such as
 * {@code RemoteMethodControl}), and the server invocation-dispatcher method set
 * comes from {@link AbstractILFactory#getInvocationDispatcherMethods}, which draws
 * its methods only from {@link #getRemoteInterfaces}.  {@code DynamicILFactory}
 * exposes that split through two <em>disjoint</em> interface sets supplied at
 * construction:
 * <ul>
 *   <li><b>{@code castAndDispatch}</b> -- interfaces appended to BOTH the client
 *       proxy cast set AND the server dispatcher (the classic "extra" behaviour,
 *       mirroring {@code ProxyTrustILFactory}'s single {@code ProxyTrust} append).
 *       These join {@link #getRemoteInterfaces}, so they reach both the dispatcher
 *       and -- because they flow through {@code super.getProxyInterfaces} -- the
 *       client stub.</li>
 *   <li><b>{@code dispatchOnly}</b> -- interfaces appended to the server dispatcher
 *       ONLY, and deliberately <em>stripped</em> from the client proxy cast set.
 *       Their methods' JRMP hashes are registered on the server (so a separate
 *       facet proxy that shares this export can dispatch them), but the exported
 *       {@link java.lang.reflect.Proxy} does not implement those interfaces.  This
 *       is the mechanism by which a Jini service stub is {@code Administrable} yet
 *       not {@code JoinAdmin}/{@code DestroyAdmin}: the admin interfaces dispatch
 *       over the one export, reached through the separate {@code getAdmin()} facet.</li>
 * </ul>
 * The two sets are disjoint by contract; there is no subset relationship between
 * them.  {@link #getRemoteInterfaces} returns
 * {@code super.getRemoteInterfaces} &cup; {@code castAndDispatch} &cup;
 * {@code dispatchOnly} (so every extra interface gets a dispatcher), while
 * {@link #getProxyInterfaces} returns {@code super.getProxyInterfaces} MINUS
 * {@code dispatchOnly} (so the dispatch-only interfaces never reach the client
 * stub).
 *
 * <p>This class is new and unreleased, so its constructor signatures are free to
 * change; the four-argument constructor is retained for the common case where the
 * caller wants the classic single "extra" set (mapped to {@code castAndDispatch},
 * with an empty {@code dispatchOnly}).
 *
 * @author  Peter.
 * @since 3.1.1
 * @see ProxyTrustILFactory
 * @see AtomicILFactory
 */
public class DynamicILFactory extends AtomicILFactory {

    /**
     * Interfaces appended to BOTH the exported client stub's cast set AND the
     * server invocation dispatcher.  Never {@code null} (an empty array is stored
     * for a {@code null} argument); defensively copied in and out.
     */
    private final Class[] castAndDispatch;

    /**
     * Interfaces appended to the <em>server invocation dispatcher</em> ONLY (so
     * their method hashes are registered) and stripped from the exported
     * <em>client</em> stub's cast set.  Disjoint from {@link #castAndDispatch}:
     * these interfaces flow into {@link #getRemoteInterfaces} (so the dispatcher
     * picks them up) and are then removed from the proxy interface list by the
     * {@link #getProxyInterfaces} override.  Never {@code null} (an empty array is
     * stored for a {@code null} argument); defensively copied.
     */
    private final Class[] dispatchOnly;

    /**
     * Creates a <code>DynamicILFactory</code> with the specified server
     * constraints, permission class, class loader, and cast-and-dispatch
     * interfaces.  The server constraints, permission class, and loader carry
     * exactly the
     * {@link AtomicILFactory#AtomicILFactory(MethodConstraints, Class, ClassLoader)
     * AtomicILFactory} semantics; {@code castAndDispatch} is the set of additional
     * interfaces appended to both the client stub's cast set and the server
     * dispatcher via {@link #getRemoteInterfaces}.  The {@code dispatchOnly} set is
     * empty.
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permissionClass   the permission class, or <code>null</code>
     * @param loader            the class loader
     * @param castAndDispatch   the additional interfaces to append to both the
     *                          client stub and the server dispatcher, or
     *                          <code>null</code> for none
     * @throws IllegalArgumentException if the permission class is abstract, is not a
     *         subclass of {@link java.security.Permission}, or does not have a public
     *         constructor that has either one <code>String</code> parameter or one
     *         {@link java.lang.reflect.Method} parameter and has no declared exceptions
     * @throws NullPointerException if loader is null
     */
    public DynamicILFactory(MethodConstraints serverConstraints,
                            Class permissionClass,
                            ClassLoader loader,
                            Class[] castAndDispatch)
    {
        this(serverConstraints, permissionClass, loader, castAndDispatch, null);
    }

    /**
     * Creates a <code>DynamicILFactory</code> with the specified server
     * constraints, permission class, class loader, cast-and-dispatch interfaces,
     * and dispatch-only interfaces.  Identical to
     * {@link #DynamicILFactory(MethodConstraints, Class, ClassLoader, Class[])}
     * except that {@code dispatchOnly} names the (disjoint) set of interfaces whose
     * methods should be dispatchable on the server but which should NOT appear on
     * the exported client stub's cast set (see the class-level <em>Two disjoint
     * extra-interface sets</em> section).
     *
     * @param serverConstraints the server constraints, or <code>null</code>
     * @param permissionClass   the permission class, or <code>null</code>
     * @param loader            the class loader
     * @param castAndDispatch   the additional interfaces to append to both the
     *                          client stub and the server dispatcher, or
     *                          <code>null</code> for none
     * @param dispatchOnly      the interfaces to register on the server dispatcher
     *                          but strip from the client stub, or <code>null</code>
     *                          for none
     * @throws IllegalArgumentException if the permission class is abstract, is not a
     *         subclass of {@link java.security.Permission}, or does not have a public
     *         constructor that has either one <code>String</code> parameter or one
     *         {@link java.lang.reflect.Method} parameter and has no declared exceptions
     * @throws NullPointerException if loader is null
     */
    public DynamicILFactory(MethodConstraints serverConstraints,
                            Class permissionClass,
                            ClassLoader loader,
                            Class[] castAndDispatch,
                            Class[] dispatchOnly)
    {
        super(serverConstraints, permissionClass, loader);
        this.castAndDispatch = copy(castAndDispatch);
        this.dispatchOnly = copy(dispatchOnly);
    }

    /**
     * Creates a <code>DynamicILFactory</code> with the specified server
     * constraints, proxy or service implementation class, and cast-and-dispatch
     * interfaces.  The server constraints and {@code proxyOrServiceImplClass} carry
     * exactly the {@link AtomicILFactory#AtomicILFactory(MethodConstraints, Class)
     * AtomicILFactory} semantics -- the permission class is {@code null} and the
     * class loader is that of {@code proxyOrServiceImplClass}; {@code castAndDispatch}
     * is the set of additional interfaces appended to both the client stub and the
     * server dispatcher via {@link #getRemoteInterfaces}.  The {@code dispatchOnly}
     * set is empty.
     *
     * @param serverConstraints       the server constraints, or <code>null</code>
     * @param proxyOrServiceImplClass the smart-proxy implementation class or the
     *                                service interface class (supplies the class
     *                                loader); must not be <code>null</code>
     * @param castAndDispatch         the additional interfaces to append to both the
     *                                client stub and the server dispatcher, or
     *                                <code>null</code> for none
     * @throws SecurityException if the caller doesn't have {@link RuntimePermission}
     *         "getClassLoader"
     * @throws NullPointerException if proxyOrServiceImplClass is null
     */
    public DynamicILFactory(MethodConstraints serverConstraints,
                            Class proxyOrServiceImplClass,
                            Class[] castAndDispatch)
    {
        super(serverConstraints, proxyOrServiceImplClass);
        this.castAndDispatch = copy(castAndDispatch);
        this.dispatchOnly = new Class[0];
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
     * dispatchable and (for the {@link #getProxyInterfaces} default) implementable
     * by the proxy: the result of
     * {@link AtomicILFactory#getRemoteInterfaces super.getRemoteInterfaces},
     * followed by the {@code castAndDispatch} interfaces and then the
     * {@code dispatchOnly} interfaces supplied at construction.
     *
     * <p>Both extra sets are appended here because the server dispatcher method set
     * ({@link AbstractILFactory#getInvocationDispatcherMethods}) is built solely
     * from {@code getRemoteInterfaces}: every extra interface -- whether it also
     * appears on the client stub or not -- must be present here to register its
     * method hashes.  The {@code dispatchOnly} interfaces are then subtracted from
     * the client stub by the {@link #getProxyInterfaces} override.
     *
     * <p>Unlike {@link ProxyTrustILFactory#getRemoteInterfaces}, this imposes no
     * marker-interface requirement on {@code impl}: the extra interfaces are general
     * (e.g. the Jini admin interfaces) and are appended unconditionally.
     *
     * @param impl the remote object being exported
     * @return the remote interface set with both extra sets appended
     * @throws ExportException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    protected Class[] getRemoteInterfaces(Remote impl) throws ExportException {
        Class[] ifs = super.getRemoteInterfaces(impl);
        int extra = castAndDispatch.length + dispatchOnly.length;
        if (extra == 0) {
            return ifs;
        }
        int base = (ifs == null) ? 0 : ifs.length;
        Class[] all = new Class[base + extra];
        int n = 0;
        for (int i = 0; i < base; i++) {
            all[n++] = ifs[i];
        }
        for (int i = 0; i < castAndDispatch.length; i++) {
            all[n++] = castAndDispatch[i];
        }
        for (int i = 0; i < dispatchOnly.length; i++) {
            all[n++] = dispatchOnly[i];
        }
        return all;
    }

    /**
     * Returns the interfaces the exported client stub (a
     * {@link java.lang.reflect.Proxy}) should implement:
     * {@link AbstractILFactory#getProxyInterfaces super.getProxyInterfaces}
     * (which is {@link #getRemoteInterfaces} -- including both extra sets -- plus
     * the extra proxy interfaces such as {@code RemoteMethodControl}) MINUS the
     * {@link #dispatchOnly} set.
     *
     * <p>This is the sole point at which the client cast set and the server
     * dispatcher set diverge.  {@code getRemoteInterfaces} is deliberately NOT
     * overridden to strip {@code dispatchOnly}: the dispatcher method set is built
     * from {@code getRemoteInterfaces}, so leaving the dispatch-only interfaces in
     * {@code getRemoteInterfaces} keeps their JRMP method hashes registered on the
     * server, while removing them here keeps them off the client stub.
     *
     * @param impl the remote object being exported
     * @return the client proxy interface set with the dispatch-only interfaces removed
     * @throws ExportException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    protected Class[] getProxyInterfaces(Remote impl) throws ExportException {
        Class[] all = super.getProxyInterfaces(impl);
        if (dispatchOnly.length == 0) {
            return all;
        }
        return subtract(all, dispatchOnly);
    }

    /**
     * Returns a new array containing the elements of {@code source} that are not
     * present (by reference or {@code equals}) in {@code remove}, preserving the
     * order of {@code source}.
     */
    private static Class[] subtract(Class[] source, Class[] remove) {
        Class[] tmp = new Class[source.length];
        int n = 0;
        for (int i = 0; i < source.length; i++) {
            boolean drop = false;
            for (int j = 0; j < remove.length; j++) {
                if (source[i] == remove[j]
                        || (source[i] != null && source[i].equals(remove[j]))) {
                    drop = true;
                    break;
                }
            }
            if (!drop) {
                tmp[n++] = source[i];
            }
        }
        if (n == tmp.length) {
            return tmp;
        }
        Class[] result = new Class[n];
        System.arraycopy(tmp, 0, result, 0, n);
        return result;
    }

    /**
     * Returns a hash code that additionally reflects the {@code castAndDispatch}
     * and {@code dispatchOnly} interface sets, so that two factories differing only
     * in their appended-interface configuration hash differently.
     */
    @Override
    public int hashCode() {
        int h = super.hashCode();
        h = 31 * h + java.util.Arrays.hashCode(castAndDispatch);
        h = 31 * h + java.util.Arrays.hashCode(dispatchOnly);
        return h;
    }

    /**
     * Compares the specified object with this factory for equality.  In addition
     * to the {@link AbstractILFactory#equals superclass} check (same class and
     * loader), the {@code castAndDispatch} and {@code dispatchOnly} interface sets
     * must match: two {@code DynamicILFactory} instances that append different
     * interface sets are NOT equal, because they produce differently-shaped stubs
     * and dispatchers.
     */
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        DynamicILFactory other = (DynamicILFactory) obj;
        return java.util.Arrays.equals(castAndDispatch, other.castAndDispatch)
                && java.util.Arrays.equals(dispatchOnly, other.dispatchOnly);
    }
}
