/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import au.net.zeus.jgdms.der.DerInputLimitControl;
import au.net.zeus.jgdms.der.DerInputLimits;
import java.lang.reflect.InvocationHandler;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;

/**
 * An {@link InvocationLayerFactory} that wires DER (JGDMS-STD-006) encoding
 * into JERI (JGDMS-STD-008 sec.14, A0).
 *
 * <p>A service whose objects implement {@code @AtomicSerial} can switch from
 * JOSS to DER by replacing its {@link InvocationLayerFactory} with a
 * {@code AtomicDerILFactory} -- no other service code change is required.
 *
 * <p>DER has no compression and carries no codebase annotations.
 *
 * <p>Usage example (server side):
 * <pre>
 *   BasicJeriExporter exporter = new BasicJeriExporter(
 *       TcpServerEndpoint.getInstance(0),
 *       new AtomicDerILFactory(null, MyService.class));
 * </pre>
 *
 * @author peter
 * @since 4.0
 */
public class AtomicDerILFactory extends BasicILFactory {

    /** Per-deployment DoS limits for reading client invocation arguments (see {@link DerInputLimits}). */
    private final DerInputLimits limits;

    /**
     * Creates a {@code AtomicDerILFactory} with the specified server constraints,
     * permission class, and class loader.
     *
     * @param serverConstraints the server constraints, or {@code null}
     * @param permissionClass the permission class, or {@code null}
     * @param loader the class loader (must not be {@code null})
     * @throws NullPointerException if {@code loader} is null
     */
    public AtomicDerILFactory(MethodConstraints serverConstraints,
                        Class permissionClass,
                        ClassLoader loader) {
        super(serverConstraints, permissionClass, notNull(loader));
        this.limits = DerInputLimits.DEFAULT;
    }

    /**
     * Creates a {@code AtomicDerILFactory} with the specified server constraints
     * and proxy-or-service-implementation class.  The class loader of
     * {@code proxyOrServiceImplClass} is used.
     *
     * @param serverConstraints the server constraints, or {@code null}
     * @param proxyOrServiceImplClass the smart-proxy class or service
     *        interface (must not be {@code null})
     * @throws NullPointerException if {@code proxyOrServiceImplClass} is null
     */
    public AtomicDerILFactory(MethodConstraints serverConstraints,
                        Class proxyOrServiceImplClass) {
        super(serverConstraints, null, proxyOrServiceImplClass.getClassLoader());
        this.limits = DerInputLimits.DEFAULT;
    }

    /**
     * Creates a {@code AtomicDerILFactory} with the specified server constraints, proxy-or-service
     * class, and per-deployment {@link DerInputLimits} for reading client invocation arguments.
     * A service sets its own input cap from its {@code net.jini.config.Configuration}, e.g.
     * {@code new AtomicDerILFactory(null, MyService.class, DerInputLimits.maxBytes(64 * 1024 * 1024))}.
     *
     * @param serverConstraints the server constraints, or {@code null}
     * @param proxyOrServiceImplClass the smart-proxy class or service interface (must not be null)
     * @param limits the DoS limits for the argument stream (must not be {@code null})
     * @throws NullPointerException if {@code proxyOrServiceImplClass} or {@code limits} is null
     */
    public AtomicDerILFactory(MethodConstraints serverConstraints,
                        Class proxyOrServiceImplClass,
                        DerInputLimits limits) {
        super(serverConstraints, null, proxyOrServiceImplClass.getClassLoader());
        this.limits = notNull(limits);
    }

    /**
     * Creates a {@code AtomicDerILFactory} with the specified server constraints,
     * permission class, and proxy-or-service-implementation class.
     *
     * @param serverConstraints the server constraints, or {@code null}
     * @param permissionClass the permission class, or {@code null}
     * @param proxyOrServiceImplClass the smart-proxy class or service
     *        interface (must not be {@code null})
     * @throws NullPointerException if {@code proxyOrServiceImplClass} is null
     */
    public AtomicDerILFactory(MethodConstraints serverConstraints,
                        Class permissionClass,
                        Class proxyOrServiceImplClass) {
        super(serverConstraints, permissionClass,
              proxyOrServiceImplClass.getClassLoader());
        this.limits = DerInputLimits.DEFAULT;
    }

    private static <T> T notNull(T obj) {
        if (obj == null) throw new NullPointerException();
        return obj;
    }

    /**
     * Adds {@link DerInputLimitControl} to the proxy's interfaces (beyond the
     * {@code RemoteMethodControl} and {@code TrustEquivalence} added by the superclass), so a client
     * can set its own per-deployment return-value DoS cap on a received proxy via the standard
     * proxy-control idiom.
     *
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    protected Class[] getExtraProxyInterfaces(Remote impl) {
        Class[] base = super.getExtraProxyInterfaces(impl); // {RemoteMethodControl, TrustEquivalence}
        Class[] out = new Class[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = DerInputLimitControl.class;
        return out;
    }

    /**
     * Returns a new {@link AtomicDerInvocationHandler} for the specified object
     * endpoint and this factory's server constraints.
     *
     * @throws NullPointerException if any element of {@code interfaces} or
     *         {@code impl} is null
     */
    @Override
    protected InvocationHandler createInvocationHandler(Class[] interfaces,
                                                        Remote impl,
                                                        ObjectEndpoint oe)
            throws ExportException {
        for (int i = interfaces.length; --i >= 0; ) {
            if (interfaces[i] == null) {
                throw new NullPointerException();
            }
        }
        if (impl == null) {
            throw new NullPointerException();
        }
        return new AtomicDerInvocationHandler(oe, getServerConstraints());
    }

    /**
     * Returns a new {@link AtomicDerInvocationDispatcher} for the specified methods,
     * remote object, and server capabilities.
     *
     * @throws NullPointerException if {@code impl} is null
     */
    @Override
    protected InvocationDispatcher createInvocationDispatcher(Collection methods,
                                                              Remote impl,
                                                              ServerCapabilities caps)
            throws ExportException {
        if (impl == null) {
            throw new NullPointerException("impl is null");
        }
        return new AtomicDerInvocationDispatcher(methods, caps,
                                           getServerConstraints(),
                                           getPermissionClass(),
                                           getClassLoader(),
                                           limits);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AtomicDerILFactory) return super.equals(o);
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        return hash ^ super.hashCode();
    }
}
