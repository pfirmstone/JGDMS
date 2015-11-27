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
 * TODO: The sun.net.spi.nameservice.NameService is
 * being removed from Java 9, we won't be able to test it this way.  
 * Consider if there's another way to implement this test.
 * @bug 6189036
 * @bug 6192775
 * @summary If the local host name cannot be resolved,
 * TcpServerEndpoint.enumerateListenEndpoints must not expose it in
 * the resulting UnknownHostException if the caller does not have
 * permission to resolve it.
 *
 * @build LocalHostExposure
 * @build TestNameServiceDescriptor
 * @build TestNameService
 * @run main/othervm/policy=security.policy -DendpointType=tcp
 *      -Dsun.net.spi.nameservice.provider.1=test,test LocalHostExposure
 * @run main/othervm/policy=security.policy -DendpointType=http
 *      -Dsun.net.spi.nameservice.provider.1=test,test LocalHostExposure
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
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

public class LocalHostExposure {

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6189036\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	/*
	 * Determine the local host name by checking what value
	 * InetAddress.getLocalHost asks our test name service to
	 * resolve.
	 */
	try {
	    InetAddress localAddr = InetAddress.getLocalHost();
	    throw new RuntimeException("TEST FAILED: " +
		"local host name unexpectedly resolvable: " + localAddr);
	} catch (UnknownHostException e) {
	}
	String localName = TestNameService.getLastNameLookup();
	if (localName == null) {
	    throw new RuntimeException("TEST FAILED: " +
		"local host name not looked up in name service");
	}
	System.err.println("Local host name is \"" + localName + "\"");

	final ServerEndpoint se = getServerEndpoint();
	final ListenContext lc = new ListenContext() {
	    public ListenCookie addListenEndpoint(ListenEndpoint le)
		throws IOException
	    {
		return le.listen(new RequestDispatcher() {
		    public void dispatch(InboundRequest r) { }
		}).getCookie();
	    }
	};

	/*
	 * Verify that invoking enumerateListenEndpoints with
	 * permission to resolve the local host name throws an
	 * UnknownHostException that does contain the local host name
	 * in its detail message.
	 */
	System.err.println("Trying with permission:");
	try {
	    se.enumerateListenEndpoints(lc);
	    throw new RuntimeException("TEST FAILED");
	} catch (UnknownHostException e) {
	    e.printStackTrace();
	    String message = e.getMessage();
	    if (message == null || message.indexOf(localName) == -1) {
		throw new RuntimeException("TEST FAILED: " +
		    "exception message does not contain local host name");
	    }
	}

	/*
	 * Verify that invoking enumerateListenEndpoints without
	 * permission to resolve the local host name throws an
	 * UnknownHostException that does not contain the local host
	 * name in its detail message.
	 */
	System.err.println("Trying without permission:");
	AccessControlContext acc = new AccessControlContext(
	    new ProtectionDomain[] { new ProtectionDomain(null, null) });
	try {
	    AccessController.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws IOException {
		    se.enumerateListenEndpoints(lc);
		    throw new RuntimeException("TEST FAILED");
		}
	    }, acc);
	} catch (PrivilegedActionException pae) {
	    IOException e = (IOException) pae.getCause();
	    e.printStackTrace();
	    if (!(e instanceof UnknownHostException)) {
		throw new RuntimeException(
		    "TEST FAILED: exception was not UnknownHostException");
	    }
	    String message = e.getMessage();
	    if (message != null && message.indexOf(localName) != -1) {
		throw new RuntimeException("TEST FAILED: " +
		    "exception message contains local host name");
	    }
	}

	System.err.println("TEST PASSED");
    }

    private static ServerEndpoint getServerEndpoint() {
	String endpointType = System.getProperty("endpointType", "tcp");
	System.err.println("Endpoint type: " + endpointType);
	if (endpointType.equals("tcp")) {
	    return TcpServerEndpoint.getInstance(0);
	} else if (endpointType.equals("http")) {
	    return HttpServerEndpoint.getInstance(0);
	} else if (endpointType.equals("ssl")) {
	    return SslServerEndpoint.getInstance(0);
	} else if (endpointType.equals("https")) {
	    return HttpsServerEndpoint.getInstance(0);
	} else {
	    throw new RuntimeException(
		"TEST FAILED: unsupported endpoint type: " + endpointType);
	}
    }
}
