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
package net.jini.lease;

import net.jini.io.MarshalledInstance;
import org.apache.river.lease.BasicRenewalFailureEvent;
import org.junit.Test;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * SOW {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md} "Layer-2 narrowing pattern" tests for
 * {@link ExpirationWarningEvent} and {@link BasicRenewalFailureEvent} -- two of the SOW's "4
 * known-good production RemoteEvent subclasses" (norm/lease-renewal's own). Both already used
 * {@code new RemoteEvent(arg)} in {@code check(GetArg)} (unlike the reggie
 * {@code ConstrainableRegistrarEvent} bug this SOW separately fixes), but neither NARROWED the
 * result to the class's real expected source type ({@code LeaseRenewalSet}) -- so a malformed or
 * hostile source would only surface as a {@code ClassCastException} at USE time ({@code
 * getRenewalSetLease()}/an unconditional cast), not as a checked, fail-before-construction
 * invariant. This test drives each class's {@code check(GetArg)} directly with a minimal,
 * self-contained {@code AtomicSerial.GetArg} (no {@code jgdms-der} dependency needed -- {@code
 * lookup}/{@code isDefaulted} are ordinary protected extension points).
 *
 * <p>Also pins a second, independent, adjacent finding surfaced while adding the narrowing:
 * {@code BasicRenewalFailureEvent}'s {@code check(GetArg)} was defined but never actually
 * INVOKED by its {@code (GetArg)} constructor (a pre-existing dead-code gap, unrelated to
 * {@code RemoteEvent.source} but directly relevant here since the new narrowing check would have
 * been silently inert otherwise) -- now wired up via {@code super(check(arg))}.
 */
public class RemoteEventSourceNarrowingTest {

    // -------------------------------------------------------------------------
    // Minimal fixtures.
    // -------------------------------------------------------------------------

    private static final class FakeLeaseRenewalSet implements LeaseRenewalSet {
        // A minimal stub: every method throws except what these tests need (none are called by
        // check(GetArg) itself -- only instanceof matters here).
        @Override public net.jini.core.lease.Lease getRenewalSetLease() { throw new UnsupportedOperationException(); }
        @Override public void renewFor(net.jini.core.lease.Lease l, long d, long r) { throw new UnsupportedOperationException(); }
        @Override public void renewFor(net.jini.core.lease.Lease l, long d) { throw new UnsupportedOperationException(); }
        @Override public net.jini.core.lease.Lease remove(net.jini.core.lease.Lease l) { throw new UnsupportedOperationException(); }
        @Override public net.jini.core.lease.Lease[] getLeases() { throw new UnsupportedOperationException(); }
        @Override public net.jini.core.event.EventRegistration setExpirationWarningListener(
                net.jini.core.event.RemoteEventListener l, long m, java.rmi.MarshalledObject h) { throw new UnsupportedOperationException(); }
        @Override public void clearExpirationWarningListener() { throw new UnsupportedOperationException(); }
        @Override public net.jini.core.event.EventRegistration setRenewalFailureListener(
                net.jini.core.event.RemoteEventListener l, java.rmi.MarshalledObject h) { throw new UnsupportedOperationException(); }
        @Override public void clearRenewalFailureListener() { throw new UnsupportedOperationException(); }
    }

    /**
     * Minimal, self-contained {@code AtomicSerial.GetArg}: {@code RemoteEvent.class} owns
     * "source"/"eventID"/"seqNum"; the target class (and its ancestors) own whatever extra
     * fields the test supplies via {@code extra}.
     */
    private static final class FakeGetArg extends org.apache.river.api.io.AtomicSerial.GetArg {
        private final Object source;
        private final long eventId;
        private final Class<?> ownFieldsClass;
        private final java.util.Map<String, Object> extra;
        private final Class<?>[] classes;

        FakeGetArg(Object source, long eventId, Class<?> ownFieldsClass,
                   java.util.Map<String, Object> extra, Class<?>... classes) {
            this.source = source;
            this.eventId = eventId;
            this.ownFieldsClass = ownFieldsClass;
            this.extra = extra;
            this.classes = classes;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            if (callerClass == net.jini.core.event.RemoteEvent.class) {
                switch (name) {
                    case "source":  return source;
                    case "eventID": return eventId;
                    case "seqNum":  return 0L;
                    default:        return ABSENT;
                }
            }
            if (callerClass == ownFieldsClass && extra.containsKey(name)) {
                return extra.get(name);
            }
            return ABSENT;
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return lookup(callerClass, name) == ABSENT;
        }

        @Override
        public Class[] serialClasses() {
            return classes;
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // ExpirationWarningEvent
    // =========================================================================

    private static java.lang.reflect.Method expirationCheckMethod() throws NoSuchMethodException {
        java.lang.reflect.Method m = ExpirationWarningEvent.class.getDeclaredMethod(
                "check", org.apache.river.api.io.AtomicSerial.GetArg.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void expirationWarningEvent_leaseRenewalSetSource_succeeds() throws Exception {
        FakeGetArg arg = new FakeGetArg(new FakeLeaseRenewalSet(),
                LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID, ExpirationWarningEvent.class,
                Collections.emptyMap(),
                net.jini.core.event.RemoteEvent.class, ExpirationWarningEvent.class);
        Object result = expirationCheckMethod().invoke(null, arg);
        assertNotNull(result);
    }

    @Test
    public void expirationWarningEvent_nonLeaseRenewalSetSource_nowRejected() throws Exception {
        // Before this fix: only the eventID was checked; a non-LeaseRenewalSet source was
        // accepted at decode time and would only fail later, at getRenewalSetLease()'s
        // unconditional cast (ClassCastException, not a checked construction-time invariant).
        FakeGetArg arg = new FakeGetArg("not-a-lease-renewal-set",
                LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID, ExpirationWarningEvent.class,
                Collections.emptyMap(),
                net.jini.core.event.RemoteEvent.class, ExpirationWarningEvent.class);
        try {
            expirationCheckMethod().invoke(null, arg);
            fail("expected InvalidObjectException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue("expected InvalidObjectException, got " + e.getCause(),
                    e.getCause() instanceof InvalidObjectException);
        }
    }

    @Test
    public void expirationWarningEvent_wrongEventId_stillRejected() throws Exception {
        // Sanity: the pre-existing eventID invariant is unaffected by the new source check.
        FakeGetArg arg = new FakeGetArg(new FakeLeaseRenewalSet(),
                999L, ExpirationWarningEvent.class, Collections.emptyMap(),
                net.jini.core.event.RemoteEvent.class, ExpirationWarningEvent.class);
        try {
            expirationCheckMethod().invoke(null, arg);
            fail("expected InvalidObjectException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof InvalidObjectException);
        }
    }

    // =========================================================================
    // BasicRenewalFailureEvent
    // =========================================================================

    private static java.lang.reflect.Method renewalFailureCheckMethod() throws NoSuchMethodException {
        java.lang.reflect.Method m = BasicRenewalFailureEvent.class.getDeclaredMethod(
                "check", org.apache.river.api.io.AtomicSerial.GetArg.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void basicRenewalFailureEvent_leaseRenewalSetSourceAndOneMarshalledField_succeeds()
            throws Exception {
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("marshalledLease", new MarshalledInstance(Boolean.TRUE));
        extra.put("marshalledThrowable", null);
        FakeGetArg arg = new FakeGetArg(new FakeLeaseRenewalSet(),
                LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID, BasicRenewalFailureEvent.class, extra,
                net.jini.core.event.RemoteEvent.class, RenewalFailureEvent.class,
                BasicRenewalFailureEvent.class);
        Object result = renewalFailureCheckMethod().invoke(null, arg);
        assertNotNull(result);
    }

    @Test
    public void basicRenewalFailureEvent_nonLeaseRenewalSetSource_nowRejected() throws Exception {
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("marshalledLease", new MarshalledInstance(Boolean.TRUE));
        extra.put("marshalledThrowable", null);
        FakeGetArg arg = new FakeGetArg("not-a-lease-renewal-set",
                LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID, BasicRenewalFailureEvent.class, extra,
                net.jini.core.event.RemoteEvent.class, RenewalFailureEvent.class,
                BasicRenewalFailureEvent.class);
        try {
            renewalFailureCheckMethod().invoke(null, arg);
            fail("expected InvalidObjectException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue("expected InvalidObjectException, got " + e.getCause(),
                    e.getCause() instanceof InvalidObjectException);
        }
    }

    @Test
    public void basicRenewalFailureEvent_bothMarshalledFieldsNull_stillRejected() throws Exception {
        // Sanity: the pre-existing "at least one field must be non null" invariant (now actually
        // wired up via super(check(arg)) -- previously dead code) is unaffected by the new
        // source check, and now genuinely runs at decode time.
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("marshalledLease", null);
        extra.put("marshalledThrowable", null);
        FakeGetArg arg = new FakeGetArg(new FakeLeaseRenewalSet(),
                LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID, BasicRenewalFailureEvent.class, extra,
                net.jini.core.event.RemoteEvent.class, RenewalFailureEvent.class,
                BasicRenewalFailureEvent.class);
        try {
            renewalFailureCheckMethod().invoke(null, arg);
            fail("expected NullPointerException (the pre-existing 'at least one field' invariant)");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue("expected NullPointerException, got " + e.getCause(),
                    e.getCause() instanceof NullPointerException);
        }
    }
}
