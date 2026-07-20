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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression test for {@link ConstrainableRegistration}'s half of the
 * born-format non-droppability fix (SOW-Entry-ATOMIC-DER-Migration.md
 * sec.4 A3), structurally parallel to
 * {@link ConstrainableRegistrarProxyFormatTest} for the other proxy family:
 * {@link ConstrainableRegistration#setConstraints} must carry the born
 * {@code entryFormat} field over from {@code this} unconditionally.
 */
public class ConstrainableRegistrationFormatTest {

    private static Registrar fakeServer(final MethodConstraints constraints) {
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
                        return "FakeRegistrar";
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
            ConstrainableRegistrationFormatTest.class.getClassLoader(),
            new Class<?>[] {Registrar.class, RemoteMethodControl.class},
            handler);
    }

    private static ServiceID serviceID() {
        UUID u = UUID.randomUUID();
        return new ServiceID(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    private static Uuid uuid() {
        UUID u = UUID.randomUUID();
        return UuidFactory.create(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    private static ServiceLease lease(Registrar server) {
        return ServiceLease.getInstance(server, serviceID(), serviceID(), uuid(), Long.MAX_VALUE);
    }

    @Test
    public void bornFormatSurvivesConstruction() {
        Registrar server = fakeServer(null);
        ConstrainableRegistration reg = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.ATOMIC_DER, null, true);
        assertEquals(MarshallingFormat.ATOMIC_DER, reg.entryFormat);
    }

    /**
     * The core regression: a client-driven {@code setConstraints} call that
     * replaces the method constraints wholesale -- with constraints that say
     * nothing at all about {@link MarshallingFormat} -- must not change,
     * drop, or in any way affect the registration's born {@code entryFormat}.
     */
    @Test
    public void setConstraintsDoesNotDropOrChangeBornFormat() {
        Registrar server = fakeServer(null);
        ConstrainableRegistration original = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.ATOMIC_DER, null, true);

        MethodConstraints replacement = new BasicMethodConstraints(
            new InvocationConstraints(net.jini.core.constraint.Integrity.YES, null));

        RemoteMethodControl afterSetConstraints = original.setConstraints(replacement);
        assertTrue("setConstraints must return a ConstrainableRegistration",
            afterSetConstraints instanceof ConstrainableRegistration);
        ConstrainableRegistration updated = (ConstrainableRegistration) afterSetConstraints;

        assertEquals("client method constraints must reflect the request",
            replacement, updated.getConstraints());
        assertEquals(
            "the born entryFormat must be carried over unconditionally -- "
            + "it is not one of the constraints being replaced, and a "
            + "client can never drop or change it via setConstraints",
            MarshallingFormat.ATOMIC_DER, updated.entryFormat);
    }

    /** Same regression, the other direction: a JOSS-born registration stays JOSS. */
    @Test
    public void setConstraintsDoesNotChangeJossBornFormat() {
        Registrar server = fakeServer(null);
        ConstrainableRegistration original = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.JOSS, null, true);

        MethodConstraints replacement = new BasicMethodConstraints(
            new InvocationConstraints(net.jini.core.constraint.Integrity.YES, null));

        ConstrainableRegistration updated =
            (ConstrainableRegistration) original.setConstraints(replacement);

        assertEquals(MarshallingFormat.JOSS, updated.entryFormat);
    }

    /**
     * A client attempting to "upgrade" the format via setConstraints (by
     * requesting {@code MarshallingFormat.ATOMIC_DER} as a client
     * constraint) must not succeed in changing the registration's actual
     * born entryFormat either.
     */
    @Test
    public void setConstraintsCannotBeUsedToChangeFormatEitherDirection() {
        Registrar server = fakeServer(null);
        ConstrainableRegistration original = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.JOSS, null, true);

        MethodConstraints attemptDer = new BasicMethodConstraints(
            new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));

        ConstrainableRegistration updated =
            (ConstrainableRegistration) original.setConstraints(attemptDer);

        assertEquals(
            "a constraint requesting ATOMIC_DER must not itself flip the "
            + "born entryFormat -- only the dedicated immutable field, set "
            + "once at the registrar's instantiation, ever determines it",
            MarshallingFormat.JOSS, updated.entryFormat);
    }

    /** {@code useDer()} tracks the born field, never getConstraints(). */
    @Test
    public void useDerTracksBornFieldNotConstraints() {
        Registrar server = fakeServer(null);
        ConstrainableRegistration derReg = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.ATOMIC_DER, null, true);
        assertTrue(derReg.useDer());

        ConstrainableRegistration jossReg = new ConstrainableRegistration(
            server, lease(server), MarshallingFormat.JOSS, null, true);
        assertFalse(jossReg.useDer());

        MethodConstraints attemptDer = new BasicMethodConstraints(
            new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null));
        ConstrainableRegistration updated =
            (ConstrainableRegistration) jossReg.setConstraints(attemptDer);
        assertFalse(updated.useDer());
    }
}
