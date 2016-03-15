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
 * @bug 4010355
 * @summary When an exception is thrown by a remote method invocation, the
 * stack trace of the exception catchable by the client application should
 * comprise both the client-side trace as well as the server-side trace, as
 * serialized with the Throwable from the server.
 * @author Peter Jones
 *
 * @build ServerStackTrace
 * @run main/othervm ServerStackTrace
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import org.apache.river.api.io.AtomicSerial;

interface Ping extends Remote {
    void ping() throws PingException, RemoteException;
}


public class ServerStackTrace implements Ping {
    
    public void ping() throws PingException {
	__BAR__();
    }

    private void __BAR__() throws PingException {
	throw new PingException();
    }

    private static void __FOO__(Ping stub)
	throws PingException, RemoteException
    {
	stub.ping();
    }

    public static void main(String[] args) throws Exception {

	System.err.println("\nRegression test for RFE 4010355\n");

	Exporter exporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new BasicILFactory(), true, true);
	
	try {
	    Ping stub = (Ping) exporter.export(new ServerStackTrace());

	    StackTraceElement[] remoteTrace;
	    try {
		__FOO__(stub);
		throw new RuntimeException("TEST FAILED: no exception caught");
	    } catch (PingException e) {
		System.err.println(
		    "trace of exception thrown by remote call:");
		e.printStackTrace();
		System.err.println();
		remoteTrace = e.getStackTrace();
	    }

	    int fooIndex = -1;
	    int barIndex = -1;
	    for (int i = 0; i < remoteTrace.length; i++) {
		StackTraceElement e = remoteTrace[i];
		if (e.getMethodName().equals("__FOO__")) {
		    if (fooIndex != -1) {
			throw new RuntimeException("TEST FAILED: " +
			    "trace contains more than one __FOO__");
		    }
		    fooIndex = i;
		} else if (e.getMethodName().equals("__BAR__")) {
		    if (barIndex != -1) {
			throw new RuntimeException("TEST FAILED: " +
			    "trace contains more than one __BAR__");
		    }
		    barIndex = i;
		}
	    }
	    if (fooIndex == -1) {
		throw new RuntimeException(
		   "TEST FAILED: trace lacks client-side method __FOO__");
	    }
	    if (barIndex == -1) {
		throw new RuntimeException(
		   "TEST FAILED: trace lacks server-side method __BAR__");
	    }
	    if (fooIndex < barIndex) {
		throw new RuntimeException(
		   "TEST FAILED: trace contains client-side method __FOO__ " +
		   "before server-side method __BAR__");
	    }
	    System.err.println("TEST PASSED");
	} finally {
	    try {
		exporter.unexport(true);
	    } catch (Exception e) {
	    }
	}
    }
}
