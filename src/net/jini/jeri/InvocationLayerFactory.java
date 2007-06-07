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

package net.jini.jeri;

import java.rmi.Remote;
import java.rmi.server.ExportException;
import net.jini.export.Exporter;

/**
 * A factory for creating a compatible proxy and invocation dispatcher for
 * a remote object being exported.  An <code>InvocationLayerFactory</code>
 * is used in conjunction with the {@link BasicJeriExporter} class to
 * customize remote invocation and dispatch behavior for remote objects.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 **/
public interface InvocationLayerFactory {

    /**
     * Returns a compatible proxy and invocation dispatcher for a remote
     * object being exported.  The proxy and invocation dispatcher are
     * returned in an {@link Instances} container object.  The proxy sends
     * calls to the remote object using the supplied
     * <code>ObjectEndpoint</code>.
     *
     * <p>The returned proxy implements an implementation-specific set of
     * remote interfaces of <code>impl</code> and may implement additional
     * implementation-specific interfaces.
     *
     * <p>A given {@link Exporter} implementation should only call this
     * method once per export.  An invocation dispatcher constructed for a
     * previous export should not be reused.
     *
     * @param	impl the remote object that the proxy is being
     *		created for
     * @param	oe the object endpoint used to communicate with
     *		the remote object
     * @param	caps the transport capabilities of the server
     * @return	a proxy and invocation dispatcher contained in an
     *		<code>Instances</code> object
     * @throws	ExportException if there is a problem creating the proxy or
     *		dispatcher 
     * @throws	NullPointerException if any argument is <code>null</code>
     **/
    Instances createInstances(Remote impl,
			      ObjectEndpoint oe,
			      ServerCapabilities caps)
	throws ExportException;

    /**
     * A container for the proxy and invocation dispatcher instances
     * returned by {@link InvocationLayerFactory#createInstances
     * InvocationLayerFactory.createInstances}.
     *
     * @since 2.0
     **/
    public class Instances {

	private final Remote proxy;
	private final InvocationDispatcher dispatcher;

	/**
	 * Creates a container for a proxy instance and an invocation
	 * dispatcher instance. 
	 *
	 * @param   proxy a proxy
	 * @param   dispatcher an invocation dispatcher
	 * @throws  NullPointerException if any argument is
	 *	    <code>null</code> 
	 **/
	public Instances(Remote proxy, InvocationDispatcher dispatcher) {
	    if (proxy == null || dispatcher == null) {
		throw new NullPointerException();
	    }
	    this.proxy = proxy;
	    this.dispatcher = dispatcher;
	}

	/**
	 * Returns the proxy.
	 *
	 * @return the proxy
	 */
	public Remote getProxy() {
	    return proxy;
	}

	/**
	 * Returns the invocation dispatcher.
	 *
	 * @return the invocation dispatcher
	 */
	public InvocationDispatcher getInvocationDispatcher() {
	    return dispatcher;
	}
    }
}
