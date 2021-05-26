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

package org.apache.river.discovery.internal;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import org.apache.river.discovery.UnicastDiscoveryClient;
import org.apache.river.discovery.UnicastResponse;

/**
 *
 * @author peter
 */
public abstract class UnicastClient implements UnicastDiscoveryClient {
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.    
    private final EndpointBasedClient impl; 
    
    protected UnicastClient(EndpointBasedClient impl){
	this.impl = impl;
    }

    // javadoc inherited from DiscoveryFormatProvider
    @Override
    public String getFormatName() {
	return impl.getFormatName();
    }

    // javadoc inherited from UnicastDiscoveryClient
    @Override
    public void checkUnicastDiscoveryConstraints(
		    InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	impl.checkUnicastDiscoveryConstraints(constraints);
    }
    
    // javadoc inherited from UnicastDiscoveryClient
    @Override
    public UnicastResponse doUnicastDiscovery(Socket socket,
					      InvocationConstraints constraints,
					      ClassLoader defaultLoader,
					      ClassLoader verifierLoader,
					      Collection context,
					      ByteBuffer sent,
					      ByteBuffer received)
	throws IOException, ClassNotFoundException
    {
	return impl.doUnicastDiscovery(socket, constraints, defaultLoader,
				       verifierLoader, context, sent, received);
    }
    
}
