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
 * <h2>Byte-for-byte retention, not name-based re-derivation</h2>
 * <p>This wrapper carries the exact raw {@code [8]} TLV content bytes as received off the wire
 * (see {@link #originalWireBytes()}), NOT a parsed/re-derivable name list. A later re-forward
 * (see {@link ProxyWireSupport#wireContentForBoomerang}) copies those bytes verbatim into the
 * re-emitted {@code [8]} item instead of re-encoding fresh bytes from parsed state. DER
 * length/tag encoding is canonical given content bytes, so the re-emitted item is byte-identical
 * to what this node originally received -- relaying the sender's exact bytes preserves the
 * sender's {@code @AtomicSerial}-validated integrity guarantee across the hop, which a freshly
 * re-derived encoding (even one that is semantically equivalent) would not: a freshly re-encoded
 * item is this node's OWN new encoding, not the originally protected blob.
 *
 * <p>This is STD-009 sec.6.4's own "boomerang" pattern -- relay the retained wire form, never
 * re-derive from a live/stripped proxy -- applied at the DER {@code [8]} wire-item granularity
 * rather than requiring an explicit {@code MarshalledInstance}-typed field at every application
 * call site. {@link #invoke} still purely delegates to the real handler -- zero behaviour change
 * for whichever interfaces this node itself needs to call through the narrowed local proxy.
 *
 * <h2>Never a wire type</h2>
 * <p>{@code BoomerangProxyHandler} is constructed ONLY as a decode-time artefact and is NEVER
 * itself serialized: the write-side fix ({@link ProxyWireSupport#wireContentForBoomerang})
 * always unwraps it back to the raw retained bytes before writing, so the wire item this node
 * forwards is shape-identical to what a fully-resolving node would have sent. It therefore
 * carries no {@code @AtomicSerial} annotation and no serial form -- attempting to write one
 * directly through the DER codec fails fast (not {@code @AtomicSerial}).
 */
public final class BoomerangProxyHandler implements InvocationHandler {

    private final InvocationHandler realHandler;
    private final byte[] originalWireBytes;

    /**
     * @param realHandler       the real, wire-received handler, unmodified
     * @param originalWireBytes the FULL wire-received {@code [8]} TLV content bytes (not
     *                          including the outer tag+length header), before any per-node
     *                          filtering (defensively copied)
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code originalWireBytes} is empty (a proxy needs at
     *                                  least one interface, so its wire content can never be
     *                                  empty)
     */
    public BoomerangProxyHandler(InvocationHandler realHandler, byte[] originalWireBytes) {
        this.realHandler = Objects.requireNonNull(realHandler, "realHandler");
        Objects.requireNonNull(originalWireBytes, "originalWireBytes");
        if (originalWireBytes.length == 0) {
            throw new IllegalArgumentException("originalWireBytes must not be empty");
        }
        this.originalWireBytes = originalWireBytes.clone();
    }

    /**
     * Pure delegation to {@link #realHandler} -- zero behaviour change for any interface that
     * did resolve locally. This handler adds no logic of its own beyond retaining the original
     * wire bytes for a possible later re-forward.
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
     * The full original {@code [8]} TLV content bytes (defensive copy), exactly as received off
     * the wire, before any per-node filtering.
     */
    public byte[] originalWireBytes() {
        return originalWireBytes.clone();
    }

    @Override
    public String toString() {
        return "BoomerangProxyHandler{realHandler=" + realHandler
                + ", originalWireBytes.length=" + originalWireBytes.length + '}';
    }
}
