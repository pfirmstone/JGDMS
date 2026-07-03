package au.net.zeus.jgdms.policy.proxy;

import net.jini.io.MarshalledInstance;
import org.apache.river.api.io.AtomicMarshalledInstance;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PolicyUpdateEventTest {

    @Test
    public void testAtomicSerialRoundTrip() throws Exception {
        PolicyUpdateEvent event = new PolicyUpdateEvent("source", 11L, 22L, new MarshalledInstance("h"));

        PolicyUpdateEvent roundTrip = roundTrip(event);

        assertEquals(11L, roundTrip.getID());
        assertEquals(22L, roundTrip.getSequenceNumber());
        assertEquals("source", roundTrip.getSource());
    }

    @Test
    public void testConstructorSetsFields() throws Exception {
        MarshalledInstance handback = new MarshalledInstance("hb");
        PolicyUpdateEvent event = new PolicyUpdateEvent("source", 5L, 9L, handback);

        assertEquals(5L, event.getID());
        assertEquals(9L, event.getSequenceNumber());
        assertEquals("source", event.getSource());
        assertNotNull(event.getRegistrationInstance());
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        // java.io serialization is disabled on the event hierarchy
        // (readObject/writeObject throw NotSerializableException); marshal via
        // the @AtomicSerial engine that RemoteEvent actually uses on the wire.
        return (T) new AtomicMarshalledInstance(value).get(false);
    }
}
