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
 * @bug 4843268
 * @summary The BasicJeriExporter implementation must not hold a
 * global lock while attempting to listen on a ListenEndpoint.
 *
 * @build GlobalListenLock
 * @run main/othervm GlobalListenLock
 */

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.Endpoint;
import net.jini.jeri.RequestDispatcher;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class GlobalListenLock {

    private static Object lock = new Object();
    private static boolean blocking = false;

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4843268\n");

	Thread t = new Thread(new Runnable() {
	    public void run() {
		Exporter exporter =
		    new BasicJeriExporter(new BlockingListenServerEndpoint(),
					  new BasicILFactory(), false, false);
		try {
		    exporter.export(new Remote() { });
		} catch (ExportException e) {
		    throw new Error(e);
		}
	    }
	});
	t.setDaemon(true);
	t.start();
	synchronized (lock) {
	    while (!blocking) {
		lock.wait();
	    }
	}
	Exporter exporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new BasicILFactory(), false, false);
	exporter.export(new Remote() { });
	exporter.unexport(true);
	System.err.println("TEST PASSED");
    }

    private static class BlockingListenServerEndpoint
	implements ServerEndpoint
    {
	BlockingListenServerEndpoint() { }

	public InvocationConstraints checkConstraints(InvocationConstraints c)
	{
	    return InvocationConstraints.EMPTY;
	}

	public Endpoint enumerateListenEndpoints(ListenContext lc)
	    throws IOException
	{
	    lc.addListenEndpoint(new ListenEndpoint() {
		public void checkPermissions() { }
		public ListenHandle listen(RequestDispatcher rd) {
		    synchronized (lock) {
			blocking = true;
			lock.notifyAll();
		    }
		    while (true) {
			try {
			    Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
			}
		    }
		}
	    });
	    return null;
	}
    }
}
