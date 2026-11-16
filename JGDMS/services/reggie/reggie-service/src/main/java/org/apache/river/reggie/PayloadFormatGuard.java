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
package org.apache.river.reggie;

import java.util.Iterator;
import java.util.List;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reggie.proxy.EntryRep;
import org.apache.river.reggie.proxy.Item;

/**
 * Reggie's nested-payload half of the born-immutable format guards
 * (JGDMS-STD-006 sec.3 item 5; SOW-Entry-ATOMIC-DER-Migration.md sec.4 A3,
 * finding A3-F1): binds the self-describing format of every nested payload
 * an untrusted registrant supplies to this registrar's born epoch format,
 * fail-closed.
 *
 * <p><b>The gap this closes.</b> {@code RegistrarImpl.registerDo} and the
 * attribute mutators gate only the client-asserted {@link Item#entryFormat}
 * token. But that token is not the value decode actually dispatches on.
 * Every nested payload -- {@link Item#service}, {@link Item#bootstrapProxy}
 * (each a {@link MarshalledWrapper} around a {@link MarshalledInstance}), and
 * each {@link EntryRep} attribute field's {@code MarshalledInstance} -- is
 * self-describing: it carries its own {@code payloadFormat}, and
 * {@link MarshalledInstance#getMarshalFactory()} resolves the codec from
 * <em>that</em> ({@code factoryForFormat(payloadFormat)}), not from the
 * asserted {@code entryFormat}. A DER-asserting registrant can therefore
 * ship legacy-JOSS ({@code AtomicMarshalledInstance}) payloads that pass the
 * token guard yet decode on the attacker-chosen JOSS codec -- decoded by the
 * registrar itself ({@code lookUp(Template,int)} ->
 * {@code Matches.getProxys()} -> {@code Item.getProxy()} ->
 * {@code bootstrapProxy.get()}) or by browsing clients in a nominally
 * single-format djinn.
 *
 * <p><b>The check.</b> {@link MarshalledInstance#getPayloadFormat()} (a
 * trust-boundary accessor, {@code final} on trunk) returns exactly the token
 * decode dispatch uses, reading no payload bytes. This guard compares that
 * token against the registrar's born format and refuses any mismatch,
 * fail-closed -- so no hostile bytes are ever decoded to make the decision.
 * A {@code null} wrapper / {@code null} field element carries no payload to
 * decode and is accepted (an optional {@code bootstrapProxy} or a wildcard
 * template field).
 *
 * <p>Mirrors Outrigger U1a's {@code OutriggerServerImpl.checkHandbackFormat}
 * (commit {@code ea5a09bd0}): same read-only {@code getPayloadFormat()}
 * probe, same fail-closed {@link IllegalArgumentException} on mismatch, same
 * "before any side effect" placement (the caller invokes these before any
 * lease grant, store mutation, event, or log write). Extracted -- like
 * {@link SnapshotFormatGuard} -- so the decision is unit-testable without
 * standing up a whole {@code RegistrarImpl}.
 */
final class PayloadFormatGuard {

    private PayloadFormatGuard() { throw new AssertionError(); }

    /**
     * Checks all nested payloads carried by a registration {@code Item}:
     * the service proxy, the (optional) bootstrap proxy, and every
     * attribute field of every attribute set.
     *
     * @param nitem the registrant-supplied item
     * @param born  this registrar's born epoch format
     * @throws IllegalArgumentException if any nested payload's format is
     *         not {@code born}
     */
    static void checkItem(Item nitem, MarshallingFormat born) {
	checkWrapper(nitem.service, "service", born);
	checkWrapper(nitem.bootstrapProxy, "bootstrapProxy", born);
	checkAttrSets(nitem.getAttributeSets(), born);
    }

    /**
     * Checks every non-null {@code EntryRep} in an attribute-set array
     * (registration attributes, mutator attributes, or mutator templates).
     *
     * @param sets the attribute-set array, may be {@code null}
     * @param born this registrar's born epoch format
     * @throws IllegalArgumentException if any field payload's format is
     *         not {@code born}
     */
    static void checkAttrSets(EntryRep[] sets, MarshallingFormat born) {
	if (sets == null) return;
	for (int i = sets.length; --i >= 0; )
	    checkEntryFields(sets[i], born);
    }

    /** Checks every non-null field payload of one {@code EntryRep}. */
    private static void checkEntryFields(EntryRep set, MarshallingFormat born) {
	if (set == null) return;
	List flds = set.fields();
	if (flds == null) return;
	for (Iterator it = flds.iterator(); it.hasNext(); ) {
	    Object f = it.next();
	    if (f == null) continue; // wildcard/absent field: nothing to decode
	    checkFormat(((MarshalledInstance) f).getPayloadFormat(),
			"entry attribute field", born);
	}
    }

    /**
     * Checks a {@link MarshalledWrapper} service/bootstrap payload. A
     * {@code null} wrapper carries no payload to decode and is accepted
     * (a null {@code service} is rejected earlier in registerDo; a null
     * {@code bootstrapProxy} is legitimate). The wrapped object is
     * validated to be a {@link MarshalledInstance} at
     * {@link MarshalledWrapper} construction, so the cast is safe.
     */
    private static void checkWrapper(MarshalledWrapper w, String descr,
				     MarshallingFormat born) {
	if (w == null) return;
	checkFormat(
	    ((MarshalledInstance) w.getMarshalledInstance()).getPayloadFormat(),
	    descr, born);
    }

    /** Fail-closed if {@code actual} is not the born format token. */
    private static void checkFormat(String actual, String descr,
				    MarshallingFormat born) {
	final String expected = born.getFormat();
	if (!expected.equals(actual)) {
	    throw new IllegalArgumentException(
		"Rejecting request: nested " + descr + " payload format is "
		+ actual + ", but this registrar's born entry format is "
		+ expected + " (JGDMS-STD-006 sec.3 item 5 / A3-F1: a nested "
		+ "payload's self-describing format must match the registrar "
		+ "epoch; the asserted Item.entryFormat token alone is not "
		+ "trusted). No lease, store entry, event, or log record was "
		+ "created.");
	}
    }
}
