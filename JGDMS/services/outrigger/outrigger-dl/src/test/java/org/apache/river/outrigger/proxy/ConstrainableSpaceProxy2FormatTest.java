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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression test for the load-bearing correctness point of the Outrigger
 * born-format migration (SOW-Entry-ATOMIC-DER-Migration.md sec.3 item 5,
 * task A1 step 3): {@link ConstrainableSpaceProxy2#setConstraints} must
 * carry the born {@code entryFormat} field over from {@code this}
 * unconditionally, so a client's {@code ProxyPreparer.setConstraints()}
 * call -- which wholesale <em>replaces</em> the client-set
 * {@code MethodConstraints} -- can never silently drop or change the
 * space's format. If the format were instead derived from
 * {@code getConstraints()} (as Reggie's {@code Util.requiresDerFormat}
 * does for its own, deliberately per-relationship, use case), this test
 * would fail: the replaced constraints below deliberately do not mention
 * {@link MarshallingFormat} at all.
 */
public class ConstrainableSpaceProxy2FormatTest {

    /**
     * A minimal {@code OutriggerServer} that also implements
     * {@code RemoteMethodControl}, sufficient for exercising
     * {@code ConstrainableSpaceProxy2}'s constructor/{@code setConstraints}
     * plumbing without a real network endpoint. {@code setConstraints}
     * returns a new proxy of the same shape carrying the requested
     * constraints, mirroring a real constrainable stub.
     */
    private static OutriggerServer fakeServer(final MethodConstraints constraints) {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "setConstraints":
                        return fakeServer((MethodConstraints) args[0]);
                    case "getConstraints":
                        return constraints;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FakeOutriggerServer";
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
            ConstrainableSpaceProxy2FormatTest.class.getClassLoader(),
            new Class<?>[] {OutriggerServer.class, RemoteMethodControl.class},
            handler);
    }

    private static Uuid uuid() {
        UUID u = UUID.randomUUID();
        return UuidFactory.create(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    @Test
    public void bornFormatSurvivesConstruction() {
        ConstrainableSpaceProxy2 proxy = new ConstrainableSpaceProxy2(
            fakeServer(null), uuid(), 1000L, MarshallingFormat.ATOMIC_DER, null);
        assertEquals(MarshallingFormat.ATOMIC_DER, proxy.entryFormat);
    }

    /**
     * The core regression: a client-driven {@code setConstraints} call
     * (e.g. from a {@code ProxyPreparer}) that replaces the method
     * constraints wholesale -- with constraints that say nothing at all
     * about {@link MarshallingFormat} -- must not change, drop, or in any
     * way affect the proxy's born {@code entryFormat}.
     */
    @Test
    public void setConstraintsDoesNotDropOrChangeBornFormat() {
        ConstrainableSpaceProxy2 original = new ConstrainableSpaceProxy2(
            fakeServer(null), uuid(), 1000L, MarshallingFormat.ATOMIC_DER, null);

        // A plausible client ProxyPreparer replacement: some constraint
        // that has nothing to do with marshalling format at all.
        MethodConstraints replacement = new BasicMethodConstraints(
            new InvocationConstraints(net.jini.core.constraint.Integrity.YES, null));

        RemoteMethodControl afterSetConstraints = original.setConstraints(replacement);
        assertTrue("setConstraints must return a ConstrainableSpaceProxy2",
            afterSetConstraints instanceof ConstrainableSpaceProxy2);
        ConstrainableSpaceProxy2 updated = (ConstrainableSpaceProxy2) afterSetConstraints;

        assertEquals("client method constraints must reflect the request",
            replacement, updated.getConstraints());
        assertEquals(
            "the born entryFormat must be carried over unconditionally -- "
            + "it is not one of the constraints being replaced, and a "
            + "client can never drop or change it via setConstraints",
            MarshallingFormat.ATOMIC_DER, updated.entryFormat);
    }

    /** Same regression, the other direction: a JOSS-born space stays JOSS. */
    @Test
    public void setConstraintsDoesNotChangeJossBornFormat() {
        ConstrainableSpaceProxy2 original = new ConstrainableSpaceProxy2(
            fakeServer(null), uuid(), 1000L, MarshallingFormat.JOSS, null);

        MethodConstraints replacement = new BasicMethodConstraints(
            new InvocationConstraints(net.jini.core.constraint.Integrity.YES, null));

        ConstrainableSpaceProxy2 updated =
            (ConstrainableSpaceProxy2) original.setConstraints(replacement);

        assertEquals(MarshallingFormat.JOSS, updated.entryFormat);
    }

    /**
     * A client attempting to "upgrade" the format via setConstraints (by
     * requesting {@code MarshallingFormat.ATOMIC_DER} as a client
     * constraint) must not succeed in changing the proxy's actual born
     * entryFormat either -- the field, not the constraint set, is the
     * single source of truth for entry/template marshalling.
     */
    @Test
    public void setConstraintsCannotBeUsedToChangeFormatEitherDirection() {
        ConstrainableSpaceProxy2 original = new ConstrainableSpaceProxy2(
            fakeServer(null), uuid(), 1000L, MarshallingFormat.JOSS, null);

        MethodConstraints attemptDer = new BasicMethodConstraints(
            new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));

        ConstrainableSpaceProxy2 updated =
            (ConstrainableSpaceProxy2) original.setConstraints(attemptDer);

        assertEquals(
            "a constraint requesting ATOMIC_DER must not itself flip the "
            + "born entryFormat -- only the dedicated immutable field, set "
            + "once at the space's instantiation, ever determines it",
            MarshallingFormat.JOSS, updated.entryFormat);
    }
}
