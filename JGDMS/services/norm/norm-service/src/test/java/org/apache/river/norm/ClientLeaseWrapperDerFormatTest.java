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
package org.apache.river.norm;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip tests for {@link ClientLeaseWrapper}'s client-lease persistence
 * write site (the constructor's {@code marshalledClientLease} assignment,
 * converted from {@code AtomicMarshalledInstance} (JOSS) to
 * {@code MarshallingFormat.ATOMIC_DER} by commit 221fe251c).
 *
 * <p>Unlike {@code EventType.getListener()} and
 * {@code JoinState.readAttributes()} (both widened by the same commit to
 * catch {@code IllegalStateException} alongside
 * {@code IOException}/{@code ClassNotFoundException}),
 * {@code ClientLeaseWrapper.getClientLease()}'s read path was <em>not</em>
 * widened -- it still only catches {@code IOException}/
 * {@code ClassNotFoundException} around {@code marshalledClientLease.get(...)}.
 * The second test below pins down that gap: it documents the CURRENT
 * (uncaught) behavior deliberately, rather than asserting a graceful
 * recovery this call site doesn't actually have, so a future fix changes
 * this test's expectation intentionally instead of silently regressing.
 */
public class ClientLeaseWrapperDerFormatTest {

    /** Minimal @AtomicSerial Lease fixture -- ATOMIC_DER is
     *  @AtomicSerial-restricted, so the marshalled lease payload must be a
     *  properly-declared @AtomicSerial type. */
    @AtomicSerial
    public static class TestLease implements Lease, Serializable {
        private static final long serialVersionUID = 1L;
        private static final String EXPIRATION = "expiration";

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm(EXPIRATION, Long.TYPE) };
        }

        public static void serialize(PutArg arg, TestLease o) throws IOException {
            arg.put(EXPIRATION, o.expiration);
            arg.writeArgs();
        }

        private final long expiration;

        public TestLease(long expiration) { this.expiration = expiration; }

        public TestLease(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get(EXPIRATION, 0L));
        }

        @Override public long getExpiration() { return expiration; }
        @Override public void cancel() { throw new UnsupportedOperationException(); }
        @Override public void renew(long duration) { throw new UnsupportedOperationException(); }
        @Override public void setSerialFormat(int format) { /* no-op test double */ }
        @Override public int getSerialFormat() { return Lease.ABSOLUTE; }
        @Override public LeaseMap<? extends Lease, Long> createLeaseMap(long duration) { return null; }
        @Override public boolean canBatch(Lease lease) { return false; }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestLease && expiration == ((TestLease) o).expiration;
        }

        @Override
        public int hashCode() { return (int) (expiration ^ (expiration >>> 32)); }
    }

    private static ClientLeaseWrapper newWrapper(Lease lease) throws IOException {
        return new ClientLeaseWrapper(lease, 1L, new LinkedList(), null,
            Lease.FOREVER, 1000L, System.currentTimeMillis());
    }

    // ── (a) the write site actually uses ATOMIC_DER, and decodes correctly ──

    @Test
    public void constructorWritesMarshalledClientLeaseUsingAtomicDerFormat() throws Exception {
        TestLease lease = new TestLease(123456789L);
        ClientLeaseWrapper clw = newWrapper(lease);

        MarshalledInstance mi = (MarshalledInstance)
            DerFormatTestSupport.getField(clw, "marshalledClientLease");

        assertEquals("ClientLeaseWrapper's constructor must marshal the client "
            + "lease using MarshallingFormat.ATOMIC_DER (not the legacy JOSS default)",
            MarshallingFormat.ATOMIC_DER.getFormat(),
            DerFormatTestSupport.payloadFormatOf(mi));
    }

    @Test
    public void marshalledClientLeaseDecodesBackToTheOriginalLease() throws Exception {
        TestLease lease = new TestLease(987654321L);
        ClientLeaseWrapper clw = newWrapper(lease);

        MarshalledInstance mi = (MarshalledInstance)
            DerFormatTestSupport.getField(clw, "marshalledClientLease");
        Object decoded = mi.get(false);

        assertEquals("the DER-encoded client lease must decode back to the "
            + "original value", lease, decoded);
    }

    // ── (b) documenting the gap: getClientLease() is NOT widened ────────────

    @Test
    public void getClientLeaseDoesNotCatchIllegalStateExceptionFromUnresolvableFormat()
            throws Exception {
        TestLease lease = new TestLease(1L);
        ClientLeaseWrapper clw = newWrapper(lease);

        // Force getClientLease() to actually go through
        // marshalledClientLease.get(...) instead of returning the transient
        // in-memory reference set by the constructor -- simulates the
        // post-recovery state where "clientLease" is transient and starts null.
        DerFormatTestSupport.setField(clw, "clientLease", null);

        MarshalledInstance mi = (MarshalledInstance)
            DerFormatTestSupport.getField(clw, "marshalledClientLease");
        // Same failure mode the widened sites guard against: see
        // DerFormatTestSupport javadoc.
        DerFormatTestSupport.makeFormatUnresolvable(mi);

        try {
            Object result = clw.getClientLease();
            fail("ClientLeaseWrapper.getClientLease() unexpectedly caught the "
                + "IllegalStateException and returned " + result
                + " -- if this call site has since been widened to match "
                + "EventType.getListener()/JoinState.readAttributes(), update "
                + "this test to assert the graceful (null) outcome instead.");
        } catch (IllegalStateException expected) {
            // Documents current behavior: getClientLease() has no
            // IllegalStateException catch, so it propagates uncaught to
            // every caller (getExpiration(), isDeformed(), recoverTransient(),
            // canBatch(), renew()) -- none of which expect it.
        }
    }
}
