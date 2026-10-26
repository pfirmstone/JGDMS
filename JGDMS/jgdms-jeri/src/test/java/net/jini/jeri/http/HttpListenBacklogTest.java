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
package net.jini.jeri.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ServerSocketFactory;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.ServerEndpoint.ListenHandle;
import org.apache.river.jeri.internal.ListenBacklogProbe;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** The HTTP server endpoint passes the accept backlog to its socket factory. */
public class HttpListenBacklogTest {

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

    private void listen(RecordingFactory f) throws IOException {
        HttpServerEndpoint.getInstance(null, 0, null, f).enumerateListenEndpoints(
            new ListenContext() {
                public ListenCookie addListenEndpoint(ListenEndpoint e)
                    throws IOException
                {
                    handle = e.listen(new ListenBacklogProbe.NoRequests());
                    return handle.getCookie();
                }
            });
        handle.close();
    }

    @Test
    public void serverSocketFactoryReceivesTheBacklog() throws Exception {
        RecordingFactory f = new RecordingFactory();
        listen(f);
        Assert.assertEquals(Integer.MAX_VALUE, f.backlog.get());
        ListenBacklogProbe.set("9");
        listen(f);
        Assert.assertEquals(9, f.backlog.get());
    }
}
