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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Decode-local {@link InvocationHandler} wrapper for a bare {@code [8]}
 * {@code java.lang.reflect.Proxy} wire item (STD-008 sec.15.2) whose wire-declared interface
 * list could only be resolved PARTIALLY at this node.
 *
 * <h2>Why this exists</h2>
 * <p>Both {@code [8]} read sites ({@code DerObjectStreamCodec.readObject} and
 * {@code ObjectCodec.decodeProxy}) resolve each wire-declared interface name independently
 * (see {@link ProxyWireSupport#resolveTolerant}) rather than failing the whole item the moment
 * a single name doesn't resolve locally. When one or more names are dropped, the live
 * {@code Proxy} this node builds is unavoidably narrower than what the sender actually held. If
 * this node later re-encodes (forwards) that narrowed proxy, naively reflecting on
 * {@code obj.getClass().getInterfaces()} would see only the narrowed set and silently,
 * permanently lose the dropped interfaces on every future hop -- a receiver that once narrows a
 * proxy has zero memory of what was dropped, and a later forward could hand the same object to a
 * downstream party that <em>could</em> have resolved the missing piece.
 *
 * <p>This wrapper carries the pristine, wire-received interface name list alongside the real
 * handler so a later write can re-emit the FULL original list (see
 * {@link ProxyWireSupport#interfaceNamesForWrite} / {@link ProxyWireSupport#handlerForWrite}),
 * while {@link #invoke} still purely delegates to the real handler -- zero behaviour change for
 * whichever interfaces this node itself needs to call through the narrowed local proxy.
 *
 * <h2>Never a wire type</h2>
 * <p>{@code TolerantProxyHandler} is constructed ONLY as a decode-time artefact and is NEVER
 * itself serialized: the write-side fix ({@link ProxyWireSupport#handlerForWrite}) always
 * unwraps it back to the real handler before writing the {@code [1]} handler item, so the wire
 * item this node forwards is shape-identical to what a fully-resolving node would have sent. It
 * therefore carries no {@code @AtomicSerial} annotation and no serial form -- attempting to
 * write one directly through the DER codec fails fast (not {@code @AtomicSerial}).
 */
public final class TolerantProxyHandler implements InvocationHandler {

    private final InvocationHandler realHandler;
    private final String[] originalInterfaceNames;

    /**
     * @param realHandler            the real, wire-received handler, unmodified
     * @param originalInterfaceNames the FULL wire-declared interface name list, in wire order,
     *                               before any per-node filtering (defensively copied)
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code originalInterfaceNames} is empty (a proxy needs
     *                                  at least one interface)
     */
    public TolerantProxyHandler(InvocationHandler realHandler, String[] originalInterfaceNames) {
        this.realHandler = Objects.requireNonNull(realHandler, "realHandler");
        Objects.requireNonNull(originalInterfaceNames, "originalInterfaceNames");
        if (originalInterfaceNames.length == 0) {
            throw new IllegalArgumentException("originalInterfaceNames must not be empty");
        }
        this.originalInterfaceNames = originalInterfaceNames.clone();
    }

    /**
     * Pure delegation to {@link #realHandler} -- zero behaviour change for any interface that
     * did resolve locally. This handler adds no logic of its own beyond retaining the original
     * interface name list for a possible later re-forward.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return realHandler.invoke(proxy, method, args);
    }

    /** The real, wire-received handler this wrapper delegates to, unmodified. */
    public InvocationHandler realHandler() {
        return realHandler;
    }

    /**
     * The full original wire-declared interface name list (defensive copy), in wire order,
     * before any per-node filtering.
     */
    public String[] originalInterfaceNames() {
        return originalInterfaceNames.clone();
    }

    @Override
    public String toString() {
        return "TolerantProxyHandler{realHandler=" + realHandler
                + ", originalInterfaceNames=" + Arrays.toString(originalInterfaceNames) + '}';
    }
}
