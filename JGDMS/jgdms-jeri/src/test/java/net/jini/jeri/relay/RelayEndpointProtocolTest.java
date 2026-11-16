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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import org.junit.Assert;
import org.junit.Test;

/**
 * The client side of the relay protocol, checked against a test relay on a
 * Unix domain socket: header bytes, request and response transfer, abort,
 * and the failures of the opening exchange.
 */
public class RelayEndpointProtocolTest {

    private static final String DEST = "svc-01.a_b";

    private static OutboundRequest start(RelayEndpointTestRelay relay,
                                         InvocationConstraints c)
        throws IOException
    {
        OutboundRequestIterator it =
            RelayEndpoint.getInstance(DEST).newRequest(c, relay.channels());
        Assert.assertTrue(it.hasNext());
        OutboundRequest r = it.next();
        Assert.assertFalse("one request per iterator", it.hasNext());
        return r;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int n;
        while ((n = in.read(b)) >= 0) {
            bos.write(b, 0, n);
        }
        return bos.toByteArray();
    }

    @Test
    public void headerWithEmptyConstraintsIsExact() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            RelayEndpointTestRelay.Received got = relay.nextReceived();
            r.abort();
            byte[] d = DEST.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream want = new ByteArrayOutputStream();
            want.write(new byte[]{'J', 'R', 'L', 'Y', 2, 0, (byte) d.length});
            want.write(d);
            want.write(new byte[]{0, 0, 0, 0, 0});
            Assert.assertArrayEquals(want.toByteArray(), got.rawHeader);
            Assert.assertEquals(DEST, got.destination);
        }
    }

    @Test
    public void headerCarriesDerEncodedConstraints() throws Exception {
        InvocationConstraints c = new InvocationConstraints(
            new InvocationConstraint[]{Integrity.YES},
            new InvocationConstraint[]{Confidentiality.YES});
        ByteArrayOutputStream der = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(der)) {
            out.writeObject(c);
        }
        byte[] cons = der.toByteArray();
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequest r = start(relay, c);
            RelayEndpointTestRelay.Received got = relay.nextReceived();
            r.abort();
            byte[] d = DEST.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer want = ByteBuffer.allocate(4 + 1 + 2 + d.length + 4 + cons.length + 1);
            want.put(new byte[]{'J', 'R', 'L', 'Y', 2});
            want.putShort((short) d.length).put(d);
            want.putInt(cons.length).put(cons).put((byte) 0);
            Assert.assertArrayEquals(want.array(), got.rawHeader);
            Object decoded = new DerMarshalInputStream(
                new ByteArrayInputStream(got.constraintBytes)).readObject();
            Assert.assertEquals(c, decoded);
        }
    }

    @Test
    public void requestAndResponseRoundTrip() throws Exception {
        byte[] payload = new byte[70000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            Assert.assertFalse("nothing sent yet", r.getDeliveryStatus());
            Assert.assertEquals(InvocationConstraints.EMPTY,
                r.getUnfulfilledConstraints());
            List<Object> context = new ArrayList<Object>();
            r.populateContext(context);
            Assert.assertTrue(context.isEmpty());
            OutputStream out = r.getRequestOutputStream();
            out.write(payload, 0, 10);
            Assert.assertTrue(r.getDeliveryStatus());
            out.write(payload, 10, payload.length - 10);
            out.close();
            byte[] response = readAll(r.getResponseInputStream());
            r.getResponseInputStream().close();
            RelayEndpointTestRelay.Received got = relay.nextFinished();
            Assert.assertNotNull(got);
            Assert.assertArrayEquals(payload, got.request);
            Assert.assertArrayEquals(payload, response);
        }
    }

    @Test
    public void emptyRequestIsEndedByClose() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            r.getRequestOutputStream().close();
            Assert.assertTrue(r.getDeliveryStatus());
            Assert.assertEquals(0, readAll(r.getResponseInputStream()).length);
            Assert.assertEquals(0, relay.nextFinished().request.length);
        }
    }

    @Test
    public void flushCountsAsPossibleDelivery() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            r.getRequestOutputStream().flush();
            Assert.assertTrue(r.getDeliveryStatus());
            r.abort();
        }
    }

    @Test
    public void handlerSeesRequestAndWritesResponse() throws Exception {
        byte[] reply = "pong".getBytes(StandardCharsets.UTF_8);
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.handler(
                (h, in, out) -> {
                    if (Arrays.equals("ping".getBytes(StandardCharsets.UTF_8),
                                      readAll(in))) {
                        out.write(reply);
                    }
                })) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            r.getRequestOutputStream().write("ping".getBytes(StandardCharsets.UTF_8));
            r.getRequestOutputStream().close();
            Assert.assertArrayEquals(reply, readAll(r.getResponseInputStream()));
        }
    }

    @Test
    public void abortClosesTheConnection() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequest r = start(relay, InvocationConstraints.EMPTY);
            r.getRequestOutputStream().write(new byte[]{1, 2, 3});
            r.abort();
            RelayEndpointTestRelay.Received got = relay.nextFinished();
            Assert.assertNotNull("relay saw the connection end", got);
            Assert.assertTrue(got.sawEndOfStream);
            try {
                r.getRequestOutputStream().write(4);
                Assert.fail("write after abort");
            } catch (IOException expected) {
            }
            try {
                r.getResponseInputStream().read();
                Assert.fail("read after abort");
            } catch (IOException expected) {
            }
        }
    }

    /** Runs the opening exchange against a relay and returns the IOException from next(). */
    private static IOException failedStart(RelayEndpointTestRelay relay,
                                           SocketChannel[] opened)
        throws Exception
    {
        RelayChannels ch = new RelayChannels("net.jini.jeri.relay.test.unused");
        ch.install(() -> opened[0] = relay.connect());
        OutboundRequestIterator it = RelayEndpoint.getInstance(DEST)
            .newRequest(InvocationConstraints.EMPTY, ch);
        try {
            it.next();
        } catch (IOException e) {
            return e;
        }
        Assert.fail("opening exchange should have failed");
        return null;
    }

    @Test
    public void refusalCarriesTheReason() throws Exception {
        String reason = "destination expired";
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.refuse(reason)) {
            SocketChannel[] opened = new SocketChannel[1];
            IOException e = failedStart(relay, opened);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains(reason));
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }

    @Test
    public void noReplyIsAnError() throws Exception {
        try (RelayEndpointTestRelay relay =
                 RelayEndpointTestRelay.rawReply(new byte[0])) {
            SocketChannel[] opened = new SocketChannel[1];
            failedStart(relay, opened);
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }

    @Test
    public void truncatedRefusalIsAnError() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.rawReply(
                 new byte[]{1, 0, 10, 'a', 'b'})) {
            SocketChannel[] opened = new SocketChannel[1];
            failedStart(relay, opened);
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }

    @Test
    public void truncatedReasonLengthIsAnError() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.rawReply(
                 new byte[]{1, 0})) {
            SocketChannel[] opened = new SocketChannel[1];
            failedStart(relay, opened);
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }

    @Test
    public void oversizedReasonIsBadFraming() throws Exception {
        byte[] reply = new byte[3 + 1025];
        reply[0] = 1;
        reply[1] = (byte) (1025 >>> 8);
        reply[2] = (byte) 1025;
        Arrays.fill(reply, 3, reply.length, (byte) 'x');
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.rawReply(reply)) {
            SocketChannel[] opened = new SocketChannel[1];
            IOException e = failedStart(relay, opened);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("1025"));
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }

    @Test
    public void malformedReasonIsBadFraming() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.rawReply(
                 new byte[]{1, 0, 2, (byte) 0xC3, (byte) 0x28})) {
            SocketChannel[] opened = new SocketChannel[1];
            IOException e = failedStart(relay, opened);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("UTF-8"));
            Assert.assertFalse("channel closed", opened[0].isOpen());
        }
    }
}
