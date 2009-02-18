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
 * @bug 4838975
 * @summary The BasicJeriExporter implementation must not compare
 * ListenEndpoint instances of different classes.
 *
 * @build SameClassCheck
 * @run main/othervm SameClassCheck
 */

import java.io.IOException;
import java.rmi.Remote;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class SameClassCheck {

    private static final ServerEndpoint goodServerEndpoint =
	TcpServerEndpoint.getInstance(0);

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4838975\n");

	Exporter[] exporters = new Exporter[3];
	for (int i = 0; i < exporters.length; i++) {
	    exporters[i] =
		new BasicJeriExporter((i & 0x1) == 0 ?
				      new EvilServerEndpoint() :
				      goodServerEndpoint,
				      new BasicILFactory(), false, false);
	}
	for (int i = 0; i < exporters.length; i++) {
	    System.err.println("Exporting with " + exporters[i] + "...");
	    exporters[i].export(new Remote() { });
	}
	for (int i = 0; i < exporters.length; i++) {
	    exporters[i].unexport(true);
	}
	System.err.println("TEST PASSED");
    }

    private static class EvilServerEndpoint implements ServerEndpoint {

	EvilServerEndpoint() { }

	public InvocationConstraints checkConstraints(InvocationConstraints c)
	{
	    return InvocationConstraints.EMPTY;
	}

	public Endpoint enumerateListenEndpoints(ListenContext lc)
	    throws IOException
	{
	    ListenEndpoint le = new EvilListenEndpoint();
	    lc.addListenEndpoint(le);
	    return new Endpoint() {
		public OutboundRequestIterator newRequest(
		    InvocationConstraints c)
		{
		    throw new AssertionError();
		}
	    };
	}

	private static class EvilListenEndpoint implements ListenEndpoint {
	    public void checkPermissions() { }
	    public ListenHandle listen(RequestDispatcher rd) {
		return new ListenHandle() {
		    public void close() { }
		    public ListenCookie getCookie() {
			return new ListenCookie() { };
		    }
		};
	    }
	    public int hashCode() { return goodServerEndpoint.hashCode(); }
	    public boolean equals(Object obj) {
		if (obj.getClass() != this.getClass()) {
		    throw new RuntimeException("TEST FAILED: " +
			"ListenEndpoint.equals invoked with object of " +
			"different class: " + obj);
		}
		return true;
	    }
	}
    }
}
