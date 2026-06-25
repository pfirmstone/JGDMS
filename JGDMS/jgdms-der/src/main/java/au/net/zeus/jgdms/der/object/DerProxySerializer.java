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

import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.export.CodebaseAccessor;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.loader.ProxyCodebaseSpi;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Resolve;
import org.apache.river.resource.Service;

/**
 * DER-path counterpart of {@code org.apache.river.api.io.ProxySerializer}.
 *
 * <p>It substitutes a downloadable proxy (a {@link DynamicProxyCodebaseAccessor} — i.e. a
 * {@code java.lang.reflect.Proxy} — or a {@link ProxyAccessor} smart proxy) with a carrier
 * holding a bootstrap {@link CodebaseAccessor} proxy plus the real proxy wrapped as a
 * (DER) {@link MarshalledInstance}; on decode it {@code implements Resolve} so the DER
 * decode ({@code DerReplacer.resolve}) calls {@link #readResolve()}, which authenticates
 * and (if needed) downloads the codebase via {@link ProxyCodebaseSpi} before unmarshalling
 * the real proxy — preserving authenticate-before-download.
 *
 * <p><b>Why it lives in jgdms-der, not jgdms-platform.</b> {@code ProxySerializer} is in the
 * local platform; reusing it would make DER proxy support depend on JGDMS platform 4.0,
 * defeating the goal of <em>downloading</em> DER into a JGDMS 3.X node. This class lives in
 * the downloadable {@code jgdms-der} module and reuses only the stable platform SPIs already
 * present in 3.X — {@link CodebaseAccessor} and {@link ProxyCodebaseSpi} (both present at
 * {@code jgdms-3.1.0}) — so it rides the {@code -dl} codebase and resolves using the 3.X
 * node's own local SPIs. It does <em>not</em> use the 4.0 {@code ProxySerializer} rework.
 *
 * <p><b>Why it lives in this package.</b> The {default, verifier} stream class loaders needed
 * for resolution are read DIRECTLY from the {@link DerGetArg} via package-private access —
 * mirroring how the platform {@code ProxySerializer} reaches {@code GetArgImpl.in}. Class
 * loaders are capabilities and must NOT be broadcast via {@code getObjectStreamContext()}
 * (which every object in the graph can read); hence the narrow same-package channel.
 */
@AtomicSerial
public class DerProxySerializer implements Resolve {

    private static final String BOOTSTRAP_PROXY = "bootstrapProxy";
    private static final String SERVICE_PROXY   = "serviceProxy";

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.object");

    /**
     * The bootstrap proxy is limited to these interfaces so additional interfaces of the
     * real proxy (which may not be available before the codebase is downloaded) are not
     * required to reconstruct it.
     */
    private static final Class<?>[] BOOTSTRAP_PROXY_INTERFACES = {
        CodebaseAccessor.class, RemoteMethodControl.class
    };

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm(BOOTSTRAP_PROXY, CodebaseAccessor.class),
            new SerialForm(SERVICE_PROXY, MarshalledInstance.class)
        };
    }

    public static void serialize(PutArg arg, DerProxySerializer ps) throws IOException {
        arg.put(BOOTSTRAP_PROXY, ps.bootstrapProxy);
        arg.put(SERVICE_PROXY, ps.serviceProxy);
        arg.writeArgs();
    }

    private final CodebaseAccessor bootstrapProxy;
    private final MarshalledInstance serviceProxy;
    private final transient Collection<?> context;
    private final transient ClassLoader defaultLoader;
    private final transient ClassLoader verifierLoader;

    DerProxySerializer(CodebaseAccessor bootstrapProxy, MarshalledInstance serviceProxy,
                       Collection<?> context, ClassLoader defaultLoader, ClassLoader verifierLoader) {
        this.bootstrapProxy = bootstrapProxy;
        this.serviceProxy = serviceProxy;
        this.context = context;
        this.defaultLoader = defaultLoader;
        this.verifierLoader = verifierLoader;
    }

    /**
     * {@code @AtomicSerial} decode constructor. The {default, verifier} loaders are read
     * DIRECTLY from {@link DerGetArg} (package-private) rather than from the JOSS-only
     * {@code @ReadInput} back-door or the broadcast {@code getObjectStreamContext()}; they are
     * {@code null} when the decode carries no loaders, in which case the default
     * {@link ProxyCodebaseSpi} unmarshals locally without a codebase download.
     */
    public DerProxySerializer(GetArg arg) throws IOException, ClassNotFoundException {
        this(
            check(notNull(arg.get(BOOTSTRAP_PROXY, null, CodebaseAccessor.class), BOOTSTRAP_PROXY)),
            notNull(arg.get(SERVICE_PROXY, null, MarshalledInstance.class), SERVICE_PROXY),
            arg.getObjectStreamContext(),
            arg instanceof DerGetArg ? ((DerGetArg) arg).streamDefaultLoader() : null,
            arg instanceof DerGetArg ? ((DerGetArg) arg).streamVerifierLoader() : null
        );
    }

    @Override
    public Object readResolve() throws ObjectStreamException {
        try {
            return getProvider(defaultLoader)
                    .resolve(bootstrapProxy, serviceProxy, defaultLoader, verifierLoader, context);
        } catch (IOException | ClassNotFoundException e) {
            InvalidObjectException ioe = new InvalidObjectException("DER proxy resolution failed: " + e);
            ioe.initCause(e);
            throw ioe;
        }
    }

    /** Substitutes a downloadable {@code java.lang.reflect.Proxy}; returns {@code proxy} unchanged if not applicable. */
    public static Object create(DynamicProxyCodebaseAccessor proxy, ClassLoader streamLoader, Collection<?> context)
            throws IOException {
        Class<?> proxyClass = proxy.getClass();
        if (proxy instanceof RemoteMethodControl
                && Proxy.isProxyClass(proxyClass)
                && getProvider(streamLoader).substitute(proxyClass, streamLoader)) {
            InvocationHandler h = Proxy.getInvocationHandler(proxy);
            return new DerProxySerializer(
                    (CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass), BOOTSTRAP_PROXY_INTERFACES, h),
                    new DerMarshalledInstance(proxy, asColl(context)),
                    context, null, null);
        }
        return proxy;
    }

    /** Substitutes a downloadable smart proxy ({@link ProxyAccessor}); returns {@code svc} unchanged if not applicable. */
    public static Object create(ProxyAccessor svc, ClassLoader streamLoader, Collection<?> context)
            throws IOException {
        Object proxy = svc.getProxy();
        Class<?> proxyClass = proxy != null ? proxy.getClass() : null;
        if (proxyClass == null) {
            LOGGER.log(Level.FINE, "Proxy was null for {0}", svc.getClass());
        }
        if (proxy instanceof RemoteMethodControl
                && proxy instanceof CodebaseAccessor
                && getProvider(streamLoader).substitute(proxyClass, streamLoader)) {
            InvocationHandler h = Proxy.getInvocationHandler(proxy);
            return new DerProxySerializer(
                    (CodebaseAccessor) Proxy.newProxyInstance(getProxyLoader(proxyClass), BOOTSTRAP_PROXY_INTERFACES, h),
                    new DerMarshalledInstance(svc, asColl(context)),
                    context, null, null);
        }
        return svc;
    }

    /**
     * The registered {@link ProxyCodebaseSpi}, or a default that performs no codebase
     * download/substitution (it simply unmarshals the serviceProxy locally). Identical policy
     * to {@code ProxySerializer.getProvider}, but reached from the downloadable module.
     */
    private static ProxyCodebaseSpi getProvider(final ClassLoader loader) {
        ProxyCodebaseSpi result = AccessController.doPrivileged((PrivilegedAction<ProxyCodebaseSpi>) () -> {
            Iterator<ProxyCodebaseSpi> it = Service.providers(ProxyCodebaseSpi.class, loader);
            return it.hasNext() ? it.next() : null;
        });
        if (result != null) return result;
        return new ProxyCodebaseSpi() {
            @Override
            public Object resolve(CodebaseAccessor bootstrapProxy, MarshalledInstance smartProxy,
                                  ClassLoader parentLoader, ClassLoader verifierLoader, Collection context)
                    throws IOException, ClassNotFoundException {
                return smartProxy.get(parentLoader, true, verifierLoader, context);
            }
            @Override
            public boolean substitute(Class serviceClass, ClassLoader streamLoader) {
                return false;
            }
        };
    }

    private static ClassLoader getProxyLoader(final Class<?> proxyClass) {
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) proxyClass::getClassLoader);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> asColl(Collection<?> c) {
        return (Collection<Object>) c;
    }

    private static <T> T notNull(T o, String name) throws InvalidObjectException {
        if (o == null) throw new InvalidObjectException(name + " cannot be null");
        return o;
    }

    private static CodebaseAccessor check(CodebaseAccessor c) throws InvalidObjectException {
        if (Proxy.isProxyClass(c.getClass())) return c;
        throw new InvalidObjectException(
                "bootstrap proxy must be a dynamically generated java.lang.reflect.Proxy");
    }
}
