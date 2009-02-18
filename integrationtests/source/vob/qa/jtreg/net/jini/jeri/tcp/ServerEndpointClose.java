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
 * @summary When a TcpServerEndpont's ServerEndpointListener is closed (such
 * as by unexporting all JERI-exported remote objects on that endpoint) after
 * a connection has been accepted from the underlying ServerSocket but before
 * the connection has been added to the internal set of connections, then the
 * new connection should still be closed in a timely fashion, rather than be
 * allowed to stay open.
 *
 * @build ServerEndpointClose
 * @run main/othervm ServerEndpointClose
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Remote;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.RemoteException;
import javax.net.ServerSocketFactory;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

public class ServerEndpointClose {

    public interface Ping extends Remote {
	void ping() throws RemoteException;
    }

    public static class Impl implements Ping {
	public void ping() { }
    }

    public static void main(String[] args) throws Exception {
	Impl impl = new Impl();
	Exporter exporter = new BasicJeriExporter(
	    TcpServerEndpoint.getInstance(null, 0, null,
					  new DelayingServerSocketFactory()),
	    new BasicILFactory());
	Ping stub = (Ping) exporter.export(impl);

	new Thread(new Unexporter(exporter)).start();

	try {
	    stub.ping();
	    throw new RuntimeException(
		"TEST FAILED: call after unexport succeeded");
	} catch (RemoteException e) {
	    if (e instanceof ConnectException ||
		e instanceof ConnectIOException)
	    {
		System.err.println(
		    "TEST PASSED: call after unexport failed with exception:");
		e.printStackTrace();
	    } else {
		throw new RuntimeException(
		    "TEST FAILED: call after unexport threw wrong exception",
		    e);
	    }
	}
    }

    private static class Unexporter implements Runnable {
	private final Exporter exporter;
	Unexporter(Exporter exporter) { this.exporter = exporter; }
	public void run() {
	    try { Thread.sleep(5000); } catch (InterruptedException e) { }
	    exporter.unexport(true);
	    System.err.println("Unexported remote object.");
	}
    }

    private static class DelayingServerSocketFactory
	extends ServerSocketFactory
    {
	public ServerSocket createServerSocket() throws IOException {
	    return new DelayingServerSocket();
	}

	public ServerSocket createServerSocket(int port) throws IOException {
	    ServerSocket ss = createServerSocket();
	    ss.bind(new InetSocketAddress(port));
	    return ss;
	}

	public ServerSocket createServerSocket(int port, int backlog)
	    throws IOException
	{
	    ServerSocket ss = createServerSocket();
	    ss.bind(new InetSocketAddress(port), backlog);
	    return ss;
	}

	public ServerSocket createServerSocket(int port,
					       int backlog,
					       InetAddress ifAddress)
	    throws IOException
	{
	    ServerSocket ss = createServerSocket();
	    ss.bind(new InetSocketAddress(ifAddress, port));
	    return ss;
	}

	private static class DelayingServerSocket extends ServerSocket {
	    DelayingServerSocket() throws IOException {	super(); }
	    public Socket accept() throws IOException {
		Socket s = super.accept();
		System.err.println("Accepted socket " + s + ", waiting...");
		try { Thread.sleep(10000); } catch (InterruptedException e) { }
		System.err.println("...done waiting, returning from accept.");
		return s;
	    }
	}
    }
}
