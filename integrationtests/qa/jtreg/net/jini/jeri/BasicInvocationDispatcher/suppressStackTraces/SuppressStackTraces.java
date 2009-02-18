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
 * @bug 4487532
 * @summary When the system property
 *	com.sun.jini.jeri.server.suppressStackTraces is set
 * true, then BasicInvocationDispatcher should take positive action
 * to counteract the new feature in 1.4 of an exception's stack trace being
 * part of its serialized form so that the server-side stack trace of an
 * exception that occurs during the execution of a remote method invocation
 * gets marshalled to the client.  In most cases, this feature-- along with the
 * final fix to 4010355 to make the server-side stack trace data available
 * at the RMI client application level-- is highly desirable, but this system
 * property provides an opportunity to suppress the server-side stack trace
 * data of exceptions thrown by remote methods from being marshalled, perhaps
 * for reasons of performance or confidentiality requirements.
 * @author Peter Jones
 *
 * @build SuppressStackTraces
 * @run main/othervm SuppressStackTraces
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Arrays;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

interface Pong extends Remote {
    void pong() throws PongException, RemoteException;
}

class PongException extends Exception {
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	/*
	 * Verify right at unmarshalling time that this exception instance
	 * contains no stack trace data from the server (regardless of whether
	 * or not it would be apparent at the RMI client application level).
	 */
	StackTraceElement[] trace = getStackTrace();
	if (trace.length > 0) {
	    throw new RuntimeException(
		"TEST FAILED: exception contained non-empty stack trace: " +
		Arrays.asList(trace));
	}
    }
}

class Impl implements Pong {
    public void pong() throws PongException { __BAR__(); }
    public void __BAR__() throws PongException { throw new PongException(); }
}

public class SuppressStackTraces {

    private static void __FOO__(Pong stub)
	throws PongException, RemoteException
    {
	stub.pong();
    }

    public static void main(String[] args) throws Exception {

	System.err.println("\nRegression test for RFE 4487532\n");

	System.setProperty(
	    "com.sun.jini.jeri.server.suppressStackTraces", "true");

	Exporter exporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new
				  BasicILFactory(), true, true);

	try {
	    verifySuppression((Pong) exporter.export(new Impl()));

	    System.err.println(
		"TEST PASSED (server-side stack trace data suppressed)");
	} finally {
	    try {
		exporter.unexport(true);
	    } catch (Exception e) {
	    }
	}
    }

    private static void verifySuppression(Pong stub) throws Exception {
	System.err.println("testing stub for exported object: " + stub);

	StackTraceElement[] remoteTrace;
	try {
	    __FOO__(stub);
	    throw new RuntimeException("TEST FAILED: no exception caught");
	} catch (PongException e) {
	    System.err.println(
		"trace of exception thrown by remote call:");
	    e.printStackTrace();
	    System.err.println();
	    remoteTrace = e.getStackTrace();
	}

	int fooIndex = -1;
	for (int i = 0; i < remoteTrace.length; i++) {
	    StackTraceElement e = remoteTrace[i];
	    if (e.getMethodName().equals("__FOO__")) {
		if (fooIndex != -1) {
		    throw new RuntimeException(
			"TEST FAILED: trace contains more than one __FOO__");
		}
		fooIndex = i;
	    } else if (e.getMethodName().equals("__BAR__")) {
		throw new RuntimeException(
		    "TEST FAILED: trace contains __BAR__");
	    }
	}
	if (fooIndex == -1) {
	    throw new RuntimeException(
		"TEST FAILED: trace lacks client-side method __FOO__");
	}
    }
}
