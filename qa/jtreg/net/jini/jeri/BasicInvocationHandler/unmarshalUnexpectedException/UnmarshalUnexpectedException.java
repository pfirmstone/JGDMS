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
 * @summary Verify that an unexpected exception (except RuntimeException or
 * Error) unmarshalled by BasicInvocationHandler.unmarshalThrow method
 * are wrapped in a java.rmi.UnexpectedException.
 * @author Ann Wollrath
 *
 * @build UnmarshalUnexpectedException
 * @run main/othervm UnmarshalUnexpectedException
 */

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.ServerError;
import java.rmi.UnexpectedException;
import java.rmi.server.ExportException;
import java.util.Collection;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

interface RemoteInterface extends Remote {

    void throwIt(Throwable t) throws RemoteException;
}
    
public class UnmarshalUnexpectedException implements RemoteInterface {

    private static class TestILFactory extends BasicILFactory {

	protected InvocationDispatcher
	    createInvocationDispatcher(Collection methods, Remote impl)
    	    throws ExportException
	{
	    return new TestInvocationDispatcher(methods);
	}

	private static class TestInvocationDispatcher
	    extends BasicInvocationDispatcher
	{
	    public TestInvocationDispatcher(Collection methods)
		throws ExportException
	    {
		super(methods, null, null, null, null);
	    }

	    protected Object invoke(Remote impl, Method method, Object[] args)
		throws Throwable
	    {
		throw (Throwable) args[0];
	    }
	}
    }

    public void throwIt(Throwable t) throws RemoteException {
	// this method is actually never called
    }

    public static void main(String[] args) throws Exception {
	
	Remote impl = new UnmarshalUnexpectedException();
	Exporter exporter =
	    new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				  new TestILFactory(), false, true);
	RemoteInterface proxy = null;
	
	try {
	    proxy = (RemoteInterface) exporter.export(impl);
	    try {
		proxy.throwIt(new RuntimeException());
	    } catch (RuntimeException e) {
		System.err.println("caught RuntimeException");
	    }
	    try {
		proxy.throwIt(new Error());
	    } catch (ServerError e) {
		System.err.println("caught ServerError");
	    }
	    try {
		proxy.throwIt(new RemoteException());
	    } catch (RemoteException e) {
		System.err.println("caught RemoteException");
	    }
	    try {
		proxy.throwIt(new IOException());
	    } catch (UnexpectedException e) {
		System.err.println("caught UnexpectedException");
		if (e.getCause().getClass() != IOException.class) {
		    throw new RuntimeException(
			"test failed; expected nested IOException, got " +
			e.getClass().toString());
		}
		System.err.println(
		    "UnexpectedException has correct nested exception");
	    }
	    System.err.println("test passed");
	} finally {
	    if (proxy != null) {
		exporter.unexport(true);
	    }
	}
    }
}


	   
		
	    
	
