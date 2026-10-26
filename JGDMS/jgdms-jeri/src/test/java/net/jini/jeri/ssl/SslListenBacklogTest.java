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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ServerSocketFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ssl.SslServerEndpointImpl.SslListenCookie;
import net.jini.jeri.ssl.SslServerEndpointImpl.SslListenEndpoint;
import org.apache.river.jeri.internal.ListenBacklog;
import org.apache.river.jeri.internal.ListenBacklogProbe;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * The TLS and HTTPS server endpoints bind with {@link ListenBacklog}, not the
 * JDK default of 50.  The listen endpoint's server socket is created without
 * an accept thread, so nothing accepts and the kernel queue alone decides how
 * many of a burst of connectors complete.
 */
public class SslListenBacklogTest {

    private String saved;
    private ServerSocket serverSocket;

    @Before
    public void setUp() {
        saved = ListenBacklogProbe.set(null);
    }

    @After
    public void tearDown() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
        ListenBacklogProbe.restore(saved);
    }

    /** Returns the listen endpoint that a server endpoint enumerates. */
    private static SslListenEndpoint listenEndpoint(ServerEndpoint se)
        throws IOException
    {
        final SslListenEndpoint[] le = new SslListenEndpoint[1];
        se.enumerateListenEndpoints(new ListenContext() {
            public ListenCookie addListenEndpoint(ListenEndpoint e) {
                le[0] = (SslListenEndpoint) e;
                return new SslListenCookie(1, e);
            }
        });
        return le[0];
    }

    private int burst(ServerEndpoint se, String backlog) throws IOException {
        ListenBacklogProbe.set(backlog);
        serverSocket = listenEndpoint(se).createServerSocket();
        return ListenBacklogProbe.burst(new InetSocketAddress(
            InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()),
            false);
    }

    private static void assertDefaultHoldsTheBurst(String name, int completed) {
        System.out.println(name + " default backlog: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        Assert.assertEquals(ListenBacklogProbe.CONNECTORS, completed);
    }

    private static void assertSmallBacklogLimits(String name, int completed) {
        System.out.println(name + " backlog 4: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        // nothing accepts, so the queue holds exactly backlog + 1 on Linux
        Assert.assertTrue("completed " + completed, completed >= 1);
        Assert.assertTrue("completed " + completed, completed <= 20);
    }

    private static void assumeKernelHoldsTheBurst() {
        Assume.assumeTrue("kernel caps the backlog below the burst",
            ListenBacklogProbe.kernelCap() > ListenBacklogProbe.CONNECTORS);
    }

    @Test
    public void engineDefaultBacklogHoldsTheWholeBurst() throws Exception {
        assumeKernelHoldsTheBurst();
        assertDefaultHoldsTheBurst("tls",
            burst(SslServerEndpoint.getInstance(0), null));
    }

    @Test
    public void engineSmallBacklogLimitsTheBurst() throws Exception {
        assertSmallBacklogLimits("tls",
            burst(SslServerEndpoint.getInstance(0), "4"));
    }

    @Test
    public void httpsDefaultBacklogHoldsTheWholeBurst() throws Exception {
        assumeKernelHoldsTheBurst();
        assertDefaultHoldsTheBurst("https",
            burst(HttpsServerEndpoint.getInstance(0), null));
    }

    @Test
    public void httpsSmallBacklogLimitsTheBurst() throws Exception {
        assertSmallBacklogLimits("https",
            burst(HttpsServerEndpoint.getInstance(0), "4"));
    }

    /** Records the backlog a server socket factory is asked for. */
    private static final class RecordingFactory extends ServerSocketFactory {
        final AtomicInteger backlog = new AtomicInteger(-1);
        public ServerSocket createServerSocket(int port) throws IOException {
            backlog.set(-2);
            return new ServerSocket(port);
        }
        public ServerSocket createServerSocket(int port, int backlog)
            throws IOException
        {
            this.backlog.set(backlog);
            return new ServerSocket(port, backlog);
        }
        public ServerSocket createServerSocket(int port, int backlog,
                                               InetAddress addr)
            throws IOException
        {
            this.backlog.set(backlog);
            return new ServerSocket(port, backlog, addr);
        }
    }

    private void assertFactoryReceivesTheBacklog(ServerEndpoint se,
                                                 RecordingFactory f)
        throws IOException
    {
        SslListenEndpoint le = listenEndpoint(se);
        serverSocket = le.createServerSocket();
        serverSocket.close();
        Assert.assertEquals(Integer.MAX_VALUE, f.backlog.get());
        ListenBacklogProbe.set("9");
        serverSocket = le.createServerSocket();
        serverSocket.close();
        Assert.assertEquals(9, f.backlog.get());
        ListenBacklogProbe.set("0");
        serverSocket = le.createServerSocket();
        Assert.assertEquals(0, f.backlog.get());
    }

    @Test
    public void tlsServerSocketFactoryReceivesTheBacklog() throws Exception {
        RecordingFactory f = new RecordingFactory();
        assertFactoryReceivesTheBacklog(
            SslServerEndpoint.getInstance(null, 0, null, f), f);
    }

    @Test
    public void httpsServerSocketFactoryReceivesTheBacklog() throws Exception {
        RecordingFactory f = new RecordingFactory();
        assertFactoryReceivesTheBacklog(
            HttpsServerEndpoint.getInstance(null, 0, null, f), f);
    }
}
