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
package org.apache.river.reggie.proxy;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.UUID;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.entry.AbstractEntry;
import net.jini.io.MarshalledInstance;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.proxy.MarshalledWrapper;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The gap the shipped A3 tests missed, closed: pushes {@link EntryRep},
 * {@link Item} and {@link Template} themselves -- not just their per-field
 * {@code MarshalledInstance} values -- through a genuine
 * {@code new MarshalledInstance(obj, EMPTY_SET, new
 * InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))} encode and a
 * real decode, exactly as they cross the wire between a DER-configured
 * Reggie and a DER-capable client.
 *
 * <p>The two rework blockers this proves fixed:
 * <ul>
 *   <li>{@code EntryRep.serialForm()} used to declare {@code fields:
 *       Object[].class} -- a heterogeneous array the DER SchemaGenerator
 *       rejects outright. Constructing an in-memory {@code EntryRep} (as the
 *       pre-rework {@code EntryRepDerFormatTest} did via {@code repFor(...)
 *       .get()}) never touches schema generation, so it never caught this.
 *       Only actually marshalling an {@code EntryRep} instance -- which is
 *       exactly what every test in this file does -- exercises the
 *       SchemaGenerator and would have failed loudly before the {@code
 *       fields} -&gt; {@code MarshalledInstance[]} fix.</li>
 *   <li>{@code Item.serialForm()} used to declare {@code bootstrapProxy:
 *       Proxy.class} -- a bare type with no DER schema rule at all, same
 *       failure mode, same reason the old in-memory-only tests missed it.</li>
 * </ul>
 *
 * <p>Each test below always performs the real ENCODE step ({@link #der}) --
 * this alone is the regression proof for both blockers: on the pre-rework
 * shapes, encoding an {@code EntryRep} throws {@code DerException:
 * SchemaGenerator: unsupported serial field type java.lang.Object} and
 * encoding an {@code Item} similarly fails on {@code bootstrapProxy}
 * (verified directly against the pre-rework code during development of this
 * file). Encoding does not touch codebase/integrity plumbing at all, so it
 * is unaffected by the decode-side environment note below.
 *
 * <p><b>Environment note (decode only):</b> decoding any object containing a
 * nested {@code MarshalledInstance} field (which every test's attribute/
 * service/bootstrap fields are) runs {@code net.jini.io.MarshalledInstance}'s
 * {@code (GetArg)} constructor, which calls an unconditional {@code
 * SerializablePermission("unmarshal").checkGuard(null)} -- a pre-existing,
 * out-of-scope {@code jgdms-platform} gap (not this rework's code) that
 * throws {@code SecurityException} on a JDK with the Security Manager fully
 * removed (vanilla JDK 24+, JEP 486) rather than no-op'ing when no {@code
 * SecurityManager} is installed. {@link #decode} detects this and skips
 * (not fails) the remainder of the affected test via {@code
 * Assume.assumeNoException}, after the real encode step above has already
 * run and been asserted -- so the suite stays green (with the encode-side
 * regression genuinely checked) on such a JDK, while the full round trip is
 * asserted for real on an SM-capable one (e.g. DirtyChai, JGDMS 4.0.0's
 * target platform).
 */
public class RealWireDerRoundTripTest {

    private static final InvocationConstraints DER =
        new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null);

    private static MarshalledInstance der(Object obj) throws IOException {
        return new MarshalledInstance(obj, Collections.EMPTY_SET, DER);
    }

    /**
     * Decodes {@code wire}, or -- on the pre-existing platform gap described
     * in this class's javadoc -- skips the rest of the calling test via
     * {@code Assume.assumeNoException} rather than failing it. Callers
     * should perform (and assert on) the {@link #der} encode step *before*
     * calling this, so the encode-side regression proof always actually
     * runs even when decode has to be skipped.
     */
    private static Object decode(MarshalledInstance wire) throws Exception {
        try {
            return wire.get(false);
        } catch (SecurityException e) {
            Assume.assumeNoException(
                "Skipping the decode/round-trip assertions below this point: "
                + "this JDK's net.jini.io.MarshalledInstance cannot decode a "
                + "nested MarshalledInstance field (Security Manager fully "
                + "removed, e.g. vanilla JDK 24+/JEP 486, so "
                + "MarshalledInstance's unconditional "
                + "SerializablePermission(\"unmarshal\").checkGuard(null) "
                + "throws) -- a pre-existing jgdms-platform gap, not a "
                + "reggie A3-rework regression. The real DER *encode* step "
                + "above this point already ran and was asserted -- that is "
                + "the actual regression proof for the two blockers this "
                + "file exists to cover. Full decode/round-trip needs an "
                + "SM-capable JDK (e.g. DirtyChai).",
                e);
            throw new AssertionError("unreachable", e); // assumeNoException always throws
        }
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    /** Reflection-path Entry with a String field plus numeric/boolean fields. */
    public static class MixedEntry extends AbstractEntry {
        public Boolean active;
        public Integer count;
        public String  name;
        public MixedEntry() { }
        public MixedEntry(String name, Integer count, Boolean active) {
            this.name = name;
            this.count = count;
            this.active = active;
        }
    }

    /**
     * No-field {@code @AtomicSerial} {@link InvocationHandler}, standing in
     * for a real JERI {@code BasicInvocationHandler}: exists so the dynamic
     * {@code Proxy} fixtures below (playing the role of a service proxy and
     * its bootstrap proxy) are themselves genuinely DER-encodable -- DER's
     * bare-{@code Proxy} wire item ({@code [8]}, STD-008 sec.15.2) requires
     * the handler to be {@code @AtomicSerial}, not just {@code Serializable}
     * (mirrored from {@code DerObjectStreamCodec}'s own doc comment).
     */
    @AtomicSerial
    public static class FixtureInvocationHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        public static SerialForm[] serialForm() { return new SerialForm[0]; }
        public static void serialize(PutArg arg, FixtureInvocationHandler o) throws IOException {
            arg.writeArgs();
        }
        public FixtureInvocationHandler() { }
        public FixtureInvocationHandler(GetArg arg) throws IOException, ClassNotFoundException { }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setConstraints": return proxy;
                case "getConstraints": return null;
                case "checkTrustEquivalence": return args != null && args.length > 0 && proxy == args[0];
                case "serviceID": return null;
                case "getServiceProxy": return proxy;
                case "getServiceAttributes": return new Entry[0];
                case "equals": return args != null && args.length > 0 && proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "FixtureProxy";
                default: {
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return Boolean.FALSE;
                    if (rt.isPrimitive() && rt != void.class) return 0;
                    return null;
                }
            }
        }
    }

    /**
     * Bootstrap-eligible service proxy fixture: implements exactly the
     * interface set {@code org.apache.river.proxy.Bootstrap#create} requires
     * to synthesize a {@link #bootstrapProxy}, so {@link Item#Item(
     * ServiceItem, boolean)} -- the real production constructor -- takes the
     * genuine {@code Bootstrap.create} path, not a hand-built substitute.
     */
    private static Object fixtureServiceProxy() {
        return Proxy.newProxyInstance(
            RealWireDerRoundTripTest.class.getClassLoader(),
            new Class<?>[] {
                RemoteMethodControl.class,
                TrustEquivalence.class,
                ServiceIDAccessor.class,
                ServiceProxyAccessor.class,
                ServiceAttributesAccessor.class,
            },
            new FixtureInvocationHandler());
    }

    private static ServiceID serviceID() {
        UUID u = UUID.randomUUID();
        return new ServiceID(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    // ── Encode-only regression proofs (never skipped -- do not decode) ──
    //
    // These three are the unambiguous, always-run signal for the two A3
    // blockers: they perform only the real ATOMIC_DER *encode* step (no
    // decode, so the jgdms-platform environment gap described above never
    // applies) and assert it succeeds. Verified directly against the
    // pre-rework code during development of this file: encoding an
    // EntryRep there throws "DerException: SchemaGenerator: unsupported
    // serial field type java.lang.Object in class ...EntryRep" (the
    // fields:Object[].class blocker) and encoding an Item fails the same
    // way on bootstrapProxy:Proxy.class.

    @Test
    public void entryRepRealWireEncodeSucceeds() throws Exception {
        EntryRep rep = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) }, true, true)[0];
        assertNotNull(der(rep));
    }

    @Test
    public void itemRealWireEncodeSucceeds() throws Exception {
        Object svc = fixtureServiceProxy();
        ServiceItem serviceItem = new ServiceItem(serviceID(), svc,
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) });
        Item item = new Item(serviceItem, true);
        assertNotNull("fixture proxy must be bootstrap-eligible for this test to be meaningful",
            item.bootstrapProxy);
        assertNotNull(der(item));
    }

    @Test
    public void templateRealWireEncodeSucceeds() throws Exception {
        ServiceTemplate st = new ServiceTemplate(
            null, null, new Entry[]{ new MixedEntry("server1", null, null) });
        Template tmpl = new Template(st, true);
        assertNotNull(der(tmpl));
    }

    // ── EntryRep: real DER wire round trip ──────────────────────────────

    @Test
    public void entryRepRealWireRoundTripDecodesCorrectly() throws Exception {
        MixedEntry entry = new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE);
        EntryRep rep = EntryRep.toEntryRep(new Entry[]{ entry }, true, true)[0];

        // The load-bearing step: marshal the EntryRep object itself (not a
        // field value) through the real ATOMIC_DER path. Before the
        // fields:Object[].class -> MarshalledInstance[].class fix, this line
        // throws (SchemaGenerator rejects the heterogeneous array).
        MarshalledInstance wire = der(rep);
        assertNotNull("real DER encode of the EntryRep object itself must succeed "
            + "-- this is the actual A3-rework blocker fix, not the per-field "
            + "encode the pre-rework tests already covered", wire);

        EntryRep decoded = (EntryRep) decode(wire);
        assertNotNull(decoded);
        Entry decodedEntry = decoded.get();
        assertTrue(decodedEntry instanceof MixedEntry);
        MixedEntry m = (MixedEntry) decodedEntry;
        assertEquals("server1", m.name);
        assertEquals(Integer.valueOf(7), m.count);
        assertEquals(Boolean.TRUE, m.active);
    }

    @Test
    public void entryRepRealWireByteIdenticalForEqualValues() throws Exception {
        EntryRep repA = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) }, true, true)[0];
        EntryRep repB = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) }, true, true)[0];

        MarshalledInstance wireA = der(repA);
        MarshalledInstance wireB = der(repB);

        // MarshalledInstance.equals() is a raw payload-bytes comparison, so
        // this assertion IS the byte-identical-wire-payload assertion for
        // two independently-marshalled EntryReps of equal logical value --
        // at the *outer* EntryRep encode, not just the per-field encode the
        // pre-rework tests exercised.
        assertEquals("equal EntryRep values must marshal to byte-identical "
            + "wire payloads under real DER encoding of the whole EntryRep",
            wireA, wireB);

        EntryRep decodedA = (EntryRep) decode(wireA);
        EntryRep decodedB = (EntryRep) decode(wireB);
        assertTrue("decoded EntryReps for equal values must be .equals()", decodedA.equals(decodedB));
        assertTrue(decodedA.matchEntry(decodedB));
        assertTrue(decodedB.matchEntry(decodedA));
    }

    @Test
    public void entryRepRealWireDifferentValuesDoNotMatch() throws Exception {
        EntryRep repA = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) }, true, true)[0];
        EntryRep repB = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server2", Integer.valueOf(7), Boolean.TRUE) }, true, true)[0];
        MarshalledInstance wireA = der(repA);
        MarshalledInstance wireB = der(repB);
        assertNotNull(wireA);
        assertNotNull(wireB);

        EntryRep decodedA = (EntryRep) decode(wireA);
        EntryRep decodedB = (EntryRep) decode(wireB);

        assertFalse(decodedA.equals(decodedB));
        assertFalse(decodedA.matchEntry(decodedB));
    }

    @Test
    public void entryRepRealWireTemplateMatchesRealWireEntry() throws Exception {
        // Template: name constrained, count/active wildcarded (null).
        EntryRep tmplRep = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", null, null) }, false, true)[0];
        // Entry: same name, concrete count/active values.
        EntryRep entryRep = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(9001), Boolean.FALSE) }, true, true)[0];
        MarshalledInstance tmplWire = der(tmplRep);
        MarshalledInstance entryWire = der(entryRep);
        assertNotNull(tmplWire);
        assertNotNull(entryWire);

        EntryRep decodedTmpl = (EntryRep) decode(tmplWire);
        EntryRep decodedEntry = (EntryRep) decode(entryWire);

        // NB. receiver/argument order: EntryRep.matchEntry(EntryRep tmpl)
        // tests whether the RECEIVER (an entry) matches the ARGUMENT (a
        // template) -- the same shape as RegistrarImpl.matchEntry(tmpl, e).
        assertTrue("a real-wire-round-tripped DER entry must still match "
            + "a real-wire-round-tripped DER template with a matching constrained field",
            decodedEntry.matchEntry(decodedTmpl));

        // And a mismatched constrained field must not match.
        EntryRep otherEntryRep = EntryRep.toEntryRep(
            new Entry[]{ new MixedEntry("other", Integer.valueOf(9001), Boolean.FALSE) }, true, true)[0];
        EntryRep decodedOther = (EntryRep) decode(der(otherEntryRep));
        assertFalse(decodedOther.matchEntry(decodedTmpl));
    }

    // ── Item: real DER wire round trip, opaque bootstrapProxy relay ─────

    @Test
    public void itemRealWireRoundTripDecodesCorrectly() throws Exception {
        Object svc = fixtureServiceProxy();
        Entry[] attrs = { new MixedEntry("server1", Integer.valueOf(7), Boolean.TRUE) };
        ServiceItem serviceItem = new ServiceItem(serviceID(), svc, attrs);

        // The real production constructor: Bootstrap.create(svc) synthesizes
        // bootstrapProxy from the (bootstrap-eligible) fixture proxy.
        Item item = new Item(serviceItem, true);
        assertNotNull("fixture proxy must be bootstrap-eligible for this test to be meaningful",
            item.bootstrapProxy);

        // The load-bearing step: marshal the Item object itself through the
        // real ATOMIC_DER path. Before the bootstrapProxy:Proxy.class ->
        // MarshalledWrapper.class fix, this line throws (no DER schema rule
        // for a bare Proxy field).
        MarshalledInstance wire = der(item);
        assertNotNull("real DER encode of the Item object itself (with a "
            + "non-null bootstrapProxy) must succeed -- the actual A3-rework "
            + "blocker fix", wire);

        Item decoded = (Item) decode(wire);
        assertNotNull(decoded);
        assertEquals(item.getServiceID(), decoded.getServiceID());
        assertNotNull("bootstrapProxy must survive the wire round trip", decoded.bootstrapProxy);

        // Attribute set round-trips (String + numeric + boolean fields).
        assertEquals(1, decoded.getAttributeSetsLength());
        Entry decodedAttr = decoded.getAttributeSetAtIndex(0).get();
        assertTrue(decodedAttr instanceof MixedEntry);
        MixedEntry m = (MixedEntry) decodedAttr;
        assertEquals("server1", m.name);
        assertEquals(Integer.valueOf(7), m.count);
        assertEquals(Boolean.TRUE, m.active);
    }

    /**
     * The opaque-relay property (§9.2): decoding an {@code Item} must never
     * itself reconstruct {@code bootstrapProxy} -- it stays a {@code
     * MarshalledWrapper} until something explicitly asks for the live
     * {@code Proxy} via {@link Item#getProxy()}. Proven two ways: (a) a
     * server-side-style relay copy (mirroring {@code RegistrarImpl
     * .copyItem}, which forwards {@code item.bootstrapProxy} -- never
     * {@code item.getProxy()}) carries the identical wrapper through
     * untouched; (b) {@code getProxy()} -- the client-side-only
     * consumption point -- still successfully materializes a working
     * {@code Proxy} on demand.
     */
    @Test
    public void itemBootstrapProxyRelaysOpaqueAndMaterializesOnlyOnDemand() throws Exception {
        Object svc = fixtureServiceProxy();
        ServiceItem serviceItem = new ServiceItem(serviceID(), svc,
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(1), Boolean.TRUE) });
        Item item = new Item(serviceItem, true);
        MarshalledInstance wire = der(item);
        assertNotNull(wire);

        Item decoded = (Item) decode(wire);
        MarshalledWrapper wrapperBeforeRelay = decoded.bootstrapProxy;
        assertNotNull(wrapperBeforeRelay);

        // Mirrors RegistrarImpl.copyItem: relay the wrapper straight through,
        // never via getProxy(). No decode happens here.
        Item relayed = new Item(decoded.getServiceID(), null, null, decoded.service,
            decoded.getAttributeSets(), decoded.bootstrapProxy, decoded.entryFormat);
        assertSame("relaying an Item must forward the identical opaque wrapper "
            + "reference, proving the relay path never decodes it",
            wrapperBeforeRelay, relayed.bootstrapProxy);

        // Only the explicit, client-side-only consumption point decodes it.
        Proxy materialized = decoded.getProxy();
        assertNotNull("getProxy() must still be able to materialize a live "
            + "Proxy from the opaque wrapper on demand", materialized);
        assertTrue(Proxy.isProxyClass(materialized.getClass()));
        assertTrue(materialized instanceof RemoteMethodControl);
    }

    // ── Template: real DER wire round trip ──────────────────────────────

    @Test
    public void templateRealWireRoundTripMatchesRealWireItem() throws Exception {
        ServiceTemplate st = new ServiceTemplate(
            null, null, new Entry[]{ new MixedEntry("server1", null, null) });
        Template tmpl = new Template(st, true);

        // The load-bearing step: marshal the Template object itself (whose
        // attributeSetTemplates are EntryReps carrying the same fields:
        // MarshalledInstance[] shape) through real ATOMIC_DER.
        MarshalledInstance wire = der(tmpl);
        assertNotNull("real DER encode of the Template object itself must succeed", wire);

        Template decodedTmpl = (Template) decode(wire);
        assertEquals(1, decodedTmpl.attributeSetTemplatesLength());

        Object svc = fixtureServiceProxy();
        ServiceItem serviceItem = new ServiceItem(serviceID(), svc,
            new Entry[]{ new MixedEntry("server1", Integer.valueOf(42), Boolean.FALSE) });
        Item item = new Item(serviceItem, true);
        Item decodedItem = (Item) decode(der(item));

        // Receiver/argument order: the Item's attribute set (the entry) is
        // the receiver, the Template's attribute-set template the argument
        // (EntryRep.matchEntry(EntryRep tmpl)).
        assertTrue("a real-wire-round-tripped DER Item's attribute set must "
            + "match a real-wire-round-tripped DER Template's matching "
            + "attribute-set template",
            decodedItem.getAttributeSetAtIndex(0).matchEntry(
                decodedTmpl.attributeSetTemplateAtIndex(0)));
    }

    @Test
    public void templateRealWireDoesNotMatchDifferentItem() throws Exception {
        ServiceTemplate st = new ServiceTemplate(
            null, null, new Entry[]{ new MixedEntry("server1", null, null) });
        MarshalledInstance tmplWire = der(new Template(st, true));
        assertNotNull(tmplWire);
        Template decodedTmpl = (Template) decode(tmplWire);

        Object svc = fixtureServiceProxy();
        ServiceItem serviceItem = new ServiceItem(serviceID(), svc,
            new Entry[]{ new MixedEntry("different-server", Integer.valueOf(42), Boolean.FALSE) });
        MarshalledInstance itemWire = der(new Item(serviceItem, true));
        assertNotNull(itemWire);
        Item decodedItem = (Item) decode(itemWire);

        assertFalse(decodedItem.getAttributeSetAtIndex(0).matchEntry(
            decodedTmpl.attributeSetTemplateAtIndex(0)));
    }
}
