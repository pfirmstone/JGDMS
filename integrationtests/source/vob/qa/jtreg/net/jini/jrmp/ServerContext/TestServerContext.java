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
 * @summary Verify that using ServerContext.getServerContextElement to obtain
 *	    the ClientHost functions properly for objects exported over JRMP.
 * @build TestServerContext TestServerContext_Stub
 * @run main/othervm TestServerContext
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.ServerNotActiveException;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;
import net.jini.jrmp.JrmpExporter;

interface Foo extends Remote {
    public String getClientHost() 
	throws RemoteException, ServerNotActiveException;
}

public class TestServerContext implements Foo {

    public static void main(String[] args) throws Exception {
	TestServerContext impl = new TestServerContext();
	try {
	    impl.getClientHost();
	    throw new Error();
	} catch (ServerNotActiveException ex) {
	}

	JrmpExporter exporter = new JrmpExporter();
	Foo stub = (Foo) exporter.export(impl);
	String clientHost = stub.getClientHost();
	System.out.println("Returned client host: " + clientHost);
	if (clientHost == null) {
	    throw new Error("clientHost is null");
	}
	exporter.unexport(true);
    }
    
    public String getClientHost() 
	throws RemoteException, ServerNotActiveException 
    {
	ClientHost ch = (ClientHost)
	    ServerContext.getServerContextElement(ClientHost.class);
	if (ch != null) {
	    return ch.getClientHost().toString();
	} else {
	    throw new Error("no ClientHost instance in context");
	}
    }
}
