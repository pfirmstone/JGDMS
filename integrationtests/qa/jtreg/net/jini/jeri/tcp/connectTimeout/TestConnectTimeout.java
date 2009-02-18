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
 * @summary Test the TCP provider's support for socket connect timeouts
 * controlled by ConnectionAbsoluteTime and ConnectionRelativeTime
 * constraints.
 *
 * @build TestConnectTimeout
 * @build AbstractSocketFactory
 * @build Ping
 * @run main/othervm TestConnectTimeout
 */

import net.jini.id.UuidFactory;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.Endpoint;
import net.jini.jeri.ObjectEndpoint;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.jeri.tcp.TcpEndpoint;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.rmi.ConnectIOException;

public class TestConnectTimeout {

    private static final long TIMEOUT = 1000; // one second

    private static final String HOST = "192.168.79.79";
    private static final int PORT = 7979;

    public static void main(String[] args) throws Exception {
	/*
	 * Use bogus host address, bogus port, and sockets that when
	 * asked to connect with a non-zero timeout (as we expect the
	 * endpoint to do) always fail as if the timeout has expired,
	 * just in case the attempt to connect to the bogus host
	 * address would fail for some other reason instead (6391265).
	 */
	Endpoint endpoint =
	    TcpEndpoint.getInstance(HOST, PORT, new AbstractSocketFactory() {
		public Socket createSocket() throws IOException {
		    return new Socket() {
			public void connect(SocketAddress endpoint,
					    int timeout)
			    throws IOException
			{
			    if (timeout > 0) {
				try {
				    Thread.sleep(timeout);
				} catch (InterruptedException e) {
				}
				throw new SocketTimeoutException("fake");
			    }
			    super.connect(endpoint, timeout);
			}
		    };
		}
	    });
	ObjectEndpoint objectEndpoint =
	    new BasicObjectEndpoint(
		endpoint,
		UuidFactory.create("57117a56-2750-11b2-b312-080020c9e4a1"),
		false);
	InvocationHandler invocationHandler =
	    new BasicInvocationHandler(objectEndpoint, null);
	RemoteMethodControl proxy =
	    (RemoteMethodControl) Proxy.newProxyInstance(
		 TestConnectTimeout.class.getClassLoader(),
		 new Class[] { Ping.class, RemoteMethodControl.class },
		 invocationHandler);

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		new ConnectionRelativeTime(TIMEOUT),
		null
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		ConstraintAlternatives.create(new InvocationConstraint[] {
		    new ConnectionRelativeTime(TIMEOUT),
		    new ConnectionRelativeTime(TIMEOUT + 1)
		}),
		null
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		null,
		new ConnectionRelativeTime(TIMEOUT)))));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		null,
		ConstraintAlternatives.create(new InvocationConstraint[] {
		    new ConnectionRelativeTime(TIMEOUT),
		    new ConnectionRelativeTime(TIMEOUT + 1)
		})
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		new ConnectionAbsoluteTime(System.currentTimeMillis()),
		null
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		ConstraintAlternatives.create(new InvocationConstraint[] {
		    new ConnectionAbsoluteTime(System.currentTimeMillis()),
		    new ConnectionAbsoluteTime(System.currentTimeMillis() + 1)
		}),
		null
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		null,
		new ConnectionAbsoluteTime(System.currentTimeMillis())
	    ))
	));

	tryProxy(proxy.setConstraints(
	    new BasicMethodConstraints(new InvocationConstraints(
		null,
		ConstraintAlternatives.create(new InvocationConstraint[] {
		    new ConnectionAbsoluteTime(System.currentTimeMillis()),
		    new ConnectionAbsoluteTime(System.currentTimeMillis() + 1)
		})
	    ))
	));

	System.err.println("TEST PASSED");
    }

    private static void tryProxy(RemoteMethodControl proxy) {
	try {
	    System.err.println("\nTrying remote call with constraints: " +
			       proxy.getConstraints());
	    ((Ping) proxy).ping();
	    throw new RuntimeException(
		"TEST FAILED: unexpected success of remote call");
	} catch (RemoteException e) {
	    if (e instanceof ConnectIOException &&
		e.getCause() instanceof SocketTimeoutException)
	    {
		System.err.println("Remote call failed correctly: " + e);
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: unexpected failure of remote call", e);
	    }
	}
    }
}
