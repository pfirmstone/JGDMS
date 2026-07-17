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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared read/write-side support for the bare {@code [8]} {@code java.lang.reflect.Proxy} wire
 * item (STD-008 sec.15.2), used by BOTH {@code au.net.zeus.jgdms.der.stream.DerObjectStreamCodec}
 * (top-level object-stream items) and {@link ObjectCodec} (nested {@code @AtomicSerial} record
 * fields) so the tolerant per-name resolution and {@link TolerantProxyHandler} wrap/unwrap logic
 * exists in exactly one place instead of being duplicated across the two independent {@code [8]}
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
     * Wraps {@code decodedHandler} in a {@link TolerantProxyHandler} carrying the FULL original
     * wire-declared {@code originalNames}, UNLESS {@code decodedHandler} is already a
     * {@code TolerantProxyHandler} -- in which case it is returned unchanged, so an earlier hop's
     * retained original-name list is never overwritten by this hop's own (possibly narrower or
     * simply different) view (idempotent across multiple hops: the retained list belongs to
     * whichever hop first saw the full original wire form).
     *
     * <p>Callers should invoke this ONLY when at least one interface was dropped at this hop
     * ({@code Resolved.droppedNames.length > 0}); the nothing-dropped case should use
     * {@code decodedHandler} directly, unwrapped -- zero overhead, zero behaviour change.
     *
     * @param decodedHandler the real handler just decoded off the wire for this hop
     * @param originalNames  this hop's own wire-declared interface name list (the full list this
     *                       hop received, before its own filtering)
     */
    public static InvocationHandler wrapForDrop(InvocationHandler decodedHandler, String[] originalNames) {
        if (decodedHandler instanceof TolerantProxyHandler already) {
            return already;
        }
        return new TolerantProxyHandler(decodedHandler, originalNames);
    }

    /**
     * The interface names to WRITE for a live proxy {@code proxyObj}: the retained original list
     * if {@code proxyObj}'s current handler is a {@link TolerantProxyHandler} (so a previously
     * narrowed proxy re-forwards its pristine original interface set, not the narrowed runtime
     * set -- the write-side landmine fix), else {@code proxyObj.getClass().getInterfaces()}'s
     * names, exactly as before this fix.
     */
    public static String[] interfaceNamesForWrite(Object proxyObj) {
        InvocationHandler live = Proxy.getInvocationHandler(proxyObj);
        if (live instanceof TolerantProxyHandler tph) {
            return tph.originalInterfaceNames();
        }
        Class<?>[] ifaces = proxyObj.getClass().getInterfaces();
        String[] names = new String[ifaces.length];
        for (int i = 0; i < ifaces.length; i++) {
            names[i] = ifaces[i].getName();
        }
        return names;
    }

    /**
     * The handler to WRITE for a live proxy {@code proxyObj}: the unwrapped real handler if
     * {@code proxyObj}'s current handler is a {@link TolerantProxyHandler} (so the re-emitted
     * wire item's {@code [1]} handler position is shape-identical to what a fully-resolving node
     * would have sent -- {@code TolerantProxyHandler} is never itself a wire type), else the live
     * handler unchanged, exactly as before this fix.
     */
    public static InvocationHandler handlerForWrite(Object proxyObj) {
        InvocationHandler live = Proxy.getInvocationHandler(proxyObj);
        return live instanceof TolerantProxyHandler tph ? tph.realHandler() : live;
    }
}
