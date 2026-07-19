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

import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import org.junit.Test;

import java.io.InvalidObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Regression test for {@link ConstrainableRegistrarEvent}'s {@code check(GetArg)} bug fix -- SOW
 * {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md}'s "Layer-2 narrowing pattern" section.
 *
 * <h2>The bug this pins</h2>
 * <p>Before this fix, {@code check(GetArg)} read the event source via a pre-{@code super}
 * {@code arg.get("source", null)} call from THIS class's own static method. {@code
 * AtomicSerial.GetArg}'s caller-resolution dispatch (see {@code cachedLookup}/{@code
 * callerClass()}) resolves the field's namespace to the CALLING frame's declaring class -- here,
 * {@code ConstrainableRegistrarEvent} itself, which is {@code @Stateless} (empty {@code
 * serialForm()}) and therefore has NO {@code "source"} field in its own namespace. The call
 * therefore always resolved to the ABSENT default ({@code null}), so {@code check} ALWAYS threw
 * "Registrar not an instanceof RemoteMethodControl" regardless of the real (valid) source -- this
 * class could never successfully decode. The fix constructs a plain {@code RemoteEvent} from the
 * {@code GetArg} ({@code new RemoteEvent(arg)}) and reads {@code getSource()} from THAT instance,
 * which resolves "source" from {@code RemoteEvent}'s own declaring frame, correctly.
 *
 * <h2>Why this test uses a hand-rolled {@code GetArg} instead of a real DER round trip</h2>
 * <p>{@code RemoteEvent} (an ancestor of every class in this hierarchy) declares a SEPARATE,
 * independent, pre-existing serial field -- {@code handback: java.rmi.MarshalledObject.class} --
 * that {@code SchemaGenerator} has always rejected ({@code MarshalledObject} is deliberately not
 * in the active DER serializer registry, {@code jgdms-der/src/main/resources/META-INF/jgdms/der-
 * serializers}: "DEFERRED (unresolved security/canonicality defects)"). This means a real DER
 * round trip of {@code ConstrainableRegistrarEvent} (via {@code
 * SchemaGenerator.generateChain}/{@code MarshalledInstance(event, ..., ATOMIC_DER)}, matching this
 * module's other DER tests' established convention of exercising DER indirectly via {@code
 * MarshalledInstance}) still fails today, on {@code handback}, independent of this fix. This is
 * documented in this implementation's final report as a separate, out-of-scope finding -- NOT
 * something this fix (or this SOW) causes or is responsible for.
 *
 * <p>To isolate and verify JUST the {@code check(GetArg)} fix, this test drives {@code
 * ConstrainableRegistrarEvent.check} with a minimal, self-contained {@link FakeGetArg} that
 * reproduces the EXACT per-caller-class namespace-dispatch mechanics the real DER {@code GetArg}
 * implements: {@code AtomicSerial.GetArg}'s {@code lookup(Class, String)}/{@code
 * isDefaulted(Class, String)} extension points are the same protected hooks any {@code GetArg}
 * implementation (real or test) fills in -- reproducing them faithfully needs no {@code jgdms-der}
 * symbol, so this test needs no dependency beyond what this module already has.
 */
public class ConstrainableRegistrarEventSourceNarrowingTest {

    /**
     * Minimal, self-contained {@code AtomicSerial.GetArg} whose {@code lookup}/{@code
     * isDefaulted} hooks reproduce the bug's mechanics: a per-caller-class field namespace, with
     * {@code RemoteEvent.class} owning {@code "source"} (and the other RemoteEvent fields) and
     * every other registered class's namespace EMPTY -- the real shape of the hierarchy's field
     * stores (RegistrarEvent/ServiceEvent/ConstrainableRegistrarEvent all contribute nothing to
     * "source"; only RemoteEvent does).
     */
    private static final class FakeGetArg extends org.apache.river.api.io.AtomicSerial.GetArg {
        private final Object source;

        FakeGetArg(Object source) {
            this.source = source;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            if (callerClass == net.jini.core.event.RemoteEvent.class) {
                switch (name) {
                    case "source":     return source;
                    case "eventID":    return 1L;
                    case "seqNum":     return 2L;
                    default:           return ABSENT; // handback, miHandback: absent (fine)
                }
            }
            return ABSENT; // every other class in the chain: empty namespace (matches reality)
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return lookup(callerClass, name) == ABSENT;
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{
                    net.jini.core.event.RemoteEvent.class,
                    net.jini.core.lookup.ServiceEvent.class,
                    RegistrarEvent.class,
                    ConstrainableRegistrarEvent.class
            };
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    private static Method checkMethod() throws NoSuchMethodException {
        Method check = ConstrainableRegistrarEvent.class.getDeclaredMethod(
                "check", org.apache.river.api.io.AtomicSerial.GetArg.class);
        check.setAccessible(true);
        return check;
    }

    @Test
    public void check_withValidRemoteMethodControlSource_succeeds() throws Exception {
        // A source that IS RemoteMethodControl (the real production shape -- a constrainable
        // registrar stub). Before the fix this ALWAYS threw regardless of the source's real
        // shape, because the pre-super arg.get("source", null) always resolved to the wrong
        // (empty) namespace and returned null.
        FakeRemoteMethodControlSource source = new FakeRemoteMethodControlSource();
        FakeGetArg arg = new FakeGetArg(source);

        Object constraints = checkMethod().invoke(null, arg);
        assertNotNull("check(GetArg) must succeed and return the source's constraints "
                + "(the real source, not a stale null default)", constraints);
        assertSame("must be the exact constraints the fake source's getConstraints() returns",
                source.constraints, constraints);
    }

    @Test
    public void check_withNonRemoteMethodControlSource_stillRejectedCorrectly() throws Exception {
        // Sanity: the invariant itself (source must implement RemoteMethodControl) is preserved --
        // the fix corrects WHICH value is read, not the validation rule.
        FakeGetArg arg = new FakeGetArg("not-a-remote-method-control");
        try {
            checkMethod().invoke(null, arg);
            fail("expected InvalidObjectException (wrapped in InvocationTargetException)");
        } catch (InvocationTargetException e) {
            assertTrue("expected InvalidObjectException, got " + e.getCause(),
                    e.getCause() instanceof InvalidObjectException);
        }
    }

    @Test
    public void check_withNullSource_rejectedByRemoteEventOwnCheck() throws Exception {
        // A genuinely-absent source is still caught -- by RemoteEvent's OWN
        // Valid.notNull("source cannot be null") invariant, run inside new RemoteEvent(arg)
        // itself, before ConstrainableRegistrarEvent's instanceof check is ever reached.
        FakeGetArg arg = new FakeGetArg(null);
        try {
            checkMethod().invoke(null, arg);
            fail("expected InvalidObjectException from RemoteEvent's own null-source check");
        } catch (InvocationTargetException e) {
            assertTrue("expected InvalidObjectException, got " + e.getCause(),
                    e.getCause() instanceof InvalidObjectException);
        }
    }

    /** Minimal fixture: a source that IS RemoteMethodControl, matching the real production shape. */
    private static final class FakeRemoteMethodControlSource implements RemoteMethodControl {
        final MethodConstraints constraints = new MethodConstraints() {
            @Override
            public InvocationConstraints getConstraints(java.lang.reflect.Method method) {
                return InvocationConstraints.EMPTY;
            }

            @Override
            public java.util.Iterator<InvocationConstraints> possibleConstraints() {
                return Collections.singletonList(InvocationConstraints.EMPTY).iterator();
            }
        };

        @Override
        public MethodConstraints getConstraints() {
            return constraints;
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return this;
        }
    }
}
