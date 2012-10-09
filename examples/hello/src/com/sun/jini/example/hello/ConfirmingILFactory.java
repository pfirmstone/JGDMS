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

package com.sun.jini.example.hello;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.rmi.server.ServerNotActiveException;
import java.util.Collection;
import javax.swing.JOptionPane;
import net.jini.core.constraint.MethodConstraints;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InvocationDispatcher;
import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.ServerCapabilities;

/**
 * Defines an <code>InvocationLayerFactory</code> that uses pop-up dialog
 * windows to confirm calls.
 *
 * 
 */
public class ConfirmingILFactory extends BasicILFactory {

    /**
     * Creates a <code>InvocationLayerFactory</code> that confirms calls, with
     * no server constraints and no permission class.
     */
    public ConfirmingILFactory() { }

    /**
     * Creates a <code>InvocationLayerFactory</code> that confirms calls, and
     * uses the specified server constraints and permission class.
     */
    public ConfirmingILFactory(MethodConstraints serverConstraints,
			       Class permissionClass)
    {
	super(serverConstraints, permissionClass);
    }

    /**
     * Returns a confirming invocation handler for the object endpoint and
     * interfaces.
     *
     * @param interfaces the interfaces
     * @param impl the remote object
     * @param oe the object endpoint
     * @return a confirming invocation handler for the object endpoint and
     *	       interfaces
     */
    protected InvocationHandler createInvocationHandler(Class[] interfaces,
							Remote impl,
							ObjectEndpoint oe)
    {
	return new ConfirmingInvocationHandler(oe, getServerConstraints());
    }

    
    /**
     * Returns a confirming invocation dispatcher for the remote object.
     *
     * @param	methods a collection of {@link Method} instances for the
     *		remote methods
     * @param	impl a remote object that the dispatcher is being created for
     * @param	capabilities the transport capabilities of the server
     * @return a confirming invocation dispatcher for the remote object
     */
    protected InvocationDispatcher createInvocationDispatcher(
	Collection methods, Remote impl, ServerCapabilities capabilities)
	throws ExportException
    {
	if (impl == null) {
	    throw new NullPointerException("impl is null");
	}
	return new Dispatch(methods, capabilities, getServerConstraints(),
			    getPermissionClass());
    }

    /** Defines a subclass of BasicInvocationDispatcher that confirms calls. */
    private static class Dispatch extends BasicInvocationDispatcher {

	Dispatch(Collection methods,
		 ServerCapabilities capabilities,
		 MethodConstraints serverConstraints,
		 Class permissionClass)
	    throws ExportException
	{
	    super(methods, capabilities,
		  serverConstraints, permissionClass, null);
	}

	/**
	 * Reads the call identifier and asks whether the call should be
	 * permitted before unmarshalling the arguments.
	 */
	protected Object[] unmarshalArguments(Remote obj,
					      Method method,
					      ObjectInputStream in,
					      Collection context)
	    throws IOException, ClassNotFoundException
	{
	    long callId = in.readLong();
	    ClientHost client = null;
	    try {
		client = (ClientHost)
		    ServerContext.getServerContextElement(ClientHost.class);
	    } catch (ServerNotActiveException e) {
	    }
	    int result = JOptionPane.showConfirmDialog(
		null,
		"Permit incoming remote call?" +
		"\n  Client: " + (client != null
				  ? client.getClientHost() : "not active") +
		"\n  Object: " + obj +
		"\n  Method: " + method.getName() +
		"\n  Call id: " + callId,
		"Permit incoming remote call?",
		JOptionPane.OK_CANCEL_OPTION);
	    if (result != JOptionPane.OK_OPTION) {
		throw new SecurityException("Server denied call");
	    }
	    return super.unmarshalArguments(obj, method, in, context);
	}
    }
}
