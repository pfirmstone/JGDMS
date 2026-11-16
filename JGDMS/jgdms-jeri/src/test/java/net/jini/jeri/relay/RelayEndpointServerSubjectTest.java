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
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.context.ServerSubject;
import net.jini.jeri.OutboundRequest;
import org.junit.Assert;
import org.junit.Test;

/**
 * The server principals in the relay's acceptance reply, and the
 * {@link ServerSubject} built from them.
 */
public class RelayEndpointServerSubjectTest {

    private static final String X500 = "javax.security.auth.x500.X500Principal";
    private static final String SPIFFE = "au.net.zeus.jgdms.spiffe.SpiffePrincipal";

    private static OutboundRequest start(RelayEndpointTestRelay relay)
        throws IOException
    {
        return RelayEndpoint.getInstance("d")
            .newRequest(InvocationConstraints.EMPTY, relay.channels()).next();
    }

    private static Subject serverSubject(OutboundRequest r) {
        List<Object> context = new ArrayList<Object>();
        r.populateContext(context);
        Assert.assertEquals(1, context.size());
        Assert.assertTrue(context.get(0) instanceof ServerSubject);
        return ((ServerSubject) context.get(0)).getServerSubject();
    }

    /** Runs the opening exchange against a raw reply; returns the IOException. */
    private static IOException refused(byte[] reply) throws Exception {
        try (RelayEndpointTestRelay relay =
                 RelayEndpointTestRelay.rawReply(reply)) {
            SocketChannel[] opened = new SocketChannel[1];
            RelayChannels ch = new RelayChannels("net.jini.jeri.relay.test.unused");
            ch.install(() -> opened[0] = relay.connect());
            try {
                RelayEndpoint.getInstance("d")
                    .newRequest(InvocationConstraints.EMPTY, ch).next();
            } catch (IOException e) {
                Assert.assertFalse("channel closed", opened[0].isOpen());
                return e;
            }
            Assert.fail("reply accepted");
            return null;
        }
    }

    private static String repeat(char c, int n) {
        char[] a = new char[n];
        java.util.Arrays.fill(a, c);
        return new String(a);
    }

    @Test
    public void x500PrincipalsReachTheServerSubject() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echoAs(
                 X500, "CN=svc, O=Zeus", X500, "CN=other")) {
            OutboundRequest r = start(relay);
            Subject s = serverSubject(r);
            Assert.assertTrue(s.isReadOnly());
            Set<Principal> want = new HashSet<Principal>();
            want.add(new X500Principal("CN=svc, O=Zeus"));
            want.add(new X500Principal("CN=other"));
            Assert.assertEquals(want, new HashSet<Principal>(s.getPrincipals()));
            try {
                s.getPrincipals().clear();
                Assert.fail("principals modifiable");
            } catch (UnsupportedOperationException | IllegalStateException expected) {
            }
            r.getRequestOutputStream().write(5);
            r.getRequestOutputStream().close();
            Assert.assertEquals(5, r.getResponseInputStream().read());
        }
    }

    @Test
    public void sixteenPrincipalsAreAccepted() throws Exception {
        String[] pairs = new String[32];
        for (int i = 0; i < 16; i++) {
            pairs[2 * i] = X500;
            pairs[2 * i + 1] = "CN=p" + i;
        }
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echoAs(pairs)) {
            OutboundRequest r = start(relay);
            Assert.assertEquals(16, serverSubject(r).getPrincipals().size());
            r.abort();
        }
    }

    @Test
    public void noPrincipalsAddsNothing() throws Exception {
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echo()) {
            OutboundRequest r = start(relay);
            List<Object> context = new ArrayList<Object>();
            r.populateContext(context);
            Assert.assertTrue(context.isEmpty());
            r.abort();
        }
    }

    @Test
    public void spiffePrincipalIsBuiltWhenAvailable() throws Exception {
        Class<?> spiffe;
        try {
            spiffe = Class.forName(SPIFFE, false,
                ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            spiffe = null;
        }
        byte[] reply = RelayEndpointTestRelay.acceptReply(
            SPIFFE, "spiffe://example.org/svc");
        if (spiffe == null) {
            IOException e = refused(reply);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains(SPIFFE));
            return;
        }
        try (RelayEndpointTestRelay relay = RelayEndpointTestRelay.echoAs(
                 SPIFFE, "spiffe://example.org/svc")) {
            OutboundRequest r = start(relay);
            Set<Principal> ps = serverSubject(r).getPrincipals();
            Assert.assertEquals(1, ps.size());
            Principal p = ps.iterator().next();
            Assert.assertEquals(spiffe, p.getClass());
            Assert.assertEquals("spiffe://example.org/svc", p.getName());
            r.abort();
        }
    }

    @Test
    public void unknownPrincipalClassIsRefused() throws Exception {
        IOException e = refused(RelayEndpointTestRelay.acceptReply(
            "com.sun.security.auth.UserPrincipal", "root"));
        Assert.assertTrue(e.getMessage(),
            e.getMessage().contains("not accepted"));
        refused(RelayEndpointTestRelay.acceptReply(
            X500, "CN=a", "javax.security.auth.x500.X500PrincipalX", "CN=b"));
    }

    @Test
    public void malformedPrincipalNamesAreRefused() throws Exception {
        refused(RelayEndpointTestRelay.acceptReply(X500, "not a name"));
        if (spiffeAvailable()) {
            refused(RelayEndpointTestRelay.acceptReply(SPIFFE, "http://x"));
        }
    }

    private static boolean spiffeAvailable() {
        try {
            Class.forName(SPIFFE, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    public void tooManyPrincipalsAreRefused() throws Exception {
        IOException e = refused(new byte[]{0, 0, 17});
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("17"));
    }

    @Test
    public void overLengthClassNameIsRefused() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{0, 0, 1});
        RelayEndpointTestRelay.writeString(bos, repeat('a', 1025));
        RelayEndpointTestRelay.writeString(bos, "CN=a");
        IOException e = refused(bos.toByteArray());
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("1025"));
    }

    @Test
    public void overLengthPrincipalNameIsRefused() throws Exception {
        IOException e = refused(RelayEndpointTestRelay.acceptReply(
            X500, "CN=" + repeat('a', 1022)));
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("1025"));
    }

    @Test
    public void malformedUtf8InPrincipalIsRefused() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{0, 0, 1});
        RelayEndpointTestRelay.writeString(bos, X500);
        bos.write(new byte[]{0, 2, (byte) 0xC3, (byte) 0x28});
        IOException e = refused(bos.toByteArray());
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("UTF-8"));
    }

    @Test
    public void truncatedPrincipalBlockIsRefused() throws Exception {
        byte[] full = RelayEndpointTestRelay.acceptReply(X500, "CN=a");
        for (int cut : new int[]{1, 2, 3, 5, full.length - 7, full.length - 1}) {
            byte[] part = new byte[cut];
            System.arraycopy(full, 0, part, 0, cut);
            refused(part);
        }
        refused(new byte[]{0, 0, 2, 0, 0, 0, 0});
    }
}
