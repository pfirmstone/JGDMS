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
/* 
 * @summary Custom server socket factory for testing HTTP endpoint
 * functionality.
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import javax.net.ServerSocketFactory;

public class CountedServerSocketFactory extends ServerSocketFactory {

    int serverSocketsCreated = 0;
    int serverSocketsAccepted = 0;

    public ServerSocket createServerSocket() throws IOException {
	ServerSocket ss = new CountedServerSocket();
	serverSocketsCreated++;
	return ss;
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
					   InetAddress bindAddr)
	throws IOException
    {
	ServerSocket ss = createServerSocket();
	ss.bind(new InetSocketAddress(bindAddr, port));
	return ss;
    }

    class CountedServerSocket extends ServerSocket {
	
	CountedServerSocket() throws IOException {
	    super();
	}
	
	public Socket accept() throws IOException {
	    Socket s = super.accept();
	    serverSocketsAccepted++;
	    return s;
	}
    }
}
