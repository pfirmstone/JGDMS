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
 * @summary Test the TCP provider's computation of unfulfilled constraints,
 * i.e. constraints of a given set that need to be at least partially
 * implemented by higher layers in order to fully satisfy the set.
 *
 * @build TestUnfulfilledConstraints
 * @run main/othervm TestUnfulfilledConstraints
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.jeri.Endpoint;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ServerEndpoint.ListenContext;
import net.jini.jeri.ServerEndpoint.ListenCookie;
import net.jini.jeri.ServerEndpoint.ListenEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class TestUnfulfilledConstraints {

    private static final Map testCases = new LinkedHashMap();
    static {
	testCases.put(
	    InvocationConstraints.EMPTY,
	    InvocationConstraints.EMPTY
	);

	testCases.put(
	    new InvocationConstraints(Integrity.NO, null),
	    new InvocationConstraints(Integrity.NO, null)
	);

	testCases.put(
	    new InvocationConstraints(null, Integrity.NO),
	    new InvocationConstraints(null, Integrity.NO)
	);

	testCases.put(
	    new InvocationConstraints(
		new InvocationConstraint[] {
		    ClientAuthentication.NO,
		    Integrity.NO
		}, null),
	    new InvocationConstraints(Integrity.NO, null)
	);

	testCases.put(
	    new InvocationConstraints(
		null,
		new InvocationConstraint[] {
		    ClientAuthentication.NO,
		    Integrity.NO
		}),
	    new InvocationConstraints(null, Integrity.NO)
	);

	testCases.put(
	    new InvocationConstraints(
		ConstraintAlternatives.create(
		    new InvocationConstraint[] {
			ClientAuthentication.NO,
			Integrity.NO
		    }
		), null),
	    InvocationConstraints.EMPTY
	);

	testCases.put(
	    new InvocationConstraints(
		null,
		ConstraintAlternatives.create(
		    new InvocationConstraint[] {
			ClientAuthentication.NO,
			Integrity.NO
		    }
		)),
	    InvocationConstraints.EMPTY
	);

	/*
	 * This provider does not (yet) support more than one
	 * constraint that needs higher layer implementation, so we
	 * cannot test the provider's ability to return alternatives
	 * in unfulfilled constraints.
	 */
    }

    private static final Object lock = new Object();
    private static boolean testedInboundRequest = false;
    private static RuntimeException inboundRequestFailure = null;

    public static void main(String[] args) throws Exception {

	ServerEndpoint serverEndpoint = TcpServerEndpoint.getInstance(0);
	System.err.println("obtained server endpoint: " + serverEndpoint);

	String method = "ServerCapabilities.checkConstraints";
	System.err.println("\nTesting " + method + ":");
	for (Iterator i = testCases.entrySet().iterator(); i.hasNext();) {
	    Map.Entry entry = (Map.Entry) i.next();
	    InvocationConstraints c = (InvocationConstraints) entry.getKey();
	    System.err.println("  trying: " + c);
	    InvocationConstraints u = serverEndpoint.checkConstraints(c);
	    System.err.println("returned: " + u);
	    if (!entry.getValue().equals(u)) {
		throw new RuntimeException(
		    "TEST FAILED: incorrect constraints returned by " +
		    method);
	    }
	}

	Endpoint endpoint =
	    serverEndpoint.enumerateListenEndpoints(new ListenContext() {
		public ListenCookie addListenEndpoint(ListenEndpoint le)
		    throws IOException
		{
		    return le.listen(new RD()).getCookie();
		}
	    });

	method = "OutboundRequest.getUnfulfilledConstraints";
	System.err.println("\nTesting " + method + ":");
	for (Iterator i = testCases.entrySet().iterator(); i.hasNext();) {
	    Map.Entry entry = (Map.Entry) i.next();
	    InvocationConstraints c = (InvocationConstraints) entry.getKey();
	    System.err.println("  trying: " + c);
	    OutboundRequestIterator iter = endpoint.newRequest(c);
	    OutboundRequest request = iter.next();
	    InvocationConstraints u = request.getUnfulfilledConstraints();
	    System.err.println("returned: " + u);
	    if (!entry.getValue().equals(u)) {
		throw new RuntimeException(
		    "TEST FAILED: incorrect constraints returned by " +
		    method);
	    }
	    request.getRequestOutputStream().close();
	    InputStream in = request.getResponseInputStream();
	    in.read();
	    in.close();
	}

	synchronized (lock) {
	    if (!testedInboundRequest) {
		throw new RuntimeException(
		    "TEST FAILED: request never dispatched?");
	    }
	    if (inboundRequestFailure != null) {
		throw inboundRequestFailure;
	    }
	}

	System.err.println("TEST PASSED");
    }

    private static class RD implements RequestDispatcher {

	public void dispatch(InboundRequest request) {
	    try {
		dispatch0(request);
	    } finally {
		try {
		    request.getRequestInputStream().close();
		    OutputStream out = request.getResponseOutputStream();
		    out.write(0);
		    out.close();
		} catch (IOException e) {
		}
	    }
	}

	private void dispatch0(InboundRequest request) {
	    synchronized (lock) {
		if (testedInboundRequest) {
		    return;
		}
		testedInboundRequest = true;
	    }
	    String method = "InboundRequest.checkConstraints";
	    System.err.println("\n| Testing " + method + ":");
	    for (Iterator i = testCases.entrySet().iterator(); i.hasNext();) {
		Map.Entry entry = (Map.Entry) i.next();
		InvocationConstraints c =
		    (InvocationConstraints) entry.getKey();
		System.err.println("|   trying: " + c);
		InvocationConstraints u;
		try {
		    u = request.checkConstraints(c);
		} catch (UnsupportedConstraintException e) {
		    synchronized (lock) {
			inboundRequestFailure = new RuntimeException(
			    "TEST FAILED: " + method +
			    " threw an UnsupportedConstraintException", e);
		    }
		    return;
		}
		System.err.println("| returned: " + u);
		if (!entry.getValue().equals(u)) {
		    synchronized (lock) {
			inboundRequestFailure = new RuntimeException(
			    "TEST FAILED: incorrect constraints returned by " +
			    method);
		    }
		}
	    }
	}
    }
}
