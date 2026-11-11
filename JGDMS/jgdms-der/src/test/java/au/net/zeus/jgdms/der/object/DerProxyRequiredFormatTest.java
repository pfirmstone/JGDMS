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
// Same package as DerProxySerializer so the test can build a carrier directly.
package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import au.net.zeus.jgdms.der.object.DerProxyMarshalledInstanceTest.FixedHandler;
import au.net.zeus.jgdms.der.object.DerProxyMarshalledInstanceTest.Greeter;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariant: when a call requires {@code MarshallingFormat.ATOMIC_DER}, a smart proxy nested in a
 * {@link DerProxySerializer} carrier must itself be a DER payload. The payload format is named on
 * the wire by the sender, so it is checked against the receiver's required format before the
 * nested payload is decoded -- otherwise a DER call can be made to run a JOSS decode.
 */
public class DerProxyRequiredFormatTest {

    private static final String DER = MarshallingFormat.ATOMIC_DER.getFormat();

    /** Writes a carrier whose serviceProxy is {@code serviceProxy}, through the DER object stream. */
    private static byte[] encodeCarrier(MarshalledInstance serviceProxy) throws IOException {
        DerProxySerializer carrier = new DerProxySerializer(
                new FixedHandler("bootstrap"), serviceProxy, Collections.emptyList(), null, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ClassLoader cl = DerProxyRequiredFormatTest.class.getClassLoader();
        DerMarshalOutputStream out = new DerMarshalOutputStream(bos, Collections.emptyList(), cl);
        out.writeObject(carrier);
        out.flush();
        return bos.toByteArray();
    }

    private static Object decode(byte[] bytes, String requiredFormat) throws Exception {
        ClassLoader cl = DerProxyRequiredFormatTest.class.getClassLoader();
        ResolutionContext rc = new ResolutionContext(cl, false, cl).withRequiredPayloadFormat(requiredFormat);
        return new DerMarshalInputStream(new ByteArrayInputStream(bytes), rc).readObject();
    }

    private static MarshalledInstance jossPayload() throws IOException {
        // The plain constructor marshals with JOSS: its payloadFormat is "JOSS".
        return new MarshalledInstance("joss-payload", Collections.emptyList());
    }

    @Test
    public void jossSmartProxyIsRejectedWhenDerRequired() throws Exception {
        byte[] bytes = encodeCarrier(jossPayload());
        assertThrows(IOException.class, () -> decode(bytes, DER));
    }

    @Test
    public void jossSmartProxyIsDecodedWhenNothingRequired() throws Exception {
        byte[] bytes = encodeCarrier(jossPayload());
        assertEquals("joss-payload", decode(bytes, null));
    }

    @Test
    public void derSmartProxyIsAcceptedWhenDerRequired() throws Exception {
        Greeter real = (Greeter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Greeter.class}, new FixedHandler("hello"));
        MarshalledInstance der = new DerMarshalledInstance(real, Collections.emptyList(), false);
        Object back = decode(encodeCarrier(der), DER);
        assertTrue(back instanceof Greeter, "expected Greeter, got " + back);
        assertEquals("hello", ((Greeter) back).greet());
    }
}
