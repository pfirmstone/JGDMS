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

import java.io.IOException;
import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.entry.AbstractEntry;
import net.jini.io.MarshalledInstance;
import org.apache.river.proxy.MarshalledWrapper;
import org.apache.river.reggie.proxy.EntryRep;
import org.apache.river.reggie.proxy.Item;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Negative + positive coverage for the A3-F1 nested-payload format binding
 * ({@link PayloadFormatGuard}; JGDMS-STD-006 sec.3 item 5).
 *
 * <p>The finding: {@code RegistrarImpl.registerDo} and the attribute mutators
 * gate only the client-asserted {@link Item#entryFormat} token, while every
 * nested payload -- {@link Item#service}, {@link Item#bootstrapProxy}, and
 * each {@link EntryRep} attribute field -- is self-describing and decode
 * dispatches on its OWN {@code payloadFormat}. A DER-asserting registrant can
 * therefore ship legacy-JOSS ({@code AtomicMarshalledInstance}, whose
 * {@code getPayloadFormat()} is {@code "JOSS"}) payloads that the token guard
 * waves through and that the registrar itself later decodes on the
 * attacker-chosen JOSS codec (lookUp -&gt; Item.getProxy -&gt;
 * bootstrapProxy.get) or that browsing clients decode in a nominally
 * single-format djinn.
 *
 * <p><b>No hostile bytes are ever decoded to make the decision.</b> The guard
 * reads {@link MarshalledInstance#getPayloadFormat()} -- a pure final-field
 * read of the same token decode dispatch uses -- and never calls
 * {@code get()}. Every assertion below therefore runs unconditionally: unlike
 * {@code RealWireDerRoundTripTest} (whose decode step Assume-skips on an
 * SM-removed JDK), this suite has no decode step to skip. The exception on a
 * mismatch is a fail-closed {@link IllegalArgumentException} carrying the
 * A3-F1 marker, thrown before any {@code get()} -- which is itself the
 * "refused before decode" proof.
 */
public class PayloadFormatGuardTest {

    private static final MarshallingFormat DER = MarshallingFormat.ATOMIC_DER;
    private static final MarshallingFormat JOSS = MarshallingFormat.JOSS;

    /** A trivial reflection-path Entry with one String field. */
    public static class Attr extends AbstractEntry {
        public String name;
        public Attr() { }
        public Attr(String name) { this.name = name; }
    }

    private static Entry[] entries() { return new Entry[]{ new Attr("server1") }; }

    /** Attribute sets whose field payloads are born in {@code useDer}'s format. */
    private static EntryRep[] attrSets(boolean useDer) throws java.rmi.RemoteException {
        return EntryRep.toEntryRep(entries(), true, useDer);
    }

    /** A service/bootstrap wrapper whose wrapped payload is legacy JOSS. */
    private static MarshalledWrapper jossWrapper() throws IOException {
        return new MarshalledWrapper("payload"); // MarshalledInstance(Object) -> JOSS
    }

    /** A service/bootstrap wrapper whose wrapped payload is real ATOMIC_DER. */
    private static MarshalledWrapper derWrapper() throws IOException {
        return new MarshalledWrapper(new MarshalledInstance("payload",
            Collections.EMPTY_SET, new InvocationConstraints(DER, null)));
    }

    /**
     * Forges the finding's exact adversarial shape via Reggie's own public
     * relay constructor: {@code entryFormat} asserts ATOMIC_DER, but the
     * supplied service/bootstrap/attribute payloads are whatever the caller
     * built. The token check upstream would pass such an Item (its token is
     * ATOMIC_DER); only the payload-format gate can catch it.
     */
    private static Item forgedItem(MarshalledWrapper service,
                                   MarshalledWrapper bootstrap,
                                   EntryRep[] attrs) {
        return new Item(null, null, null, service, attrs, bootstrap, DER);
    }

    private static void assertA3F1Rejected(Runnable check) {
        try {
            check.run();
            fail("expected fail-closed IllegalArgumentException: a nested "
                + "payload whose self-describing format contradicts the "
                + "registrar's born format must be refused (A3-F1)");
        } catch (IllegalArgumentException e) {
            assertTrue("rejection must name the A3-F1 payload-format binding, "
                + "actual message: " + e.getMessage(),
                e.getMessage() != null && e.getMessage().contains("A3-F1"));
        }
    }

    // ── registerDo surface: checkItem ──────────────────────────────────────

    @Test
    public void registerDo_jossServicePayload_refused() throws Exception {
        // DER-born attrs, but the SERVICE payload is JOSS -> refused.
        Item item = forgedItem(jossWrapper(), null, attrSets(true));
        assertA3F1Rejected(() -> PayloadFormatGuard.checkItem(item, DER));
    }

    @Test
    public void registerDo_jossBootstrapPayload_refused() throws Exception {
        // DER service + DER attrs, but the BOOTSTRAP proxy payload is JOSS ->
        // refused: bootstrapProxy is the field the registrar itself later
        // decodes at lookUp -> Item.getProxy() -> bootstrapProxy.get().
        Item item = forgedItem(derWrapper(), jossWrapper(), attrSets(true));
        assertA3F1Rejected(() -> PayloadFormatGuard.checkItem(item, DER));
    }

    @Test
    public void registerDo_jossAttributeFieldPayload_refused() throws Exception {
        // DER service + DER bootstrap, but an ATTRIBUTE field payload is JOSS.
        Item item = forgedItem(derWrapper(), derWrapper(), attrSets(false));
        assertA3F1Rejected(() -> PayloadFormatGuard.checkItem(item, DER));
    }

    @Test
    public void registerDo_allDerPayloads_accepted() throws Exception {
        // The legitimate born-DER registration: nothing is refused, and a
        // null bootstrapProxy (a service that is not bootstrap-eligible) is
        // accepted -- it carries no payload to decode.
        Item item = forgedItem(derWrapper(), null, attrSets(true));
        PayloadFormatGuard.checkItem(item, DER); // must not throw
    }

    // ── attribute-mutator surface: checkAttrSets ───────────────────────────
    // Exercises the shared gate the three mutators (addAttributes,
    // modifyAttributes, setAttributes) each call before any store mutation.

    @Test
    public void mutators_jossAttributeSets_refused() throws Exception {
        EntryRep[] joss = attrSets(false);
        assertA3F1Rejected(() -> PayloadFormatGuard.checkAttrSets(joss, DER));
    }

    @Test
    public void mutators_derAttributeSets_accepted() throws Exception {
        PayloadFormatGuard.checkAttrSets(attrSets(true), DER); // must not throw
    }

    @Test
    public void mutators_nullAndWildcardElementsTolerated() throws Exception {
        // A null EntryRep element (modifyAttributes uses null to mean "delete")
        // and a template field left null (a wildcard) carry no payload to
        // decode and must be tolerated, not NPE'd or spuriously refused.
        PayloadFormatGuard.checkAttrSets(null, DER);
        PayloadFormatGuard.checkAttrSets(new EntryRep[]{ null }, DER);
        // Template with all-null (wildcard) fields, born DER: accepted.
        EntryRep[] tmpl = EntryRep.toEntryRep(new Entry[]{ new Attr(null) }, false, true);
        PayloadFormatGuard.checkAttrSets(tmpl, DER);
    }

    // ── symmetry: a JOSS-born (prior-djinn interop) registrar ──────────────

    @Test
    public void jossRegistrar_derPayload_refused() throws Exception {
        // The mirror direction: a born-JOSS registrar must refuse a DER
        // payload just as fail-closed -- the epoch is single-format either way.
        EntryRep[] der = attrSets(true);
        assertA3F1Rejected(() -> PayloadFormatGuard.checkAttrSets(der, JOSS));
    }

    @Test
    public void jossRegistrar_jossPayload_accepted() throws Exception {
        PayloadFormatGuard.checkAttrSets(attrSets(false), JOSS); // must not throw
    }
}
