package au.net.zeus.jgdms.policy.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.io.MarshalledInstance;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(value);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        return (T) in.readObject();
    }
}
