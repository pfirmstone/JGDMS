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

package com.sun.jini.phoenix;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.server.ServerNotActiveException;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Map;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;

/**
 * Definition of an access control only allowing calls from the local host.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
class LocalAccess {
    private static Map cache = new HashMap(3);

    private LocalAccess() {
    }

    /**
     * Checks whether a call was made by a local host.
     * Specifically, this means that if, in the dispatched call,
     * there is a ServerContext and it contains an element
     * that is an instance of ClientHost, then if the InetAddress
     * returned by ClientHost.getClientHost is not a local
     * network interface (according to NetworkInterface.getByInetAddress)
     * or not a loopback address
     * (according to NetworkInterface.isLoopBackAddress),
     * the call will be rejected; in all other cases,
     * the call will be accepted.
     * 
     * @throws AccessControlException when the origin is not a local host
     */
    public static synchronized void check() {
	ClientHost host = null;
	try {
	    host = (ClientHost)
		ServerContext.getServerContextElement(ClientHost.class);
	} catch (ServerNotActiveException e) {
	    return;
	}
	if (host == null) return;
	InetAddress addr = host.getClientHost();
	Boolean ok = (Boolean) cache.get(addr);
	if (ok == null) {
	    try {
		ok = Boolean.valueOf(addr != null && (NetworkInterface.getByInetAddress(
					addr) != null || addr.isLoopbackAddress()));
	    } catch (IOException e) {
		ok = Boolean.FALSE;
	    }
	    cache.put(addr, ok);
	}
	if (!ok.booleanValue()) {
	    throw new AccessControlException("origin is non-local host");
	}
    }
}
