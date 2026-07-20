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
package org.apache.river.outrigger.proxy;

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
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Board-review fix regression test (fix 2, [BLOCKING]): {@link SpaceProxy2}
 * and {@link AdminProxy}'s {@code (GetArg)} deserialization constructors
 * must treat an absent {@code entryFormat} field -- a proxy serialized
 * before this field existed, i.e. before DER support -- as implicit legacy
 * {@link MarshallingFormat#JOSS}, not reject it (STD-006 sec.11.8 graceful
 * degradation: an absent old-format marker means implicit legacy JOSS, not
 * a reject). A field that IS present but whose serialized value is
 * genuinely {@code null} remains a rejected invariant violation
 * (corruption) -- this test asserts both directions, plus the unaffected
 * present-and-valid case, for both proxy classes.
 *
 * <p>Builds a minimal {@link GetArg} directly (following the
 * {@code GetArgIdempotencyTest.HostileGetArg} pattern in {@code
 * jgdms-platform}) rather than driving a real marshal/unmarshal round
 * trip, so "field absent from the stream's persistent schema" can be
 * expressed precisely (a key simply missing from the backing map) as
 * distinct from "field present with value {@code null}" (a key present,
 * mapped to {@code null}) -- exactly the distinction {@code GetArg}'s
 * untyped {@code get(name, Object)} overload preserves and its
 * type-checked {@code get(name, val, type)} overload collapses.
 */
public class EntryFormatGetArgCompatTest {

    /** Minimal concrete subclass: {@link SpaceProxy2} is abstract with no unimplemented methods. */
    private static class TestSpaceProxy2 extends SpaceProxy2 {
        TestSpaceProxy2(GetArg arg) throws IOException, ClassNotFoundException { super(arg); }
    }

    /** Minimal concrete subclass: {@link AdminProxy} is abstract with no unimplemented methods. */
    private static class TestAdminProxy extends AdminProxy {
        TestAdminProxy(GetArg arg) throws IOException, ClassNotFoundException { super(arg); }
    }

    /**
     * A {@link GetArg} backed directly by a {@code Map<String,Object>}: a
     * key absent from the map resolves to {@link GetArg#ABSENT} (so {@code
     * get}'s default-return kicks in and {@code defaulted} reports {@code
     * true}); a key present in the map -- even mapped to {@code null} --
     * resolves to that exact value, distinguishing "absent" from
     * "present-but-null" precisely as a real stream would.
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

    private static Uuid uuid() {
        UUID u = UUID.randomUUID();
        return UuidFactory.create(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    private static OutriggerServer fakeServer() {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "FakeOutriggerServer";
                    default: {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return Boolean.FALSE;
                        if (rt.isPrimitive() && rt != void.class) return 0;
                        return null;
                    }
                }
            }
        };
        return (OutriggerServer) Proxy.newProxyInstance(
            EntryFormatGetArgCompatTest.class.getClassLoader(),
            new Class<?>[]{OutriggerServer.class},
            handler);
    }

    private static OutriggerAdmin fakeAdmin() {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "FakeOutriggerAdmin";
                    default: {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return Boolean.FALSE;
                        if (rt.isPrimitive() && rt != void.class) return 0;
                        return null;
                    }
                }
            }
        };
        return (OutriggerAdmin) Proxy.newProxyInstance(
            EntryFormatGetArgCompatTest.class.getClassLoader(),
            new Class<?>[]{OutriggerAdmin.class},
            handler);
    }

    // ── SpaceProxy2 ──────────────────────────────────────────────────────

    @Test
    public void spaceProxy2AbsentEntryFormatDecodesAsJoss() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("space", fakeServer());
        fields.put("spaceUuid", uuid());
        fields.put("serverMaxServerQueryTimeout", Long.valueOf(1000L));
        // "entryFormat" deliberately absent -- an old, pre-DER proxy.
        MapGetArg arg = new MapGetArg(SpaceProxy2.class, fields);

        TestSpaceProxy2 proxy = new TestSpaceProxy2(arg);
        assertEquals("an old proxy lacking entryFormat entirely predates DER "
            + "and must decode as legacy JOSS, not be rejected",
            MarshallingFormat.JOSS, proxy.entryFormat);
    }

    @Test
    public void spaceProxy2PresentButNullEntryFormatIsRejected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("space", fakeServer());
        fields.put("spaceUuid", uuid());
        fields.put("serverMaxServerQueryTimeout", Long.valueOf(1000L));
        fields.put("entryFormat", null); // present, but genuinely null: corruption
        MapGetArg arg = new MapGetArg(SpaceProxy2.class, fields);

        try {
            new TestSpaceProxy2(arg);
            fail("a present-but-null entryFormat is corruption and must still "
                + "be rejected");
        } catch (InvalidObjectException expected) {
            // expected
        }
    }

    @Test
    public void spaceProxy2PresentEntryFormatIsUnaffected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("space", fakeServer());
        fields.put("spaceUuid", uuid());
        fields.put("serverMaxServerQueryTimeout", Long.valueOf(1000L));
        fields.put("entryFormat", MarshallingFormat.ATOMIC_DER);
        MapGetArg arg = new MapGetArg(SpaceProxy2.class, fields);

        TestSpaceProxy2 proxy = new TestSpaceProxy2(arg);
        assertEquals(MarshallingFormat.ATOMIC_DER, proxy.entryFormat);
    }

    // ── AdminProxy ───────────────────────────────────────────────────────

    @Test
    public void adminProxyAbsentEntryFormatDecodesAsJoss() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("admin", fakeAdmin());
        fields.put("spaceUuid", uuid());
        // "entryFormat" deliberately absent -- an old, pre-DER proxy.
        MapGetArg arg = new MapGetArg(AdminProxy.class, fields);

        TestAdminProxy proxy = new TestAdminProxy(arg);
        assertEquals("an old proxy lacking entryFormat entirely predates DER "
            + "and must decode as legacy JOSS, not be rejected",
            MarshallingFormat.JOSS, proxy.entryFormat);
    }

    @Test
    public void adminProxyPresentButNullEntryFormatIsRejected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("admin", fakeAdmin());
        fields.put("spaceUuid", uuid());
        fields.put("entryFormat", null); // present, but genuinely null: corruption
        MapGetArg arg = new MapGetArg(AdminProxy.class, fields);

        try {
            new TestAdminProxy(arg);
            fail("a present-but-null entryFormat is corruption and must still "
                + "be rejected");
        } catch (InvalidObjectException expected) {
            // expected
        }
    }

    @Test
    public void adminProxyPresentEntryFormatIsUnaffected() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("admin", fakeAdmin());
        fields.put("spaceUuid", uuid());
        fields.put("entryFormat", MarshallingFormat.ATOMIC_DER);
        MapGetArg arg = new MapGetArg(AdminProxy.class, fields);

        TestAdminProxy proxy = new TestAdminProxy(arg);
        assertEquals(MarshallingFormat.ATOMIC_DER, proxy.entryFormat);
    }
}
