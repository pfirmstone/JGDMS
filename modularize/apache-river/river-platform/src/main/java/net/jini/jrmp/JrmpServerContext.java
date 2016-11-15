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

package net.jini.jrmp;

import org.apache.river.jeri.internal.runtime.Util;
import java.net.InetAddress;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientHost;

/**
 * Server context provider for remote objects exported via JRMP.  This class is
 * intended to be specified in a resource to configure the operation of 
 * {@link ServerContext#getServerContext}.
 * 
 * @author	Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class JrmpServerContext implements ServerContext.Spi {
    
    /**
     * Returns a server context collection containing an element that
     * implements the {@link ClientHost} interface whose
     * {@link ClientHost#getClientHost getClientHost} method
     * returns the client host if the current thread is handling a JRMP
     * remote call, or <code>null</code> otherwise.  The client host string
     * is determined by calling {@link RemoteServer#getClientHost}; if
     * <code>getClientHost</code> throws a {@link
     * ServerNotActiveException}, then no JRMP call is in progress and
     * <code>null</code> is returned.
     * 
     * @return	server context collection or <code>null</code>
     **/
    public Collection getServerContext() {
	try {
	    String host = RemoteServer.getClientHost();
	    InetAddress addr = null;
	    try {
		addr = InetAddress.getByName(host);
	    } catch (Exception e) {
		// REMIND: this exception should be logged
	    }
	    Collection context = Collections.EMPTY_LIST;
	    if (addr != null) {
		context = new ArrayList(1);
		Util.populateContext(context, addr);
	    } 
	    return Collections.unmodifiableCollection(context);
	} catch (ServerNotActiveException ex) {
	    return null;
	}
    }
}
