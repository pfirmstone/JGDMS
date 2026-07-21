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
import java.rmi.MarshalledObject;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Behavioral tests for the withdrawn {@link java.rmi.MarshalledObject}
 * handback overloads on {@link SpaceProxy2}
 * ({@code SOW-Outrigger-DER-Only-JOSS-Rejection.md} decisions 3+4, JGDMS
 * 4.0.0): a <em>null</em> handback delegates to the
 * {@link MarshalledInstance} path -- nothing MO-shaped exists in a null
 * call, so the ~30 conformance classes binding the MO overload with null
 * stay green -- while a <em>non-null</em> MO throws
 * {@link UnsupportedOperationException} before any remote call is made.
 */
public class SpaceProxy2MOOverloadTest {

    /** Records the server-side method invoked and its handback argument. */
    private static final class Recorder {
        final AtomicBoolean invoked = new AtomicBoolean();
        final AtomicReference<String> method = new AtomicReference<String>();
        final AtomicReference<Object> handback = new AtomicReference<Object>();
    }

    /**
     * A minimal {@code OutriggerServer} + {@code RemoteMethodControl}
     * dynamic proxy recording notify/registerForAvailabilityEvent calls
     * (mirrors {@link ConstrainableSpaceProxy2FormatTest}'s fake-server
     * pattern -- no network endpoint involved).
     */
    private static OutriggerServer fakeServer(final Recorder recorder) {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "notify":
                    case "registerForAvailabilityEvent":
                        recorder.invoked.set(true);
                        recorder.method.set(method.getName());
                        recorder.handback.set(args[args.length - 1]);
                        // Callers only need a non-null-safe placeholder;
                        // the proxy under test returns this verbatim.
                        return null;
                    case "setConstraints":
                        return proxy;
                    case "getConstraints":
                        return null;
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
            SpaceProxy2MOOverloadTest.class.getClassLoader(),
            new Class<?>[] {OutriggerServer.class, RemoteMethodControl.class},
            handler);
    }

    private static Uuid uuid() {
        UUID u = UUID.randomUUID();
        return UuidFactory.create(u.getMostSignificantBits(),
                                  u.getLeastSignificantBits());
    }

    private static SpaceProxy2 proxy(Recorder recorder) {
        return new ConstrainableSpaceProxy2(fakeServer(recorder), uuid(),
            1000L, MarshallingFormat.ATOMIC_DER, null);
    }

    // ---------------- notify ----------------

    @Test
    public void notifyWithNullMODelegatesToMIPath() throws Exception {
        Recorder recorder = new Recorder();
        EventRegistration reg = proxy(recorder).notify(
            null, null, null, 1000L, (MarshalledObject) null);
        assertTrue("null-MO notify must delegate to the server MI path",
            recorder.invoked.get());
        assertEquals("notify", recorder.method.get());
        assertNull("nothing MO-shaped exists in a null call: the delegated "
            + "handback must be null", recorder.handback.get());
        assertNull(reg); // fake server returns null; delegation is the point
    }

    @Test
    public void notifyWithNonNullMOThrowsUOEWithoutRemoteCall() throws Exception {
        Recorder recorder = new Recorder();
        try {
            proxy(recorder).notify(null, null, null, 1000L,
                new MarshalledObject<String>("joss-handback"));
            fail("non-null MarshalledObject handback must throw "
                + "UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue("message must name the DER-only withdrawal",
                expected.getMessage().contains("ATOMIC_DER"));
        }
        assertFalse("the withdrawal must fire before any remote call",
            recorder.invoked.get());
    }

    // ------- registerForAvailabilityEvent -------

    @Test
    public void registerWithNullMODelegatesToMIPath() throws Exception {
        Recorder recorder = new Recorder();
        EventRegistration reg = proxy(recorder).registerForAvailabilityEvent(
            new LinkedList(), null, false, null, 1000L,
            (MarshalledObject) null);
        assertTrue("null-MO registerForAvailabilityEvent must delegate to "
            + "the server MI path", recorder.invoked.get());
        assertEquals("registerForAvailabilityEvent", recorder.method.get());
        assertNull("nothing MO-shaped exists in a null call: the delegated "
            + "handback must be null", recorder.handback.get());
        assertNull(reg);
    }

    @Test
    public void registerWithNonNullMOThrowsUOEWithoutRemoteCall() throws Exception {
        Recorder recorder = new Recorder();
        try {
            proxy(recorder).registerForAvailabilityEvent(
                new LinkedList(), null, false, null, 1000L,
                new MarshalledObject<String>("joss-handback"));
            fail("non-null MarshalledObject handback must throw "
                + "UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue("message must name the DER-only withdrawal",
                expected.getMessage().contains("ATOMIC_DER"));
        }
        assertFalse("the withdrawal must fire before any remote call",
            recorder.invoked.get());
    }
}
