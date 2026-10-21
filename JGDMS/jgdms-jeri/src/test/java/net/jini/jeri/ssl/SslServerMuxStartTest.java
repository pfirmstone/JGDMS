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
package net.jini.jeri.ssl;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.security.auth.Subject;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A peer that completes the TLS handshake, and so holds a connection of the
 * server, but never sends the mux connection header, is closed when the mux
 * start timeout has passed; a peer that does start the mux is not.
 */
public class SslServerMuxStartTest {

    private static final String START_TIMEOUT =
        "org.apache.river.jeri.mux.serverStartTimeout";

    @BeforeClass
    public static void setUpClass() throws Exception {
        S0Fixture.setUp();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        S0Fixture.tearDown();
    }

    private static int threadsNamed(String name) {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals(name) && t.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** A listener that serves connections with the real connection manager. */
    private static S0Fixture.Listener serve() throws Exception {
        return new S0Fixture.Listener(null, null, null, request -> { });
    }

    @Test(timeout = 60000)
    public void testAuthenticatedPeerThatSendsNothingIsClosed() throws Exception {
        S0Fixture.withProperty(START_TIMEOUT, "1000", (Callable<Void>) () -> {
            try (S0Fixture.Listener l = serve()) {
                /* Warm up: a client that starts the mux. */
                try (SSLSocket warm = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    warm.startHandshake();
                }
                Assert.assertTrue(S0Fixture.await(
                    () -> l.handle.connectionCount() == 0, 10_000));
                int readers = threadsNamed("mux reader");
                int writers = threadsNamed("mux writer");
                try (SSLSocket s = (SSLSocket) S0Fixture.clientSocketFactory()
                         .createSocket("localhost", l.port))
                {
                    s.setSoTimeout(20_000);
                    s.startHandshake();
                    long t0 = System.nanoTime();
                    Assert.assertTrue("the connection is registered",
                        S0Fixture.await(() -> l.handle.connectionCount() == 1, 5000));
                    Assert.assertTrue("a reader and a writer", S0Fixture.await(
                        () -> threadsNamed("mux reader") == readers + 1
                              && threadsNamed("mux writer") == writers + 1, 5000));
                    long ms = S0Fixture.awaitClosed(s, 10_000);
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                    Assert.assertTrue("closed after " + ms + " ms, " + elapsed
                        + " ms from the handshake", ms >= 0 && elapsed >= 800
                        && elapsed < 4000);
                }
                Assert.assertTrue("the slot is released", S0Fixture.await(
                    () -> l.handle.connectionCount() == 0, 5000));
                Assert.assertTrue("the threads end", S0Fixture.await(
                    () -> threadsNamed("mux reader") == readers
                          && threadsNamed("mux writer") == writers, 5000));
            }
            return null;
        });
    }

    @Test(timeout = 60000)
    public void testClientThatStartsTheMuxIsNotClosed() throws Exception {
        S0Fixture.withProperty(START_TIMEOUT, "800", (Callable<Void>) () -> {
            try (S0Fixture.Listener l = serve()) {
                Subject.callAs(S0Fixture.client.subject, (Callable<Void>) () -> {
                    net.jini.jeri.Endpoint ep =
                        SslEndpoint.getInstance("localhost", l.port);
                    net.jini.jeri.OutboundRequestIterator it = ep.newRequest(
                        new InvocationConstraints(new InvocationConstraint[] {
                            ClientAuthentication.YES, ServerAuthentication.YES,
                            Integrity.YES, Confidentiality.YES }, null));
                    net.jini.jeri.OutboundRequest request = it.next();
                    Assert.assertTrue(S0Fixture.await(
                        () -> l.handle.connectionCount() == 1, 5000));
                    Thread.sleep(2400);
                    Assert.assertEquals("a connection whose client started the mux "
                        + "stays", 1, l.handle.connectionCount());
                    request.abort();
                    return null;
                });
            }
            return null;
        });
    }
}
