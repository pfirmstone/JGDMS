package au.net.zeus.jgdms.policy;

import au.net.zeus.jgdms.policy.proxy.PolicyEventLease;
import java.lang.reflect.Field;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.security.RemotePolicyService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InMemoryPolicyServiceImplTest {

    private static final String[] SAMPLE_GRANTS = new String[]{
        "grant { permission java.security.AllPermission; };"
    };

    private InMemoryPolicyServiceImpl impl;

    @Before
    public void setUp() {
        clearSecurityManagerIfSupported();
        impl = new InMemoryPolicyServiceImpl();
        impl.setEventSource(new StubRemotePolicyService());
    }

    @After
    public void tearDown() {
        if (impl != null) {
            impl.shutdown();
        }
        clearSecurityManagerIfSupported();
    }

    @Test
    public void testReplaceStoresGrants() throws Exception {
        impl.replace(SAMPLE_GRANTS);
        assertArrayEquals(SAMPLE_GRANTS, impl.getCurrentGrants());
    }

    @Test
    public void testReplaceDispatchesEvent() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                latch.countDown();
            }
        }, null, 60_000L);

        impl.replace(SAMPLE_GRANTS);
        assertTrue("listener should receive event", latch.await(2, TimeUnit.SECONDS));
    }

    @Test(expected = NullPointerException.class)
    public void testReplaceNullThrows() throws Exception {
        impl.replace(null);
    }

    @Test
    public void testRegisterAndCancelLease() throws Exception {
        final AtomicInteger deliveries = new AtomicInteger();
        final CountDownLatch first = new CountDownLatch(1);

        EventRegistration registration = impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                deliveries.incrementAndGet();
                first.countDown();
            }
        }, null, 60_000L);

        impl.replace(SAMPLE_GRANTS);
        assertTrue(first.await(2, TimeUnit.SECONDS));

        Uuid leaseId = extractLeaseId((PolicyEventLease) registration.getLease());
        impl.cancelPolicyLease(leaseId);

        impl.replace(SAMPLE_GRANTS);
        Thread.sleep(200L);
        assertEquals("no additional events after cancel", 1, deliveries.get());
    }

    @Test
    public void testRenewLease() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        EventRegistration registration = impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                latch.countDown();
            }
        }, null, 50L);

        Uuid leaseId = extractLeaseId((PolicyEventLease) registration.getLease());
        long granted = impl.renewPolicyLease(leaseId, 500L);
        assertEquals(500L, granted);

        Thread.sleep(120L);
        impl.replace(SAMPLE_GRANTS);
        assertTrue("listener should still be active after renew",
                latch.await(2, TimeUnit.SECONDS));

        try {
            impl.renewPolicyLease(UuidFactory.generate(), 1_000L);
            fail("Expected UnknownLeaseException");
        } catch (UnknownLeaseException expected) {
            // expected
        }
    }

    @Test
    public void testListenerRemovedOnLeaseExpiry() throws Exception {
        final AtomicInteger deliveries = new AtomicInteger();

        impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                deliveries.incrementAndGet();
            }
        }, null, 1L);

        Thread.sleep(50L);
        impl.replace(SAMPLE_GRANTS);
        Thread.sleep(200L);

        assertEquals("expired listener must not receive event", 0, deliveries.get());
    }

    @Test
    public void testListenerRemovedOnDefiniteFailure() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();

        impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                attempts.incrementAndGet();
                throw new NoSuchObjectException("listener gone");
            }
        }, null, 60_000L);

        impl.replace(SAMPLE_GRANTS);
        Thread.sleep(200L);
        impl.replace(SAMPLE_GRANTS);
        Thread.sleep(200L);

        assertEquals("listener should be removed after definite failure", 1, attempts.get());
    }

    @Test
    public void testListenerRetainedOnTransientFailure() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        final CountDownLatch secondAttempt = new CountDownLatch(2);

        impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                attempts.incrementAndGet();
                secondAttempt.countDown();
                throw new RemoteException("transient");
            }
        }, null, 60_000L);

        impl.replace(SAMPLE_GRANTS);
        impl.replace(SAMPLE_GRANTS);

        assertTrue("listener should still be invoked again after transient failure",
                secondAttempt.await(2, TimeUnit.SECONDS));
        assertEquals(2, attempts.get());
    }

    @Test
    public void testGetCurrentGrantsReturnsDefensiveCopy() throws Exception {
        impl.replace(SAMPLE_GRANTS);

        String[] copy = impl.getCurrentGrants();
        copy[0] = "grant { permission java.lang.RuntimePermission \"exitVM\"; };";

        assertArrayEquals("stored grants must not change when returned array is mutated",
                SAMPLE_GRANTS, impl.getCurrentGrants());
    }

    @Test
    public void testShutdownPreventsEventDelivery() throws Exception {
        final AtomicInteger deliveries = new AtomicInteger();

        impl.registerForPolicyUpdates(new RemoteEventListener() {
            @Override
            public void notify(RemoteEvent theEvent)
                    throws UnknownEventException, RemoteException {
                deliveries.incrementAndGet();
            }
        }, null, 60_000L);

        impl.shutdown();
        impl.replace(SAMPLE_GRANTS);
        Thread.sleep(200L);

        assertEquals("shutdown executor should reject event delivery tasks", 0, deliveries.get());
    }

    private static Uuid extractLeaseId(PolicyEventLease lease) throws Exception {
        assertNotNull(lease);
        Field f = PolicyEventLease.class.getDeclaredField("leaseId");
        f.setAccessible(true);
        return (Uuid) f.get(lease);
    }

    private static final class StubRemotePolicyService implements RemotePolicyService {
        @Override
        public void replace(String[] grants) throws RemoteException {
        }

        @Override
        public String[] getCurrentGrants() throws RemoteException {
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
            return duration;
        }

        @Override
        public void cancelPolicyLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
        }
    }

    private static void clearSecurityManagerIfSupported() {
        try {
            System.setSecurityManager(null);
        } catch (UnsupportedOperationException ignored) {
            // JDKs that disable SecurityManager can throw here.
        }
    }
}
