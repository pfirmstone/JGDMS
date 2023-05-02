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

package org.apache.river.discovery;

import java.io.IOException;
import java.util.Arrays;
import net.jini.core.lookup.ServiceID;
import net.jini.io.UnsupportedConstraintException;

/**
 * Class representing the values in a multicast request.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class MulticastRequest {

    /** The client host. */
    private final String host;
    /** The client listen port. */
    private final int port;
    /** The groups of interest. */
    private final String[] groups;
    /** The IDs of known lookup services. */
    private final ServiceID[] serviceIDs;

    /**
     * Creates a new <code>MulticastRequest</code> instance containing the
     * given values.  Arrays are copied; <code>null</code> array values are
     * considered equivalent to empty arrays.
     *
     * @param host the client host
     * @param port the client listen port
     * @param groups the groups of interest
     * @param serviceIDs the IDs of known lookup services
     * @throws NullPointerException if <code>host</code> is <code>null</code>,
     * or if <code>groups</code> or <code>serviceIDs</code> contains
     * <code>null</code>
     * @throws IllegalArgumentException if <code>port</code> is out of range
     */
    public MulticastRequest(String host,
			    int port,
			    String[] groups,
			    ServiceID[] serviceIDs)
    {
	groups = (groups != null) ? (String[]) groups.clone() : new String[0];
	serviceIDs = (serviceIDs != null) ?
	    (ServiceID[]) serviceIDs.clone() : new ServiceID[0];
	if (host == null ||
	    Arrays.asList(groups).contains(null) ||
	    Arrays.asList(serviceIDs).contains(null))
	{
	    throw new NullPointerException();
	}
	if (port < 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException("port out of range: " + port);
	}
	this.host = host;
	this.port = port;
	this.groups = groups;
	this.serviceIDs = serviceIDs;
    }

    /**
     * Returns the client host name.
     *
     * @return the client host name
     */
    public String getHost() {
	return host;
    }

    /**
     * Returns the client listen port.
     *
     * @return the client listen port
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns the groups targeted by the request.
     *
     * @return the groups targeted by the request
     */
    public String[] getGroups() {
	return (String[]) groups.clone();
    }

    /**
     * Returns the service IDs of known lookup services.
     *
     * @return the service IDs of known lookup services
     */
    public ServiceID[] getServiceIDs() {
	return (ServiceID[]) serviceIDs.clone();
    }

    /**
     * Checks if the constraints whose checking was delayed when this instance
     * was decoded, if any, are satisfied. If the instance was not decoded, but
     * directly constructed, this method does nothing.
     * @throws UnsupportedConstraintException if unable to satisfy the specified
     * constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions, or if the client subject check
     * fails
     * @throws IOException if an error occurs in interpreting the data
     *
     */
    public void checkConstraints() throws IOException {
        // do nothing
    }

    /**
     * Returns string representation of this request.
     *
     * @return string representation of this request
     */
    public String toString() {
	return "MulticastRequest[" + host + ":" + port + ", " +
	       Arrays.asList(groups) + ", " + Arrays.asList(serviceIDs) + "]";
    }
}
