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
import java.io.InvalidObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.proxy.MarshalledWrapper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Board-review-style regression test (mirroring A1's
 * {@code EntryFormatGetArgCompatTest} in outrigger-dl, structurally
 * parallel per SOW-Entry-ATOMIC-DER-Migration.md sec.4 A3):
 * {@link RegistrarProxy}, {@link Registration} and {@link Item}'s
 * {@code (GetArg)} deserialization constructors must treat an absent
 * {@code entryFormat} field -- an instance serialized before this field
 * existed, i.e. before DER support -- as implicit legacy
 * {@link MarshallingFormat#JOSS}, not reject it (STD-006 sec.11.8 graceful
 * degradation: an absent old-format marker means implicit legacy JOSS, not
 * a reject). A field that IS present but whose serialized value is
 * genuinely {@code null} remains a rejected invariant violation
 * (corruption) -- this test asserts both directions, plus the unaffected
 * present-and-valid case, for all three classes.
 *
 * <p>Builds a minimal {@link GetArg} directly (following the
 * {@code GetArgIdempotencyTest.HostileGetArg} / outrigger-dl
 * {@code EntryFormatGetArgCompatTest} pattern) rather than driving a real
 * marshal/unmarshal round trip, so "field absent from the stream's
 * persistent schema" can be expressed precisely (a key simply missing from
 * the backing map) as distinct from "field present with value {@code null}"
 * (a key present, mapped to {@code null}).
 */
public class EntryFormatGetArgCompatTest {

    /** Minimal concrete subclass: {@link RegistrarProxy} is abstract. */
    private static class TestRegistrarProxy extends RegistrarProxy {
        TestRegistrarProxy(GetArg arg) throws IOException, ClassNotFoundException { super(arg); }
    }

    /** Minimal concrete subclass: {@link Registration} is abstract. */
    private static class TestRegistration extends Registration {
        TestRegistration(GetArg arg) throws IOException, ClassNotFoundException { super(arg); }
    }

    /**
     * A {@link GetArg} backed directly by a {@code Map<String,Object>}: a
     * key absent from the map resolves to {@link GetArg#ABSENT} (so
     * {@code get}'s default-return kicks in); a key present in the map --
     * even mapped to {@code null} -- resolves to that exact value,
     * distinguishing "absent" from "present-but-null" precisely as a real
     * stream would.
     */
    private static class MapGetArg extends GetArg {
        private final Map<String, Object> present;
        private final Class<?> caller;

        MapGetArg(Class<?> caller, Map<String, Object> present) {
            this.caller = caller;
            this.present = present;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            return present.containsKey(name) ? present.get(name) : ABSENT;
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return !present.containsKey(name);
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{caller};
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    private static ServiceID serviceID() {
        UUID u = UUID.randomUUID();
        return new ServiceID(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    private static Uuid uuid() {
        UUID u = UUID.randomUUID();
        return UuidFactory.create(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    /**
     * Also implements {@link RemoteMethodControl} (not just {@link Registrar})
     * so it can be used as the underlying server reference for
     * {@code ServiceLease.getInstance} (which requires it), matching the
     * shape of a real constrainable stub.
     */
    private static Registrar fakeServer() {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "setConstraints": return proxy;
                    case "getConstraints": return null;
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "FakeRegistrar";
                    default: {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return Boolean.FALSE;
                        if (rt.isPrimitive() && rt != void.class) return 0;
                        return null;
                    }
                }
            }
        };
        return (Registrar) Proxy.newProxyInstance(
            EntryFormatGetArgCompatTest.class.getClassLoader(),
            new Class<?>[]{Registrar.class, RemoteMethodControl.class},
            handler);
    }

    // ── RegistrarProxy ───────────────────────────────────────────────────

    @Test
    public void registrarProxyAbsentEntryFormatDecodesAsJoss() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("registrarIDMostSig", Long.valueOf(1L));
        fields.put("registrarIDLeastSig", Long.valueOf(2L));
        // "entryFormat" deliberately absent -- a pre-A3 proxy.
        MapGetArg arg = new MapGetArg(RegistrarProxy.class, fields);

        TestRegistrarProxy proxy = new TestRegistrarProxy(arg);
        assertEquals("a pre-A3 proxy lacking entryFormat entirely predates "
            + "DER and must decode as legacy JOSS, not be rejected",
            MarshallingFormat.JOSS, proxy.entryFormat);
    }

    @Test
    public void registrarProxyPresentButNullEntryFormatIsRejected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("registrarIDMostSig", Long.valueOf(1L));
        fields.put("registrarIDLeastSig", Long.valueOf(2L));
        fields.put("entryFormat", null); // present, but genuinely null: corruption
        MapGetArg arg = new MapGetArg(RegistrarProxy.class, fields);

        try {
            new TestRegistrarProxy(arg);
            fail("a present-but-null entryFormat is corruption and must "
                + "still be rejected");
        } catch (InvalidObjectException expected) {
            // expected
        }
    }

    @Test
    public void registrarProxyPresentEntryFormatIsUnaffected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("registrarIDMostSig", Long.valueOf(1L));
        fields.put("registrarIDLeastSig", Long.valueOf(2L));
        fields.put("entryFormat", MarshallingFormat.ATOMIC_DER);
        MapGetArg arg = new MapGetArg(RegistrarProxy.class, fields);

        TestRegistrarProxy proxy = new TestRegistrarProxy(arg);
        assertEquals(MarshallingFormat.ATOMIC_DER, proxy.entryFormat);
    }

    // ── Registration ─────────────────────────────────────────────────────

    private static ServiceLease fakeLease() {
        return ServiceLease.getInstance(fakeServer(), serviceID(), serviceID(), uuid(), Long.MAX_VALUE);
    }

    @Test
    public void registrationAbsentEntryFormatDecodesAsJoss() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("lease", fakeLease());
        // "entryFormat" deliberately absent -- a pre-A3 registration.
        MapGetArg arg = new MapGetArg(Registration.class, fields);

        TestRegistration reg = new TestRegistration(arg);
        assertEquals("a pre-A3 registration lacking entryFormat entirely "
            + "predates DER and must decode as legacy JOSS, not be rejected",
            MarshallingFormat.JOSS, reg.entryFormat);
    }

    @Test
    public void registrationPresentButNullEntryFormatIsRejected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("lease", fakeLease());
        fields.put("entryFormat", null); // present, but genuinely null: corruption
        MapGetArg arg = new MapGetArg(Registration.class, fields);

        try {
            new TestRegistration(arg);
            fail("a present-but-null entryFormat is corruption and must "
                + "still be rejected");
        } catch (InvalidObjectException expected) {
            // expected
        }
    }

    @Test
    public void registrationPresentEntryFormatIsUnaffected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("server", fakeServer());
        fields.put("lease", fakeLease());
        fields.put("entryFormat", MarshallingFormat.ATOMIC_DER);
        MapGetArg arg = new MapGetArg(Registration.class, fields);

        TestRegistration reg = new TestRegistration(arg);
        assertEquals(MarshallingFormat.ATOMIC_DER, reg.entryFormat);
    }

    // ── Item ─────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private static MarshalledWrapper fakeService() throws IOException {
        return new MarshalledWrapper("svc");
    }

    @Test
    public void itemAbsentEntryFormatDecodesAsJoss() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("serviceID", serviceID());
        fields.put("service", fakeService());
        // "entryFormat" deliberately absent -- a pre-A3 Item.
        MapGetArg arg = new MapGetArg(Item.class, fields);

        Item item = new Item(arg);
        assertEquals("a pre-A3 Item lacking entryFormat entirely predates "
            + "DER and must decode as legacy JOSS, not be rejected",
            MarshallingFormat.JOSS, item.entryFormat);
    }

    @Test
    public void itemPresentButNullEntryFormatIsRejected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("serviceID", serviceID());
        fields.put("service", fakeService());
        fields.put("entryFormat", null); // present, but genuinely null: corruption
        MapGetArg arg = new MapGetArg(Item.class, fields);

        try {
            new Item(arg);
            fail("a present-but-null entryFormat is corruption and must "
                + "still be rejected");
        } catch (InvalidObjectException expected) {
            // expected
        }
    }

    @Test
    public void itemPresentEntryFormatIsUnaffected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("serviceID", serviceID());
        fields.put("service", fakeService());
        fields.put("entryFormat", MarshallingFormat.ATOMIC_DER);
        MapGetArg arg = new MapGetArg(Item.class, fields);

        Item item = new Item(arg);
        assertEquals(MarshallingFormat.ATOMIC_DER, item.entryFormat);
    }
}
