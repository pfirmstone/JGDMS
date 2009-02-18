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
 * @bug 6307813
 * @summary Request initiation to a net.jini.jeri.connection-based
 * endpoint should not block waiting for the establishment of a
 * connection that the request wouldn't have been used for anyway
 * (like if the connection does not satisfy the new request's
 * constraints).
 *
 * @build Overlocking
 * @build AbstractSocketFactory
 * @run main/othervm Overlocking
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketPermission;
import java.security.Permission;
import javax.net.SocketFactory;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class Overlocking {

    private static final String BOGUS_HOST = "BOGUS_HOST";

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6307813\n");

	/*
	 * Set a custom security manager to prevent attempts to reuse
	 * the bogus socket produced by the socket factory.
	 */
	System.setSecurityManager(new SM());

	/*
	 * Listen on a server endpoint with a no-op dispatcher and a
	 * client-side socket factory whose first socket hangs on
	 * attempt to read.
	 */
	SF sf = new SF();
	final Endpoint ep =
	    TcpServerEndpoint.getInstance(null, 0, sf, null)
	    .enumerateListenEndpoints(new ListenContext() {
		public ListenCookie addListenEndpoint(ListenEndpoint lep)
		    throws IOException
		{
		    return lep.listen(new RequestDispatcher() {
			public void dispatch(InboundRequest req) { }
		    }).getCookie();
		}
	    });

	/*
	 * Initiate first request in a separate thread.  We expect it
	 * to hang waiting for connection handshake data; wait until
	 * we're sure that it is blocking on read.
	 */
	Thread t = new Thread(new Runnable() {
	    public void run() {
		try {
		    System.err.println(
			"Initiating first request asynchronously:");
		    ep.newRequest(InvocationConstraints.EMPTY).next();
		    System.err.println("First request initiated.");
		} catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	});
	t.setDaemon(true); // so that test VM exits upon completion
	t.start();
	System.err.println("Waiting for first request initiation to block:");
	synchronized (sf) {
	    while (!sf.blocked) {
		sf.wait();
	    }
	}
	System.err.println("First request initiation blocked.");

	/*
	 * Initiate another request on the same endpoint while the
	 * first request initiation is still blocking on the bogus
	 * socket; this request should use a normal socket and thus it
	 * should return quickly.  If not, then the test will hang
	 * (and time out, if run in the harness).
	 */
	System.err.println("Initiating second request:");
	ep.newRequest(InvocationConstraints.EMPTY).next();
	System.err.println("Second request initiated.");

	System.err.println("TEST PASSED");
    }

    /*
     * Custom security manager that allows all operations except
     * connecting to the host reported by the bogus first socket
     * produced by the socket factory.
     */
    private static class SM extends SecurityManager {
	SM() { }
	public void checkConnect(String host, int port) {
	    if (host.equals(BOGUS_HOST) && port != -1) {
		System.err.println(
		    "SM.checkConnect(" + host + ", " + port +
		    ") throwing SecurityException.");
		throw new SecurityException();
	    }
	}
    }

    /*
     * Socket factory that on first invocation produces a socket that
     * reports a bogus connected host name and hangs on attempt to
     * read, and on later invocations produces normal sockets.  The
     * "blocked" flag indicates when a read on the first socket has
     * been attempted, and the factory object's lock is notified when
     * this flag is set.
     */
    private static class SF extends AbstractSocketFactory {

	private volatile boolean first = true;

	boolean blocked = false; // guarded by this object's lock

	SF() { }

	public Socket createSocket() {
	    if (first) {
		first = false;
		return new S();
	    } else {
		return new Socket();
	    }
	}

	private class S extends Socket {

	    S() { super(); }

	    public SocketAddress getRemoteSocketAddress() {
		if (!isConnected()) {
		    return super.getRemoteSocketAddress();
		}
		return new InetSocketAddress(BOGUS_HOST, getPort());
	    }

	    public InputStream getInputStream() throws IOException {
		return new InputStream() {
		    public int read() throws IOException {
			System.err.println("Read on first socket: blocking.");
			synchronized (SF.this) {
			    blocked = true;
			    SF.this.notifyAll();
			}
			while (true) {
			    try {
				Thread.sleep(Long.MAX_VALUE);
			    } catch (InterruptedException e) {
				e.printStackTrace();
				IOException ioe = new InterruptedIOException();
				ioe.initCause(e);
				throw ioe;
			    }
			}
		    }
		};
	    }
	}
    }
}
