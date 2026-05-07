package au.net.zeus.jgdms.policy.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.Arrays;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lookup.ServiceID;
import net.jini.export.CodebaseAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.security.RemotePolicyService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RemotePolicyServiceProxyTest {

    private Uuid serviceId;
    private MockServer plainServer;
    private MockConstrainableServer constrainableServer;

    @Before
    public void setUp() {
        serviceId = UuidFactory.generate();
        plainServer = new MockServer();
        constrainableServer = new MockConstrainableServer();
    }

    @Test
    public void testAtomicSerialRoundTrip() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);

        RemotePolicyServiceProxy roundTrip = roundTrip(proxy);

        assertEquals(serviceId, roundTrip.getReferentUuid());
        assertEquals(plainServer, roundTrip.getProxy());
    }

    @Test
    public void testCreateReturnsConstrainable() {
        Object proxy = RemotePolicyServiceProxy.create(constrainableServer, serviceId);
        assertTrue(proxy instanceof RemotePolicyServiceProxy.ConstrainableRemotePolicyServiceProxy);
    }

    @Test
    public void testCreateReturnsPlain() {
        Object proxy = RemotePolicyServiceProxy.create(plainServer, serviceId);
        assertTrue(proxy instanceof RemotePolicyServiceProxy);
        assertTrue(!(proxy instanceof RemoteMethodControl));
    }

    @Test
    public void testSetConstraintsReturnsNewInstance() {
        RemotePolicyServiceProxy.ConstrainableRemotePolicyServiceProxy proxy =
                new RemotePolicyServiceProxy.ConstrainableRemotePolicyServiceProxy(
                        constrainableServer, serviceId, null);

        RemoteMethodControl constrained = proxy.setConstraints(null);

        assertNotSame(proxy, constrained);
        assertTrue(constrained instanceof RemotePolicyServiceProxy.ConstrainableRemotePolicyServiceProxy);
        assertSame(proxy.getProxy(), ((RemotePolicyServiceProxy.ConstrainableRemotePolicyServiceProxy) constrained)
                .getProxy());
    }

    @Test
    public void testDelegatesReplace() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);
        String[] grants = new String[]{"g1", "g2"};

        proxy.replace(grants);

        assertArrayEquals(grants, plainServer.lastReplaceGrants);
    }

    @Test
    public void testDelegatesGetCurrentGrants() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);
        plainServer.currentGrants = new String[]{"x"};

        assertArrayEquals(new String[]{"x"}, proxy.getCurrentGrants());
    }

    @Test
    public void testDelegatesRegisterForPolicyUpdates() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);
        RemoteEventListener listener = new RemoteEventListener() {
            @Override
            public void notify(net.jini.core.event.RemoteEvent theEvent) {
            }
        };
        MarshalledInstance handback = new MarshalledInstance("h");
        plainServer.registrationToReturn = new EventRegistration(7L, plainServer, null, 0L);

        EventRegistration result = proxy.registerForPolicyUpdates(listener, handback, 123L);

        assertSame(plainServer.registrationToReturn, result);
        assertSame(listener, plainServer.lastListener);
        assertSame(handback, plainServer.lastHandback);
        assertEquals(123L, plainServer.lastDuration);
    }

    @Test
    public void testDelegatesRenewLease() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);
        Uuid id = UuidFactory.generate();
        plainServer.renewResult = 987L;

        long result = proxy.renewPolicyLease(id, 321L);

        assertEquals(987L, result);
        assertEquals(id, plainServer.lastLeaseId);
        assertEquals(321L, plainServer.lastDuration);
    }

    @Test
    public void testDelegatesCancelLease() throws Exception {
        RemotePolicyServiceProxy proxy = new RemotePolicyServiceProxy(plainServer, serviceId);
        Uuid id = UuidFactory.generate();

        proxy.cancelPolicyLease(id);

        assertEquals(id, plainServer.lastCancelledLeaseId);
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

    private static class MockServer implements RemotePolicyService,
            Administrable,
            CodebaseAccessor,
            ServiceProxyAccessor,
            ServiceAttributesAccessor,
            ServiceIDAccessor,
            JoinAdmin,
            DestroyAdmin,
            java.io.Serializable {

        private static final long serialVersionUID = 1L;
        private final Uuid identity = UuidFactory.generate();

        String[] lastReplaceGrants;
        String[] currentGrants = new String[0];
        RemoteEventListener lastListener;
        MarshalledInstance lastHandback;
        long lastDuration;
        EventRegistration registrationToReturn;
        Uuid lastLeaseId;
        long renewResult;
        Uuid lastCancelledLeaseId;

        @Override
        public void replace(String[] grants) {
            lastReplaceGrants = grants == null ? null : Arrays.copyOf(grants, grants.length);
        }

        @Override
        public String[] getCurrentGrants() {
            return currentGrants;
        }

        @Override
        public EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                                          MarshalledInstance handback,
                                                          long duration) {
            this.lastListener = listener;
            this.lastHandback = handback;
            this.lastDuration = duration;
            return registrationToReturn;
        }

        @Override
        public long renewPolicyLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            lastLeaseId = leaseId;
            lastDuration = duration;
            return renewResult;
        }

        @Override
        public void cancelPolicyLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
            lastCancelledLeaseId = leaseId;
        }

        @Override
        public Object getAdmin() {
            return this;
        }

        @Override
        public String getClassAnnotation() {
            return null;
        }

        @Override
        public String getCertFactoryType() {
            return null;
        }

        @Override
        public String getCertPathEncoding() {
            return null;
        }

        @Override
        public byte[] getEncodedCerts() {
            return null;
        }

        @Override
        public Object getServiceProxy() {
            return this;
        }

        @Override
        public Entry[] getServiceAttributes() {
            return new Entry[0];
        }

        @Override
        public ServiceID serviceID() {
            return new ServiceID(0L, 0L);
        }

        @Override
        public Entry[] getLookupAttributes() {
            return new Entry[0];
        }

        @Override
        public void addLookupAttributes(Entry[] attrSets) {
        }

        @Override
        public void modifyLookupAttributes(Entry[] attrSetTemplates, Entry[] attrSets) {
        }

        @Override
        public String[] getLookupGroups() {
            return new String[0];
        }

        @Override
        public void addLookupGroups(String[] groups) {
        }

        @Override
        public void removeLookupGroups(String[] groups) {
        }

        @Override
        public void setLookupGroups(String[] groups) {
        }

        @Override
        public LookupLocator[] getLookupLocators() {
            return new LookupLocator[0];
        }

        @Override
        public void addLookupLocators(LookupLocator[] locators) {
        }

        @Override
        public void removeLookupLocators(LookupLocator[] locators) {
        }

        @Override
        public void setLookupLocators(LookupLocator[] locators) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public int hashCode() {
            return identity.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MockServer
                    && identity.equals(((MockServer) obj).identity);
        }
    }

    private static final class MockConstrainableServer extends MockServer
            implements RemoteMethodControl {

        private static final long serialVersionUID = 1L;

        private MethodConstraints constraints;

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            this.constraints = constraints;
            return this;
        }

        @Override
        public MethodConstraints getConstraints() {
            return constraints;
        }
    }
}
