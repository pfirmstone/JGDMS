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
package au.net.zeus.jgdms.policy.proxy;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.lease.LeaseRenewalEvent;
import net.jini.lease.LeaseRenewalManager;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.RemotePolicyProvider;
import org.apache.river.api.security.RemotePolicyService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PolicyUpdateListener}.
 *
 * <p>These tests use hand-rolled stubs/spies rather than a mocking framework
 * to avoid any dependency on DirtyChai or Mockito in this module.
 */
public class PolicyUpdateListenerTest {

    // -------------------------------------------------------------------------
    // Stub / spy implementations
    // -------------------------------------------------------------------------

    /** Stub RemotePolicyService that records calls and returns canned data. */
    private static class StubRemotePolicyService implements RemotePolicyService {
        volatile String[] grantsToReturn = new String[0];
        volatile boolean throwOnRegister = false;
        volatile boolean throwOnGetCurrentGrants = false;
        final List<RemoteEventListener> registered = new ArrayList<>();
        volatile EventRegistration registrationToReturn;

        @Override
        public void replace(String[] grants) throws RemoteException { }

        @Override
        public String[] getCurrentGrants() throws RemoteException {
            if (throwOnGetCurrentGrants) {
                throw new RemoteException("simulated");
            }
            return grantsToReturn;
        }

        @Override
        public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                          MarshalledInstance handback,
                                                          long duration)
                throws IOException {
            if (throwOnRegister) throw new RemoteException("simulated register failure");
            registered.add(listener);
            return registrationToReturn;
        }

        @Override
        public long renewPolicyLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            return duration;
        }

        @Override
        public void cancelPolicyLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException { }
    }

    /** Spy RemotePolicyProvider that records calls to replace(). */
    private static class SpyRemotePolicyProvider extends RemotePolicyProvider {
        final List<PermissionGrant[]> replaceCalls = new ArrayList<>();
        volatile boolean throwOnReplace = false;

        SpyRemotePolicyProvider() {
            super(java.security.Policy.getPolicy());
        }

        @Override
        public void replace(PermissionGrant[] grants) throws IOException {
            if (throwOnReplace) throw new RemoteException("simulated");
            replaceCalls.add(grants);
        }
    }

    /** Stub Lease that does nothing. */
    private static class StubLease implements Lease {
        private long expiration = Long.MAX_VALUE;
        volatile boolean cancelled = false;

        @Override
        public long getExpiration() { return expiration; }

        @Override
        public void cancel() throws UnknownLeaseException, RemoteException {
            cancelled = true;
        }

        @Override
        public void renew(long duration) throws UnknownLeaseException, RemoteException {
            expiration = System.currentTimeMillis() + duration;
        }

        @Override
        public boolean canBatch(Lease lease) { return false; }

        @Override
        public net.jini.core.lease.LeaseMap createLeaseMap(long duration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSerialFormat(int format) { }

        @Override
        public int getSerialFormat() { return 0; }
    }

    /** No-op exporter that returns a pre-built stub. */
    private static class StubExporter implements Exporter {
        final Remote stubToReturn;
        volatile boolean unexported = false;
        volatile int exportCount = 0;

        StubExporter(Remote stubToReturn) {
            this.stubToReturn = stubToReturn;
        }

        @Override
        public Remote export(Remote impl) throws java.rmi.server.ExportException {
            exportCount++;
            return stubToReturn;
        }

        @Override
        public boolean unexport(boolean force) {
            unexported = true;
            return true;
        }
    }

    /** Stub RemoteEventListener used as the exported "stub". */
    private static class StubRemoteEventListener implements RemoteEventListener {
        @Override
        public void notify(RemoteEvent event) throws RemoteException { }
    }

    /** Log handler that captures log records above a given level. */
    private static class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        private final Level threshold;

        CapturingHandler(Level threshold) {
            this.threshold = threshold;
            setLevel(threshold);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= threshold.intValue()) {
                records.add(record);
            }
        }

        @Override public void flush() { }
        @Override public void close() { }

        boolean hasMessageContaining(String fragment) {
            for (LogRecord r : records) {
                if (r.getMessage() != null && r.getMessage().contains(fragment)) return true;
            }
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private StubRemotePolicyService service;
    private SpyRemotePolicyProvider localPolicy;
    private StubExporter exporter;
    private StubLease lease;
    private CapturingHandler logCapture;
    private Logger listenerLogger;

    @Before
    public void setUp() {
        service = new StubRemotePolicyService();
        localPolicy = new SpyRemotePolicyProvider();
        exporter = new StubExporter(new StubRemoteEventListener());
        lease = new StubLease();

        Uuid id = UuidFactory.generate();
        service.registrationToReturn = new EventRegistration(1L, service, lease, 0L);

        // Attach a log capturing handler to the PolicyUpdateListener logger.
        listenerLogger = Logger.getLogger(PolicyUpdateListener.class.getName());
        logCapture = new CapturingHandler(Level.WARNING);
        listenerLogger.addHandler(logCapture);
    }

    @After
    public void tearDown() {
        listenerLogger.removeHandler(logCapture);
    }

    private PolicyUpdateListener createListener() {
        return new PolicyUpdateListener(
                service, localPolicy, exporter, Lease.FOREVER,
                new LeaseRenewalManager());
    }

    // -------------------------------------------------------------------------
    // Helper to create a PolicyUpdateEvent
    // -------------------------------------------------------------------------

    private static PolicyUpdateEvent makeEvent(long seqNum) {
        return new PolicyUpdateEvent(new Object(), 1L, seqNum, null);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * After {@link PolicyUpdateListener#start()}, the listener should have
     * been exported and registered with the service.
     */
    @Test
    public void testStartExportsAndRegisters() throws IOException {
        PolicyUpdateListener listener = createListener();
        listener.start();
        try {
            assertEquals("exporter should have been called once", 1, exporter.exportCount);
            assertEquals("one listener should be registered", 1, service.registered.size());
        } finally {
            listener.stop();
        }
    }

    /**
     * After {@link PolicyUpdateListener#start()}, an initial grant pull should
     * have occurred, causing {@link RemotePolicyProvider#replace(PermissionGrant[])}
     * to be invoked at least once.
     */
    @Test
    public void testStartPerformsInitialGrantPull() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();
        try {
            assertEquals("replace() should have been called on initial start", 1,
                    localPolicy.replaceCalls.size());
        } finally {
            listener.stop();
        }
    }

    /**
     * {@link PolicyUpdateListener#notify(RemoteEvent)} should cause
     * {@link RemotePolicyProvider#replace(PermissionGrant[])} to be called.
     */
    @Test
    public void testNotifyCallsSyncGrants() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();
        int countAfterStart = localPolicy.replaceCalls.size();

        listener.notify(makeEvent(1L));

        assertEquals("replace() should have been called once more after notify()",
                countAfterStart + 1, localPolicy.replaceCalls.size());
        listener.stop();
    }

    /**
     * When the received sequence number is exactly {@code lastSeqNum + 1},
     * no WARNING about a gap should be logged.
     */
    @Test
    public void testNoGapWarningOnSequentialEvents() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();
        logCapture.records.clear(); // clear the initial-start records

        listener.notify(makeEvent(0L));
        listener.notify(makeEvent(1L));

        assertFalse("No gap warning expected for sequential events",
                logCapture.hasMessageContaining("gap"));
        listener.stop();
    }

    /**
     * When the received sequence number jumps (a gap), a WARNING should be
     * logged mentioning the gap.
     */
    @Test
    public void testGapDetectionLogsWarning() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();

        listener.notify(makeEvent(0L));
        logCapture.records.clear(); // clear previous records

        // Jump from 0 to 5 — a gap of 4.
        listener.notify(makeEvent(5L));

        assertTrue("A WARNING about a sequence gap should have been logged",
                logCapture.hasMessageContaining("gap") ||
                logCapture.hasMessageContaining("gap detected") ||
                logCapture.hasMessageContaining("missed"));
        listener.stop();
    }

    /**
     * {@link PolicyUpdateListener#stop()} should unexport the listener.
     */
    @Test
    public void testStopUnexports() throws IOException {
        PolicyUpdateListener listener = createListener();
        listener.start();
        listener.stop();

        assertTrue("exporter.unexport() should have been called", exporter.unexported);
    }

    /**
     * Calling {@link PolicyUpdateListener#start()} a second time without an
     * intervening {@link PolicyUpdateListener#stop()} should be a no-op.
     */
    @Test
    public void testDoubleStartIsNoop() throws IOException {
        PolicyUpdateListener listener = createListener();
        listener.start();
        listener.start(); // second call
        try {
            assertEquals("exporter should only have been called once", 1, exporter.exportCount);
        } finally {
            listener.stop();
        }
    }

    /**
     * {@link PolicyUpdateListener#notify(LeaseRenewalEvent)} with an
     * {@link UnknownLeaseException} should trigger a re-subscribe attempt.
     */
    @Test
    public void testUnknownLeaseExceptionTriggersResubscribe() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();

        int registeredBeforeResubscribe = service.registered.size();

        // Simulate an UnknownLeaseException from the LeaseRenewalManager.
        LeaseRenewalEvent event = new LeaseRenewalEvent(
                new LeaseRenewalManager(), lease, Long.MAX_VALUE,
                new UnknownLeaseException("simulated"));
        listener.notify(event);

        // After re-subscribe, there should be one more registration.
        assertTrue("Service should have been re-registered after UnknownLeaseException",
                service.registered.size() > registeredBeforeResubscribe);
        listener.stop();
    }

    /**
     * A non-{@link PolicyUpdateEvent} remote event should be ignored (no
     * exception thrown, no grant update).
     */
    @Test
    public void testNonPolicyUpdateEventIsIgnored() throws IOException {
        service.grantsToReturn = new String[0];
        PolicyUpdateListener listener = createListener();
        listener.start();
        int countAfterStart = localPolicy.replaceCalls.size();

        // Send a generic RemoteEvent (not a PolicyUpdateEvent).
        RemoteEvent bogusEvent = new RemoteEvent(new Object(), 99L, 0L, (net.jini.io.MarshalledInstance) null) { };
        listener.notify(bogusEvent);

        assertEquals("replace() should NOT have been called for an unknown event type",
                countAfterStart, localPolicy.replaceCalls.size());
        listener.stop();
    }
}
