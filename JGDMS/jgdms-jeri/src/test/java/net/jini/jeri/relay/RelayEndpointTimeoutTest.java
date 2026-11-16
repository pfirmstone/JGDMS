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
import java.nio.channels.SocketChannel;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import org.junit.Assert;
import org.junit.Test;

/**
 * The time limit on the opening exchange.  Each test names its own timeout
 * property, so the JVM-wide one is never set.
 */
public class RelayEndpointTimeoutTest {

    @Test
    public void silentRelayTimesOut() throws Exception {
        String prop = "net.jini.jeri.relay.test.timeout.silent";
        System.setProperty(prop, "200");
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.silent()) {
            SocketChannel[] opened = new SocketChannel[1];
            RelayChannels ch = new RelayChannels(
                "net.jini.jeri.relay.test.unused", prop);
            ch.install(() -> opened[0] = relay.connect());
            long start = System.nanoTime();
            try {
                RelayEndpoint.getInstance("d")
                    .newRequest(InvocationConstraints.EMPTY, ch).next();
                Assert.fail("next returned");
            } catch (IOException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("200 ms"));
            }
            long ms = (System.nanoTime() - start) / 1000000L;
            Assert.assertTrue("took " + ms + " ms", ms >= 150 && ms < 10000);
            Assert.assertFalse("channel closed", opened[0].isOpen());
            RelayEndpointTestRelay.Received got = relay.nextFinished();
            Assert.assertNotNull(got);
            Assert.assertTrue(got.sawEndOfStream);
        } finally {
            System.clearProperty(prop);
        }
    }

    @Test
    public void requestOutlivesTheHandshakeDeadline() throws Exception {
        String prop = "net.jini.jeri.relay.test.timeout.outlive";
        System.setProperty(prop, "300");
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            OutboundRequest r = RelayEndpoint.getInstance("d")
                .newRequest(InvocationConstraints.EMPTY, relay.channels(prop))
                .next();
            Thread.sleep(700);
            r.getRequestOutputStream().write(new byte[]{1, 2});
            r.getRequestOutputStream().close();
            InputStream in = r.getResponseInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) >= 0) {
                bos.write(b);
            }
            Assert.assertArrayEquals(new byte[]{1, 2}, bos.toByteArray());
        } finally {
            System.clearProperty(prop);
        }
    }

    @Test
    public void timeoutPropertyValues() {
        String prop = "net.jini.jeri.relay.test.timeout.values";
        RelayChannels ch = new RelayChannels("net.jini.jeri.relay.test.unused", prop);
        try {
            System.clearProperty(prop);
            Assert.assertEquals(30000L, ch.handshakeTimeoutMillis());
            System.setProperty(prop, " 250 ");
            Assert.assertEquals(250L, ch.handshakeTimeoutMillis());
            System.setProperty(prop, "0");
            Assert.assertEquals(1L, ch.handshakeTimeoutMillis());
            System.setProperty(prop, "-5");
            Assert.assertEquals(1L, ch.handshakeTimeoutMillis());
            System.setProperty(prop, "soon");
            Assert.assertEquals(30000L, ch.handshakeTimeoutMillis());
        } finally {
            System.clearProperty(prop);
        }
        Assert.assertEquals(RelayChannels.HANDSHAKE_TIMEOUT_PROPERTY,
            "net.jini.jeri.relay.handshakeTimeoutMs");
    }
}
