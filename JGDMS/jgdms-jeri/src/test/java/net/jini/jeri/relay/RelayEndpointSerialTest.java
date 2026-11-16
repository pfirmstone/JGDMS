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

package net.jini.jeri.relay;

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicObjectEndpoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * DER round trip, equality and hash of {@link RelayEndpoint}.
 */
public class RelayEndpointSerialTest {

    private static Object roundTrip(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(bos)) {
            out.writeObject(o);
        }
        try (DerMarshalInputStream in = new DerMarshalInputStream(
                 new ByteArrayInputStream(bos.toByteArray()))) {
            return in.readObject();
        }
    }

    @Test
    public void derRoundTrip() throws Exception {
        RelayEndpoint ep = RelayEndpoint.getInstance("d-1.x_y");
        Object decoded = roundTrip(ep);
        Assert.assertTrue(decoded instanceof RelayEndpoint);
        Assert.assertNotSame(ep, decoded);
        Assert.assertEquals(ep, decoded);
        Assert.assertEquals("d-1.x_y", ((RelayEndpoint) decoded).getDestination());
    }

    @Test
    public void derRoundTripInsideBasicObjectEndpoint() throws Exception {
        Uuid id = UuidFactory.create(0x0123456789abcdefL, 0xfedcba9876543210L);
        BasicObjectEndpoint boe = new BasicObjectEndpoint(
            RelayEndpoint.getInstance("dest42"), id, false);
        Object decoded = roundTrip(boe);
        Assert.assertTrue(decoded instanceof BasicObjectEndpoint);
        Assert.assertEquals(boe, decoded);
        Assert.assertEquals(RelayEndpoint.getInstance("dest42"),
            ((BasicObjectEndpoint) decoded).getEndpoint());
    }

    @Test
    public void equalityAndHashByDestination() {
        RelayEndpoint a = RelayEndpoint.getInstance("same");
        RelayEndpoint b = RelayEndpoint.getInstance("same");
        RelayEndpoint c = RelayEndpoint.getInstance("other");
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertTrue(a.checkTrustEquivalence(b));
        Assert.assertNotEquals(a, c);
        Assert.assertFalse(a.checkTrustEquivalence(c));
        Assert.assertNotEquals(a, "same");
        Assert.assertTrue(a.toString().contains("same"));
    }
}
