package au.net.zeus.jgdms.policy.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.security.RemotePolicyService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PolicyEventLeaseTest {

    @Test
    public void testAtomicSerialRoundTrip() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        Uuid leaseId = UuidFactory.generate();
        PolicyEventLease lease = new PolicyEventLease(server, leaseId, System.currentTimeMillis() + 1_000L);

        PolicyEventLease roundTrip = roundTrip(lease);

        assertEquals(lease, roundTrip);
        assertTrue(roundTrip.canBatch(new PolicyEventLease(server, leaseId, System.currentTimeMillis() + 2_000L)));
    }

    @Test
    public void testRenewDelegatestoServer() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        Uuid leaseId = UuidFactory.generate();
        server.renewResult = 333L;
        PolicyEventLease lease = new PolicyEventLease(server, leaseId, System.currentTimeMillis() + 1_000L);

        lease.renew(222L);
        assertEquals(leaseId, server.lastRenewLeaseId);
        assertEquals(222L, server.lastRenewDuration);
    }

    @Test
    public void testCancelDelegatestoServer() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        Uuid leaseId = UuidFactory.generate();
        PolicyEventLease lease = new PolicyEventLease(server, leaseId, System.currentTimeMillis() + 1_000L);

        lease.cancel();

        assertEquals(leaseId, server.lastCancelledLeaseId);
    }

    @Test
    public void testCanBatchSameServer() {
        StubPolicyServer server = new StubPolicyServer();
        PolicyEventLease a = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);
        PolicyEventLease b = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);

        assertTrue(a.canBatch(b));
    }

    @Test
    public void testCanBatchDifferentServer() {
        PolicyEventLease a = new PolicyEventLease(new StubPolicyServer(), UuidFactory.generate(),
                System.currentTimeMillis() + 1_000L);
        PolicyEventLease b = new PolicyEventLease(new StubPolicyServer(), UuidFactory.generate(),
                System.currentTimeMillis() + 1_000L);

        assertFalse(a.canBatch(b));
    }

    @Test
    public void testCreateLeaseMapRenewAll() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        PolicyEventLease a = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);
        PolicyEventLease b = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);

        LeaseMap map = a.createLeaseMap(100L);
        map.put(b, Long.valueOf(200L));
        map.renewAll();

        assertEquals(2, server.renewCalls);
        assertTrue(server.renewDurations.contains(Long.valueOf(100L)));
        assertTrue(server.renewDurations.contains(Long.valueOf(200L)));
    }

    @Test
    public void testCreateLeaseMapCancelAll() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        PolicyEventLease a = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);
        PolicyEventLease b = new PolicyEventLease(server, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);

        LeaseMap map = a.createLeaseMap(100L);
        map.put(b, Long.valueOf(100L));
        map.cancelAll();

        assertEquals(2, server.cancelCalls);
    }

    @Test
    public void testLeaseMapRenewAllCollectsExceptions() throws Exception {
        StubPolicyServer server = new StubPolicyServer();
        Uuid okId = UuidFactory.generate();
        Uuid badId = UuidFactory.generate();
        server.failRenewFor.add(badId);

        PolicyEventLease ok = new PolicyEventLease(server, okId, System.currentTimeMillis() + 1_000L);
        PolicyEventLease bad = new PolicyEventLease(server, badId, System.currentTimeMillis() + 1_000L);

        LeaseMap map = ok.createLeaseMap(100L);
        map.put(bad, Long.valueOf(100L));

        try {
            map.renewAll();
            fail("Expected LeaseMapException");
        } catch (LeaseMapException expected) {
            assertNotNull(expected.exceptionMap);
            assertTrue(expected.exceptionMap.containsKey(bad));
        }
    }

    @Test(expected = NullPointerException.class)
    public void testNullServerThrows() {
        new PolicyEventLease(null, UuidFactory.generate(), System.currentTimeMillis() + 1_000L);
    }

    @Test(expected = NullPointerException.class)
    public void testNullLeaseIdThrows() {
        new PolicyEventLease(new StubPolicyServer(), null, System.currentTimeMillis() + 1_000L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(value);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        return (T) in.readObject();
    }

    private static final class StubPolicyServer implements RemotePolicyService, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final Uuid identity = UuidFactory.generate();

        Uuid lastRenewLeaseId;
        long lastRenewDuration;
        Uuid lastCancelledLeaseId;
        long renewResult;
        int renewCalls;
        int cancelCalls;
        Set<Uuid> failRenewFor = new HashSet<>();
        Set<Long> renewDurations = new HashSet<>();

        @Override
        public void replace(String[] grants) {
        }

        @Override
        public String[] getCurrentGrants() {
            return new String[0];
        }

        @Override
        public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                          MarshalledInstance handback,
                                                          long duration) {
            return null;
        }

        @Override
        public long renewPolicyLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            renewCalls++;
            renewDurations.add(Long.valueOf(duration));
            lastRenewLeaseId = leaseId;
            lastRenewDuration = duration;
            if (failRenewFor.contains(leaseId)) {
                throw new UnknownLeaseException("no lease");
            }
            return renewResult == 0L ? duration : renewResult;
        }

        @Override
        public void cancelPolicyLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
            cancelCalls++;
            lastCancelledLeaseId = leaseId;
        }

        @Override
        public int hashCode() {
            return identity.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof StubPolicyServer
                    && identity.equals(((StubPolicyServer) obj).identity);
        }
    }
}
