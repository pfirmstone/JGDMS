/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.rmi.tls;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/** An RMI stub carries its client socket factory, so the factory must survive serialization and stay equal. */
public class TlsRMIClientSocketFactorySerializationTest {

    @Test
    public void theClientFactoryRoundTripsAndStaysEqual() throws Exception {
        TlsRMIClientSocketFactory original = new TlsRMIClientSocketFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(original);
        }
        Object back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            back = in.readObject();
        }
        assertNotSame(original, back);
        assertEquals("RMI shares connections between stubs whose factories are equal", original, back);
        assertEquals(original.hashCode(), back.hashCode());
    }
}
