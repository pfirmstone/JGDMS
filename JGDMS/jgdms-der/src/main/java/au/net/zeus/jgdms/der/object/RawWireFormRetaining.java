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

/**
 * Capability of an {@link InvocationHandler} to carry, <em>immutably</em>, the exact raw wire
 * bytes of the {@code [8]} {@code java.lang.reflect.Proxy} item it was decoded from, so that a
 * later re-forward of the (possibly interface-narrowed) proxy re-emits those bytes verbatim
 * rather than re-deriving fresh bytes from the narrowed live proxy.
 *
 * <h2>Why a handler capability rather than a wrapper</h2>
 * <p>When a {@code [8]} proxy's wire-declared interface list can only be resolved PARTIALLY at a
 * node (see {@link ProxyWireSupport#resolveTolerant}), the live {@code Proxy} this node builds is
 * narrower than what the sender held. If this node later re-encodes (forwards) that narrowed
 * proxy, naively reflecting on {@code getClass().getInterfaces()} would silently, permanently lose
 * the dropped interfaces on every future hop. The fix retains the exact raw {@code [8]} TLV
 * content bytes as received off the wire and relays them verbatim on re-forward -- preserving the
 * sender's {@code @AtomicSerial}-validated integrity guarantee, which a freshly re-derived
 * encoding (even a semantically equivalent one) would not.
 *
 * <p>Rather than wrapping the decoded handler in a separate decode-local handler (which would make
 * {@code Proxy.getInvocationHandler(proxy)} return the wrapper, not the handler installed on the
 * proxy -- breaking the load-bearing {@code Proxy.getInvocationHandler(proxy) != this} self-check
 * that JERI's {@code createMarshalInputStream}/{@code setConstraints} enforce as a security
 * invariant), the retained bytes are carried by the JERI handler ITSELF, immutably: the handler
 * installed on the narrowed proxy IS the real handler, so the self-check passes naturally with no
 * wrapper in the way.
 *
 * <h2>Scope of the retention guarantee (what it does and does not cover)</h2>
 * <p><strong>Best-effort, not universal -- non-JERI handlers.</strong>
 * {@link ProxyWireSupport#wrapForDrop} retains only for a decoded handler that implements this
 * interface. Every JERI handler ({@code BasicInvocationHandler} and its subclasses
 * {@code AtomicInvocationHandler}/{@code AtomicDerInvocationHandler}) does. A non-JERI
 * {@code InvocationHandler} on the {@code [8]} path is still built into a usable narrowed proxy,
 * but carries no retained form: a later re-forward silently re-derives from the narrowed live
 * proxy (dropping the unresolved interfaces). This is benign -- it is exactly the pre-retention
 * behaviour for ALL handlers -- so retention is a best-effort improvement, not a guarantee that
 * spans every handler type.
 *
 * <p><strong>Bare-{@code [8]} encode path only -- not smart-proxy codebase substitution.</strong>
 * The guarantee applies to the bare-{@code [8]} {@code java.lang.reflect.Proxy} encode path (the
 * {@code DerObjectStreamCodec} bare-proxy branch and {@code ObjectCodec.encodeProxy}), where the
 * write site consults {@link ProxyWireSupport#wireContentForBoomerang} before any fresh encode. It
 * does NOT cover a proxy re-homed via smart-proxy codebase substitution (the {@code
 * DerObjectStreamCodec} {@code substituteProxies} branch, which is OFF by default): that branch
 * runs earlier in the write path, so a substitutable proxy diverted to the downloadable-carrier
 * ({@code [1]}) path never reaches the retention fence and its retained form is not relayed (that
 * hop re-derives, non-verbatim). Where reachable at all, the object is being intentionally re-homed
 * as a codebase proxy, so byte-for-byte relay of the original bare-{@code [8]} form is not the
 * intended behaviour there; the divergent shape (a bare-{@code [8]}-decoded proxy that is also a
 * substitutable {@code ProxyAccessor}) is near-unreachable in normal operation. Impact is fidelity
 * loss only -- never a self-check bypass, gadget, or authorization change.
 *
 * <h2>Immutability</h2>
 * <p>{@link #withRawForm(byte[])} MUST return a new, independent handler carrying a defensive copy
 * of {@code rawForm}; it must not mutate the receiver. The retained bytes are a transient concern
 * (a decode-local relay hint) and MUST NOT participate in the handler's serial form, {@code
 * equals}/{@code hashCode}, or wire-visible state -- a retaining handler must serialize identically
 * to an otherwise-equal non-retaining one.
 *
 * @since 4.0
 */
public interface RawWireFormRetaining {

    /**
     * Returns a new, independent {@link InvocationHandler} equal in every wire-visible respect to
     * this one, additionally carrying (a defensive copy of) {@code rawForm} as its retained
     * original {@code [8]} wire form. Implementations MUST preserve their concrete type so a
     * decoded handler is not silently downgraded to a base type on re-wrap.
     *
     * @param rawForm the FULL {@code [8]} TLV content bytes received off the wire (not including
     *                the outer tag+length header); may be {@code null} to carry none
     * @return an immutable copy of this handler carrying {@code rawForm}
     */
    InvocationHandler withRawForm(byte[] rawForm);

    /**
     * Returns (a defensive copy of) the retained original {@code [8]} wire form, or {@code null}
     * if this handler carries none (the common, nothing-ever-dropped case).
     *
     * @return the retained original serialized form, or {@code null} if none
     */
    byte[] rawForm();
}
