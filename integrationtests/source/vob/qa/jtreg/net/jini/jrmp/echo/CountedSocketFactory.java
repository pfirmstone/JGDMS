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
 * @summary Custom socket factory for testing basic JrmpExporter functionality.
 */

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.ServerSocket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

public class CountedSocketFactory
    implements RMIClientSocketFactory, RMIServerSocketFactory, Serializable
{
    int clientSocketsCreated = 0;
    int serverSocketsCreated = 0;
    int serverSocketsAccepted = 0;

    public Socket createSocket(String host, int port) throws IOException {
	Socket s = new Socket(host, port);
	clientSocketsCreated++;
	return s;
    }

    public ServerSocket createServerSocket(int port) throws IOException {
	ServerSocket ss = new CountedServerSocket(port);
	serverSocketsCreated++;
	return ss;
    }
    
    class CountedServerSocket extends ServerSocket {
	
	CountedServerSocket(int port) throws IOException {
	    super(port);
	}
	
	public Socket accept() throws IOException {
	    Socket s = super.accept();
	    serverSocketsAccepted++;
	    return s;
	}
    }
}
