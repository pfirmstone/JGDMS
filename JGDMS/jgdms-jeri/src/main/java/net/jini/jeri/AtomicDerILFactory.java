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
    }

    private static <T> T notNull(T obj) {
        if (obj == null) throw new NullPointerException();
        return obj;
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
                                           getClassLoader());
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
