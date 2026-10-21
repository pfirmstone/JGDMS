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
package net.jini.jeri.tcp;

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
import net.jini.jeri.ServerEndpoint.ListenHandle;
import org.apache.river.jeri.internal.ListenBacklog;
import org.apache.river.jeri.internal.ListenBacklogProbe;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * The TCP server endpoint binds with {@link ListenBacklog}, not the JDK default
 * of 50.  The accept loop is held up after its first accepted connection, so
 * the kernel queue alone decides how many of a burst of connectors complete.
 */
public class TcpListenBacklogTest {

    private String saved;
    private ListenHandle handle;

    @Before
    public void setUp() {
        saved = ListenBacklogProbe.set(null);
    }

    @After
    public void tearDown() {
        if (handle != null) {
            handle.close();
        }
        ListenBacklogProbe.restore(saved);
    }

    /** Listens and returns the port. */
    private int listen(ServerEndpoint se) throws Exception {
        final ListenEndpoint[] le = new ListenEndpoint[1];
        se.enumerateListenEndpoints(new ListenContext() {
            public ListenCookie addListenEndpoint(ListenEndpoint e)
                throws IOException
            {
                le[0] = e;
                handle = e.listen(new ListenBacklogProbe.NoRequests());
                return handle.getCookie();
            }
        });
        ServerSocket ss = (ServerSocket)
            ListenBacklogProbe.field(handle, "serverSocket");
        return ss.getLocalPort();
    }

    /**
     * Runs a burst against a listener whose accept loop is held up on the
     * listen handle's lock after it accepts its first connection.
     */
    private int burstAgainstHeldListener(String backlog) throws Exception {
        ListenBacklogProbe.set(backlog);
        int port = listen(TcpServerEndpoint.getInstance(0));
        Object held = ListenBacklogProbe.field(handle, "lock");
        try {
            synchronized (held) {
                return ListenBacklogProbe.burst(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), port), false);
            }
        } finally {
            handle.close();
        }
    }

    @Test
    public void defaultBacklogHoldsTheWholeBurst() throws Exception {
        Assume.assumeTrue("kernel caps the backlog below the burst",
            ListenBacklogProbe.kernelCap() > ListenBacklogProbe.CONNECTORS + 8);
        int completed = burstAgainstHeldListener(null);
        System.out.println("tcp default backlog: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        Assert.assertEquals(ListenBacklogProbe.CONNECTORS, completed);
    }

    @Test
    public void smallBacklogLimitsTheBurst() throws Exception {
        int completed = burstAgainstHeldListener("4");
        System.out.println("tcp backlog 4: " + completed + " of "
            + ListenBacklogProbe.CONNECTORS + " connects completed");
        // the queue holds backlog + 1, and one connection is already accepted
        Assert.assertTrue("completed " + completed, completed >= 1);
        Assert.assertTrue("completed " + completed, completed <= 20);
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

    @Test
    public void serverSocketFactoryReceivesTheBacklog() throws Exception {
        RecordingFactory f = new RecordingFactory();
        listen(TcpServerEndpoint.getInstance(null, 0, null, f));
        handle.close();
        Assert.assertEquals(Integer.MAX_VALUE, f.backlog.get());

        ListenBacklogProbe.set("9");
        listen(TcpServerEndpoint.getInstance(null, 0, null, f));
        handle.close();
        Assert.assertEquals(9, f.backlog.get());

        ListenBacklogProbe.set("0");
        listen(TcpServerEndpoint.getInstance(null, 0, null, f));
        handle.close();
        Assert.assertEquals(0, f.backlog.get());
    }
}
