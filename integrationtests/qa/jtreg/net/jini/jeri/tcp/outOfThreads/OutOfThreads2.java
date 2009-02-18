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
 * @bug 6313626
 * @summary This test verifies robustness (to a certain level) of the
 * stream-mode mux implementation when the VM cannot create any more
 * threads.  See comments for details.
 *
 * @build OutOfThreads2
 * @build AbstractSocketFactory
 * @build AbstractServerSocketFactory
 * @build ThreadHog
 * @build NewThreadExecutor
 * @run main/othervm OutOfThreads2
 */

import com.sun.jini.thread.Executor;
import com.sun.jini.thread.GetThreadPoolAction;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.ServerSocket;
import java.rmi.Remote;
import java.rmi.RemoteException;
import javax.net.ServerSocketFactory;
import net.jini.export.Exporter;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class OutOfThreads2 {

    public interface Ping extends Remote {
	void ping() throws RemoteException;
    }

    public static class PingImpl implements Ping {
	PingImpl() { }
	public void ping() { System.err.println("ping() invoked"); }
    }

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6313626\n");

	/*
	 * Reserve a few unpooled threads to release before VM
	 * shutdown, so that shutdown hooks can be started.
	 */
	ThreadHog unpooledHog = new ThreadHog(new NewThreadExecutor());
	unpooledHog.hogThreads(10);

	try {
	    test();
	} finally {
	    unpooledHog.releaseAll();
	    Thread.sleep(1000);
	}
    }

    private static void test() throws Exception {
	Ping impl = new PingImpl();
	class SF extends AbstractSocketFactory implements Serializable {
	    SF() { }
	    public Socket createSocket() throws IOException {
		return new Socket();
	    }
	};
	SF sf = new SF();
	ServerSocketFactory ssf = new AbstractServerSocketFactory() {
	    public ServerSocket createServerSocket() throws IOException {
		return new ServerSocket();
	    }
	};
	ServerEndpoint se =
	    TcpServerEndpoint.getInstance("localhost", 0, sf, ssf);
	InvocationLayerFactory ilf =
	    new BasicILFactory(null, null, PingImpl.class.getClassLoader());
	Exporter exporter = new BasicJeriExporter(se, ilf, false, false);
	Ping proxy = (Ping) exporter.export(impl);
	/*
	 * Create another proxy that will use a different connection
	 * than the first (SocketFactory does not override equals).
	 */
	Ping proxy2 = (Ping) (new MarshalledInstance(proxy)).get(false);

	Executor systemThreadPool =
	    (Executor) (new GetThreadPoolAction(false)).run();
	Executor userThreadPool =
	    (Executor) (new GetThreadPoolAction(true)).run();

	/*
	 * Reserve a few workers in the system thread pool.
	 */
	ThreadHog systemHog = new ThreadHog(systemThreadPool);
	systemHog.hogThreads(10);

	/*
	 * Invoke once to cause a connection to be established.
	 */
	System.err.println("[invocation #1:]");
	proxy.ping();

	/*
	 * Consume all threads with workers in the user thread pool.
	 */
	ThreadHog userHog = new ThreadHog(userThreadPool);
	userHog.hogAllThreads();

	/*
	 * Invocation should fail because the user thread pool has no
	 * idle threads and no new threads can be created.
	 */
	System.err.println("[invocation #2:]");
	try {
	    proxy.ping();
	    throw new Error("unexpected success");
	} catch (Throwable t) {
	    t.printStackTrace();
	}

	/*
	 * Release workers in the user thread pool: now tasks can be
	 * executed in it, but its idle workers will still prevent
	 * other threads from being created (like for the system
	 * thread pool).
	 */
	userHog.releaseAll();
	Thread.sleep(1000);

	/*
	 * Verify that invocations can succeed again without having to
	 * create a new connection, which would require several new
	 * workers in the system thread pool.
	 */
	systemHog.hogAllThreads();
	System.err.println("[invocation #3:]");
	proxy.ping();

	/*
	 * In preparation for a new connection, release one worker in
	 * the system thread pool so that the ConnectionManager can
	 * create a repear thread.
	 */
	systemHog.release(1);

	/*
	 * Invocation should fail because the system thread pool does
	 * not have two idle threads for a new connection's I/O (and
	 * no new threads can be created).
	 */
	System.err.println("[invocation #4:]");
	try {
	    proxy2.ping();
	    throw new Error("unexpected success");
	} catch (Throwable t) {
	    t.printStackTrace();
	}

	/*
	 * Release workers in the system thread pool.
	 */
	systemHog.releaseAll();
	Thread.sleep(1000);

	/*
	 * Verify that invocations can succeed again.
	 */
	System.err.println("[invocation #5:]");
	proxy2.ping();

	System.err.println("TEST PASSED");
    }
}
