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
 * @bug 6304782
 * @summary This test verifies robustness (to a certain level) of the
 * NIO-mode mux implementation when the VM cannot create any more
 * threads.  See comments for details.
 *
 * @build OutOfThreads
 * @build AbstractSocketFactory
 * @build AbstractServerSocketFactory
 * @build ThreadHog
 * @build NewThreadExecutor
 * @run main/othervm OutOfThreads
 */

import org.apache.river.thread.Executor;
import org.apache.river.thread.GetThreadPoolAction;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.Remote;
import java.rmi.RemoteException;
import javax.net.ServerSocketFactory;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class OutOfThreads {

    private static final boolean useNIO = true;

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
	class SF extends AbstractSocketFactory {
	    volatile Socket lastSocket;
	    SF() { }
	    public Socket createSocket() throws IOException {
		Socket s = useNIO ? SocketChannel.open().socket()
				  : new Socket();
		lastSocket = s;
		return s;
	    }
	};
	SF sf = new SF();
	ServerSocketFactory ssf = new AbstractServerSocketFactory() {
	    public ServerSocket createServerSocket() throws IOException {
		return useNIO ? ServerSocketChannel.open().socket()
			      : new ServerSocket();
	    }
	};
	ServerEndpoint se =
	    TcpServerEndpoint.getInstance(null, 0, sf, ssf);
	InvocationLayerFactory ilf =
	    new BasicILFactory(null, null, PingImpl.class.getClassLoader());
	Exporter exporter = new BasicJeriExporter(se, ilf, false, false);
	Ping proxy = (Ping) exporter.export(impl);
	/*
	 * Create this object early to avoid possible FuturePing
	 * verification failure when new threads cannot be created.
	 */
	FuturePing future = new FuturePing(proxy);

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
	 * other threads from being created (like for other pools).
	 */
	userHog.releaseAll();
	Thread.sleep(1000);

	/*
	 * Verify that invocations can succeed again.
	 */
	System.err.println("[invocation #3:]");
	proxy.ping();

	/*
	 * Corrupt connection protocol so that on the next request
	 * attempt, the server side will detect a protocol violation
	 * and shut down the connection.
	 */
	if (useNIO) {
	    sf.lastSocket.getChannel().write(
		(ByteBuffer) ((Buffer) ByteBuffer.allocate(1).put((byte) 0x00)).flip());
	} else {
	    sf.lastSocket.getOutputStream().write(0x00);
	}

	/*
	 * Attempt to invoke again asynchronously.  This invocation
	 * may block for now, because when the connection is shut down
	 * due to the protocol violation, the task to shut down the
	 * associated sessions asynchronously cannot be executed
	 * because the system thread pool has no idle threads and no
	 * new threads can be created).
	 */
	System.err.println("[invocation #4:]");
	userThreadPool.execute(future, "ping");
	Thread.sleep(1000);

	/*
	 * Release workers in the system thread pool.
	 */
	systemHog.releaseAll();
	Thread.sleep(1000);

	/*
	 * Invoke again to cause a new connection to be established.
	 */
	System.err.println("[invocation #5:]");
	proxy.ping();

	/*
	 * When the new connection's idle timeout expires, its
	 * shutdown procedure should execute the lingering tasks to
	 * shut down the sessions associated with the first
	 * connection, allowing the asyncronous invocation to complete
	 * (with a RemoteException).
	 */
	try {
	    future.waitFor();
	    throw new Error("unexpected success");
	} catch (RemoteException e) {
	    e.printStackTrace();
	}

	System.err.println("TEST PASSED");
    }

    private static class FuturePing implements Runnable {
	private final Ping proxy;
	private final Object lock = new Object();
	private boolean completed = false;
	private RemoteException failure = null;

	FuturePing(Ping proxy) {
	    this.proxy = proxy;
	}

	void waitFor() throws InterruptedException, RemoteException {
	    synchronized (lock) {
		while (!completed) {
		    lock.wait();
		}
		if (failure != null) {
		    throw failure;
		}
	    }
	}

	public void run() {
	    RemoteException f = null;
	    try {
		proxy.ping();
	    } catch (RemoteException e) {
		f = e;
	    }
	    synchronized (lock) {
		completed = true;
		failure = f;
		lock.notifyAll();
	    }
	}
    }
}
