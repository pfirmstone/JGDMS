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
package com.sun.jini.outrigger;

import java.lang.reflect.Method;
import net.jini.id.Uuid;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import com.sun.jini.proxy.ConstrainableProxyUtil;

/**
 * Constrainable subclass of <code>IteratorProxy</code>
 */
final class ConstrainableIteratorProxy extends IteratorProxy {
    /**
     * Array containing element pairs in which each pair of elements
     * represents a mapping between two methods having the following
     * characteristics:
     * <ul>
     * <li> the first element in the pair is one of the public, remote
     *      method(s) that may be invoked by the client through 
     *      <code>AdminIterator</code>.
     * <li> the second element in the pair is the method, implemented
     *      in the backend server class, that is ultimately executed in
     *      the server's backend when the client invokes the corresponding
     *      method in this proxy.
     * </ul>
     */
    private static final Method[] methodMapArray =  {
	ProxyUtil.getMethod(AdminIterator.class, "next", 
			    new Class[] {}),
	ProxyUtil.getMethod(OutriggerAdmin.class, "nextReps", 
			    new Class[] {Uuid.class,
					 int.class,
					 Uuid.class}),

	ProxyUtil.getMethod(AdminIterator.class, "delete", 
			    new Class[] {}),
	ProxyUtil.getMethod(OutriggerAdmin.class,"delete", 
			    new Class[] {Uuid.class,
					 Uuid.class}),

	ProxyUtil.getMethod(AdminIterator.class, "close", 
			    new Class[] {}),
	ProxyUtil.getMethod(OutriggerAdmin.class,"close", 
			    new Class[] {Uuid.class})
    };

    /**
     * Create a new <code>ConstrainableIteratorProxy</code>.
     * @param iterationUuid The identity of the iteration this proxy is for.
     * @param server reference to remote server for the space.
     * @param fetchSize Number of entries to ask for when it goes to the
     *                  server
     * @param methodConstraints the client method constraints to place on
     *                          this proxy (may be <code>null</code>).
     * @throws NullPointerException if <code>server</code> or
     *         <code>iterationUuid</code> is <code>null</code>.     
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    ConstrainableIteratorProxy(Uuid iterationUuid, OutriggerAdmin server,
	int fetchSize, MethodConstraints methodConstraints)
    {
	super(iterationUuid, constrainServer(server, methodConstraints),
	      fetchSize);
    }

    /**
     * Returns a copy of the given <code>OutriggerAdmin</code> proxy
     * having the client method constraints that result after
     * mapping defined by methodMapArray is applied.
     * @param server The proxy to attach constrains too.
     * @param constraints The source method constraints.
     * @throws NullPointerException if <code>server</code> is 
     *         <code>null</code>.
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    private static OutriggerAdmin constrainServer(OutriggerAdmin server,
        MethodConstraints constraints)
    {
	final MethodConstraints serverRefConstraints 
	    = ConstrainableProxyUtil.translateConstraints(constraints,
							  methodMapArray);
	final RemoteMethodControl constrainedServer = 
	    ((RemoteMethodControl)server).
	    setConstraints(serverRefConstraints);

	return (OutriggerAdmin)constrainedServer;
    }
}
