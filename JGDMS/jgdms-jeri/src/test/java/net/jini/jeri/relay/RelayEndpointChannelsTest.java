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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import org.junit.Assert;
import org.junit.Test;

/**
 * The channel source: set-once factory, and the socket property fallback.
 * The JVM-wide factory is never installed here, so other tests sharing the
 * JVM are not affected.
 */
public class RelayEndpointChannelsTest {

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            bos.write(b);
        }
        return bos.toByteArray();
    }

    private static void echoOnce(OutboundRequestIterator it) throws IOException {
        OutboundRequest r = it.next();
        r.getRequestOutputStream().write(new byte[]{7, 8, 9});
        r.getRequestOutputStream().close();
        Assert.assertArrayEquals(new byte[]{7, 8, 9},
            readAll(r.getResponseInputStream()));
    }

    @Test
    public void secondInstallFails() throws Exception {
        RelayChannels ch = new RelayChannels("net.jini.jeri.relay.test.a");
        RelayChannelFactory first = () -> null;
        ch.install(first);
        try {
            ch.install(() -> null);
            Assert.fail("second install accepted");
        } catch (IllegalStateException expected) {
        }
        try {
            ch.install(first);
            Assert.fail("reinstall of the same factory accepted");
        } catch (IllegalStateException expected) {
        }
    }

    @Test(expected = NullPointerException.class)
    public void nullFactoryRejected() {
        new RelayChannels("net.jini.jeri.relay.test.b").install(null);
    }

    @Test
    public void factoryReturningNullFailsAtNext() throws Exception {
        RelayChannels ch = new RelayChannels("net.jini.jeri.relay.test.c");
        ch.install(() -> null);
        OutboundRequestIterator it = RelayEndpoint.getInstance("x")
            .newRequest(InvocationConstraints.EMPTY, ch);
        try {
            it.next();
            Assert.fail("next succeeded");
        } catch (IOException expected) {
        }
    }

    @Test
    public void noFactoryAndNoPropertyFailsAtNext() throws Exception {
        String prop = "net.jini.jeri.relay.test.unset";
        System.clearProperty(prop);
        RelayChannels ch = new RelayChannels(prop);
        OutboundRequestIterator it = RelayEndpoint.getInstance("x")
            .newRequest(InvocationConstraints.EMPTY, ch);
        Assert.assertTrue(it.hasNext());
        try {
            it.next();
            Assert.fail("next succeeded");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(),
                expected.getMessage().contains(prop));
        }
        Assert.assertFalse(it.hasNext());
    }

    @Test
    public void propertyNamesTheSocket() throws Exception {
        String prop = "net.jini.jeri.relay.test.socket";
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            System.setProperty(prop, relay.socketPath().toString());
            try {
                echoOnce(RelayEndpoint.getInstance("x")
                    .newRequest(InvocationConstraints.EMPTY, new RelayChannels(prop)));
            } finally {
                System.clearProperty(prop);
            }
        }
    }

    @Test
    public void publicNewRequestUsesTheSystemProperty() throws Exception {
        String old = System.getProperty(RelayChannels.SOCKET_PROPERTY);
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            System.setProperty(RelayChannels.SOCKET_PROPERTY,
                relay.socketPath().toString());
            echoOnce(RelayEndpoint.getInstance("x")
                .newRequest(InvocationConstraints.EMPTY));
            Assert.assertEquals("x", relay.nextReceived().destination);
        } finally {
            if (old == null) {
                System.clearProperty(RelayChannels.SOCKET_PROPERTY);
            } else {
                System.setProperty(RelayChannels.SOCKET_PROPERTY, old);
            }
        }
    }
}
