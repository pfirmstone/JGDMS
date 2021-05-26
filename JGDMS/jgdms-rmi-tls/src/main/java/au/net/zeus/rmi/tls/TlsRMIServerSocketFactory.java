/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.rmi.tls;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.security.auth.Subject;

/**
 * 
 */
public final class TlsRMIServerSocketFactory implements RMIServerSocketFactory {

    public ServerSocket createServerSocket(int port) throws IOException {
	final AccessControlContext acc = AccessController.getContext();
	Subject subject = AccessController.doPrivileged(
	    new PrivilegedAction<Subject>() {
		@Override
		public Subject run() {
		    return Subject.getSubject(acc);
		}
	    }
	);
	SSLContext sslContext = Utilities.getServerSSLContextInfo(subject);
	SSLServerSocket socket;
	socket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port);
	socket.setUseClientMode(false);
	socket.setNeedClientAuth(true);
	return socket;
    }
    
}
