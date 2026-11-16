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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import org.junit.Assert;
import org.junit.Test;

/**
 * The per-call relay token: binding, restoring, isolation between threads,
 * and its place at the end of the request header.
 */
public class RelayEndpointCallTokenTest {

    private static final byte[] A = {1, 2, 3, (byte) 0xFF};
    private static final byte[] B = {9};

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i + 1);
        }
        return b;
    }

    /** Returns the token the relay received for one request from the iterator. */
    private static byte[] sent(RelayEndpointTestRelay relay,
                               OutboundRequestIterator it) throws Exception
    {
        OutboundRequest r = it.next();
        RelayEndpointTestRelay.Received got = relay.nextReceived();
        r.abort();
        byte[] raw = got.rawHeader;
        int len = raw[raw.length - got.token.length - 1] & 0xFF;
        Assert.assertEquals(got.token.length, len);
        return got.token;
    }

    @Test
    public void headerCarriesTheBoundToken() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            OutboundRequestIterator it = RelayCallToken.callWith(A, () ->
                RelayEndpoint.getInstance("d")
                    .newRequest(InvocationConstraints.EMPTY, relay.channels()));
            Assert.assertArrayEquals(A, sent(relay, it));
            byte[] max = bytes(64);
            it = RelayCallToken.callWith(max, () ->
                RelayEndpoint.getInstance("d")
                    .newRequest(InvocationConstraints.EMPTY, relay.channels()));
            Assert.assertArrayEquals(max, sent(relay, it));
        }
    }

    @Test
    public void noBindingSendsLengthZero() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            Assert.assertEquals(0, sent(relay, RelayEndpoint.getInstance("d")
                .newRequest(InvocationConstraints.EMPTY, relay.channels())).length);
        }
    }

    @Test
    public void tokenIsCapturedByNewRequest() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.hold()) {
            RelayEndpoint ep = RelayEndpoint.getInstance("d");
            OutboundRequestIterator unbound =
                ep.newRequest(InvocationConstraints.EMPTY, relay.channels());
            byte[] got = RelayCallToken.callWith(A, () -> sent(relay, unbound));
            Assert.assertEquals("next inside a binding, newRequest outside",
                0, got.length);
            OutboundRequestIterator bound = RelayCallToken.callWith(A, () ->
                ep.newRequest(InvocationConstraints.EMPTY, relay.channels()));
            Assert.assertArrayEquals("newRequest inside, next outside",
                A, sent(relay, bound));
        }
    }

    @Test
    public void bindingIsRestoredAfterReturn() throws Exception {
        Assert.assertNull(RelayCallToken.current());
        byte[] inside = RelayCallToken.callWith(A, RelayCallToken::current);
        Assert.assertArrayEquals(A, inside);
        Assert.assertNull(RelayCallToken.current());
    }

    @Test
    public void bindingIsRestoredAfterThrow() throws Exception {
        try {
            RelayCallToken.callWith(A, () -> {
                throw new IOException("boom");
            });
            Assert.fail("exception not propagated");
        } catch (IOException expected) {
            Assert.assertEquals("boom", expected.getMessage());
        }
        Assert.assertNull(RelayCallToken.current());
    }

    @Test
    public void nestedBindingRestoresTheOuterToken() throws Exception {
        RelayCallToken.callWith(A, () -> {
            byte[] inner = RelayCallToken.callWith(B, RelayCallToken::current);
            Assert.assertArrayEquals(B, inner);
            Assert.assertArrayEquals(A, RelayCallToken.current());
            try {
                RelayCallToken.callWith(B, () -> {
                    throw new IllegalStateException();
                });
                Assert.fail("exception not propagated");
            } catch (IllegalStateException expected) {
            }
            Assert.assertArrayEquals(A, RelayCallToken.current());
            return null;
        });
        Assert.assertNull(RelayCallToken.current());
    }

    @Test
    public void bindingIsNotSeenByANewThread() throws Exception {
        AtomicReference<Object> seen = new AtomicReference<Object>("unset");
        RelayCallToken.callWith(A, () -> {
            Thread t = new Thread(() -> seen.set(RelayCallToken.current()));
            t.start();
            t.join();
            return null;
        });
        Assert.assertNull(seen.get());
    }

    @Test
    public void tokenIsCopied() throws Exception {
        byte[] mine = A.clone();
        RelayCallToken.callWith(mine, () -> {
            mine[0] = 42;
            Assert.assertArrayEquals(A, RelayCallToken.current());
            RelayCallToken.current()[1] = 42;
            Assert.assertArrayEquals(A, RelayCallToken.current());
            return null;
        });
    }

    @Test
    public void invalidTokensAreRejected() throws Exception {
        try {
            RelayCallToken.callWith(null, () -> null);
            Assert.fail("null accepted");
        } catch (NullPointerException expected) {
        }
        for (byte[] bad : new byte[][]{new byte[0], bytes(65)}) {
            boolean[] ran = {false};
            try {
                RelayCallToken.callWith(bad, () -> ran[0] = true);
                Assert.fail("accepted length " + bad.length);
            } catch (IllegalArgumentException expected) {
                Assert.assertFalse(expected.getMessage().contains(
                    Arrays.toString(bad)));
            }
            Assert.assertFalse("action ran", ran[0]);
        }
        Assert.assertEquals(Boolean.TRUE,
            RelayCallToken.callWith(new byte[]{0}, () -> Boolean.TRUE));
    }
}
