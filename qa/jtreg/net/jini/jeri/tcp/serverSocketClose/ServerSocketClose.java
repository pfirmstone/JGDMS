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
/* @test
 * @bug 4975734
 * @bug 4975747
 * @bug 4975726
 * @summary If the TcpServerEndpoint accept loop terminates for some
 * reason, like if an exception occurs for which it gives up entirely,
 * then the server socket should also be closed, so that clients are
 * not left hanging.
 *
 * @build ServerSocketClose
 * @build AbstractServerSocketFactory
 * @run main/othervm/policy=security.policy -DendpointType=tcp
 *     ServerSocketClose
 * @run main/othervm/policy=security.policy -DendpointType=http
 *     ServerSocketClose
 * @run main/othervm/policy=security.policy -DendpointType=ssl
 *     ServerSocketClose
 * @run main/othervm/policy=security.policy -DendpointType=https
 *     ServerSocketClose
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ServerSocketFactory;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.http.HttpServerEndpoint;
import net.jini.jeri.ssl.HttpsServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class ServerSocketClose {

    private static final int TIMEOUT = 5000;

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4975734\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	SSF ssf = new SSF();
	ServerEndpoint se = getServerEndpoint(ssf);
	ListenContext lc = new ListenContext() {
	    public ListenCookie addListenEndpoint(ListenEndpoint le)
		throws IOException
	    {
		return le.listen(new RequestDispatcher() {
		    public void dispatch(InboundRequest r) { }
		}).getCookie();
	    }
	};
	se.enumerateListenEndpoints(lc);

	synchronized (ssf) {
	    long now, deadline = 0;
	    while (!ssf.serverSocketClosed) {
		now = System.currentTimeMillis();
		if (deadline == 0) {
		    deadline = now + TIMEOUT;
		} else if (now >= deadline) {
		    break;
		}
		ssf.wait(deadline - now);
	    }
	    if (!ssf.serverSocketClosed) {
		throw new RuntimeException("TEST FAILED: " +
					   "server socket not closed");
	    }
	}
	System.err.println("TEST PASSED");
    }

    private static class SSF extends AbstractServerSocketFactory {

	boolean serverSocketClosed = false;

	SSF() { };

	public ServerSocket createServerSocket() throws IOException {
	    return new SS();
	}

	private class SS extends ServerSocket {

	    SS() throws IOException { super(); };

	    public Socket accept() throws IOException {
		throw new ThreadDeath();
	    }

	    public void close() throws IOException {
		synchronized (SSF.this) {
		    serverSocketClosed = true;
		    SSF.this.notifyAll();
		}
		super.close();
	    }
	}
    }

    private static ServerEndpoint getServerEndpoint(ServerSocketFactory ssf) {
	String endpointType = System.getProperty("endpointType", "tcp");
	System.err.println("Endpoint type: " + endpointType);
	if (endpointType.equals("tcp")) {
	    return TcpServerEndpoint.getInstance(null, 0, null, ssf);
	} else if (endpointType.equals("http")) {
	    return HttpServerEndpoint.getInstance(null, 0, null, ssf);
	} else if (endpointType.equals("ssl")) {
	    return SslServerEndpoint.getInstance(null, 0, null, ssf);
	} else if (endpointType.equals("https")) {
	    return HttpsServerEndpoint.getInstance(null, 0, null, ssf);
	} else {
	    throw new RuntimeException(
		"TEST FAILED: unsupported endpoint type: " + endpointType);
	}
    }
}
