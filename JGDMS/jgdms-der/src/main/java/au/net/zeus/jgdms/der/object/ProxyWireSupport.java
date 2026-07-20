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

import au.net.zeus.jgdms.der.getarg.ResolutionContext;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;

/**
 * Shared read/write-side support for the bare {@code [8]} {@code java.lang.reflect.Proxy} wire
 * item (STD-008 sec.15.2), used by BOTH {@code au.net.zeus.jgdms.der.stream.DerObjectStreamCodec}
 * (top-level object-stream items) and {@link ObjectCodec} (nested {@code @AtomicSerial} record
 * fields) so the tolerant per-name resolution and {@link RawWireFormRetaining raw-wire-form
 * retention} logic exists in exactly one place instead of being duplicated across the two independent {@code [8]}
 * implementations.
 */
public final class ProxyWireSupport {

    /**
     * DoS bound on a {@code [8]} proxy's interface count (mirrors
     * {@code AtomicMarshalInputStream}'s {@code Byte.MAX_VALUE} bound). Single source of truth
     * for both {@code [8]} sites.
     */
    public static final int MAX_PROXY_INTERFACES = 127;

    private static final String[] NOTHING_DROPPED = new String[0];

    private static final Logger LOGGER = Logger.getLogger("au.net.zeus.jgdms.der.object");

    private ProxyWireSupport() {
        throw new AssertionError("no instances");
    }

    /**
     * Static-dispatch seam onto {@link DerProxySerializer#create(DynamicProxyCodebaseAccessor,
     * ClassLoader, Collection)} for callers outside this package. {@link DerProxySerializer} is
     * package-private (matching the shape of its JOSS counterpart, {@code
     * org.apache.river.api.io.ProxySerializer}), so cross-package write seams ({@code
     * DerObjectStreamCodec}, {@code DerMarshalInstanceOutput}) reach it through this forwarder
     * rather than through a public class. Substitutes a downloadable {@code
     * java.lang.reflect.Proxy}; returns {@code proxy} unchanged if not applicable.
     */
    public static Object substituteDownloadableProxy(DynamicProxyCodebaseAccessor proxy,
            ClassLoader streamLoader, Collection<?> context) throws IOException {
        return DerProxySerializer.create(proxy, streamLoader, context);
    }

    /**
     * Static-dispatch seam onto {@link DerProxySerializer#create(ProxyAccessor, ClassLoader,
     * Collection)} for callers outside this package -- see {@link
     * #substituteDownloadableProxy(DynamicProxyCodebaseAccessor, ClassLoader, Collection)}.
     * Substitutes a downloadable smart proxy ({@link ProxyAccessor}); returns {@code svc}
     * unchanged if not applicable.
     */
    public static Object substituteDownloadableProxy(ProxyAccessor svc,
            ClassLoader streamLoader, Collection<?> context) throws IOException {
        return DerProxySerializer.create(svc, streamLoader, context);
    }

    /**
     * Outcome of a tolerant per-name proxy-interface resolution: the resolved {@code Proxy}
     * class, its (narrowed) interfaces, and the subset of wire-declared names that could not be
     * resolved locally ({@code droppedNames.length == 0} iff nothing was dropped).
     */
    public static final class Resolved {
        public final Class<?> proxyClass;
        public final Class<?>[] interfaces;
        public final String[] droppedNames;

        private Resolved(Class<?> proxyClass, Class<?>[] interfaces, String[] droppedNames) {
            this.proxyClass = proxyClass;
            this.interfaces = interfaces;
            this.droppedNames = droppedNames;
        }
    }

    /**
     * Resolves a {@code [8]} proxy's wire-declared interface names against {@code resolution},
     * tolerating individual names that fail to resolve locally instead of failing the whole item
     * (the pre-fix, all-or-nothing behaviour of a single {@link ResolutionContext#loadProxyClass}
     * call).
     *
     * <p>Tries the whole-set {@link ResolutionContext#loadProxyClass} FIRST -- identical to the
     * pre-fix call, and the common case, so a fully-resolvable proxy pays zero extra cost (no
     * per-name loop at all). Only on a {@link ClassNotFoundException} does this fall back to
     * resolving each name individually via {@link ResolutionContext#loadClass}, collecting the
     * subset that resolved and separately logging (at {@code WARNING}, so a partial success never
     * becomes a silent, un-auditable capability narrowing) the names that didn't, then
     * re-deriving the actual endpoint-loader-selected {@code Proxy} class for the narrowed
     * subset via a second {@code loadProxyClass} call (reusing the same preferred-class/OSGi-aware
     * loader-selection logic rather than re-implementing it here).
     *
     * @param names      the FULL wire-declared interface names, in wire order
     * @param resolution the endpoint-assigned resolution context
     * @return the resolution outcome
     * @throws ClassNotFoundException if NONE of {@code names} resolves locally -- a proxy needs
     *                                at least one interface, so this is the same fail-secure
     *                                rejection as before the tolerant-resolution fix
     */
    public static Resolved resolveTolerant(String[] names, ResolutionContext resolution)
            throws ClassNotFoundException {
        try {
            Class<?> proxyClass = resolution.loadProxyClass(names);
            return new Resolved(proxyClass, proxyClass.getInterfaces(), NOTHING_DROPPED);
        } catch (ClassNotFoundException wholeSetFailure) {
            List<String> resolvedNames = new ArrayList<>(names.length);
            List<String> dropped = new ArrayList<>();
            for (String name : names) {
                try {
                    resolution.loadClass(name);
                    resolvedNames.add(name);
                } catch (ClassNotFoundException perNameFailure) {
                    dropped.add(name);
                    LOGGER.log(Level.WARNING,
                            "DER [8] proxy: interface \"{0}\" is not locally resolvable; dropping"
                            + " it from the reconstructed proxy (the narrowed proxy is still"
                            + " usable for its resolvable interfaces, and the original interface"
                            + " list is retained in case this proxy is later re-forwarded) -- {1}",
                            new Object[]{ name, perNameFailure });
                }
            }
            if (resolvedNames.isEmpty()) {
                // NOTE: ClassNotFoundException(String) sets its cause to null explicitly (its
                // own private legacy "ex" field backs getCause()/getException()), NOT the usual
                // Throwable "unset" sentinel -- a later initCause() call would throw
                // IllegalStateException("Can't overwrite cause"). The cause must be supplied via
                // the two-arg constructor instead.
                throw new ClassNotFoundException(
                        "DER [8] proxy: none of the " + names.length
                        + " wire-declared interfaces resolved locally: " + Arrays.toString(names),
                        wholeSetFailure);
            }
            String[] resolvedArray = resolvedNames.toArray(new String[0]);
            Class<?> proxyClass = resolution.loadProxyClass(resolvedArray);
            return new Resolved(proxyClass, proxyClass.getInterfaces(),
                    dropped.toArray(new String[0]));
        }
    }

    /**
     * The exact {@code [8]} TLV content bytes to re-emit verbatim for a live proxy
     * {@code proxyObj}, or {@code null} if {@code proxyObj}'s current handler does not
     * {@linkplain RawWireFormRetaining retain a raw wire form} (the common, nothing-ever-dropped
     * case, and any non-JERI handler) -- in which case the caller must build fresh content from
     * {@code proxyObj.getClass().getInterfaces()} and the live handler, exactly as before this fix.
     *
     * <p>Byte-for-byte re-emission, not name-based re-derivation: see {@link RawWireFormRetaining}.
     *
     * <p><strong>Write-ordering invariant.</strong> Both {@code [8]} write sites
     * ({@code DerObjectStreamCodec} bare-proxy branch and {@code ObjectCodec.encodeProxy}) consult
     * this method FIRST and, on a non-{@code null} result, re-emit those bytes verbatim and return
     * BEFORE reaching the normal handler re-serialization. This ordering is what makes it safe for
     * the retaining handler to be an ordinary {@code @AtomicSerial} handler whose {@code rawForm}
     * is {@code transient} (and thus would be dropped by a normal re-serialization): the raw-bytes
     * relay strictly precedes -- and pre-empts -- that fall-through encode.
     */
    public static byte[] wireContentForBoomerang(Object proxyObj) {
        InvocationHandler live = Proxy.getInvocationHandler(proxyObj);
        return live instanceof RawWireFormRetaining r ? r.rawForm() : null;
    }
}
