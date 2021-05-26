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
 * Class representing the values in a multicast announcement.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class MulticastAnnouncement {

    /** The announcement sequence number. */
    private final long sequenceNumber;
    /** The lookup service host. */
    private final String host;
    /** The lookup service listen port. */
    private final int port;
    /** The lookup service member groups. */
    private final String[] groups;
    /** The lookup service ID. */
    private final ServiceID serviceID;

    /**
     * Creates a new <code>MulticastAnnouncement</code> instance containing the
     * given values.  The <code>groups</code> array is copied; a
     * <code>null</code> value is considered equivalent to an empty array.
     *
     * @param sequenceNumber the announcement sequence number
     * @param host the lookup service host
     * @param port the lookup service listen port
     * @param groups the lookup service member groups
     * @param serviceID the lookup service ID
     * @throws NullPointerException if <code>host</code> or
     * <code>serviceID</code> is <code>null</code>, or if <code>groups</code>
     * contains <code>null</code>
     * @throws IllegalArgumentException if <code>port</code> is out of range
     */
    public MulticastAnnouncement(   long sequenceNumber,
				    String host,
				    int port,
				    String[] groups,
				    ServiceID serviceID)
    {
	this(sequenceNumber, host, port, groups, serviceID,
		check(host, port, groups, serviceID));
    }
    
    private static boolean check(String host,
				 int port,
				 String[] groups,
				 ServiceID serviceID)
    {
	groups = (groups != null) ? groups : new String[0];
	if (host == null || 
	    serviceID == null ||
	    Arrays.asList(groups).contains(null))
	{
	    throw new NullPointerException();
	}
	if (port < 0 || port > 0xFFFF) {
	    throw new IllegalArgumentException("port out of range: " + port);
	}
	return true;
    }
    
    private MulticastAnnouncement(  long sequenceNumber,
				    String host,
				    int port,
				    String[] groups,
				    ServiceID serviceID,
				    boolean check)
    {
	this.sequenceNumber = sequenceNumber;
	this.host = host;
	this.port = port;
	this.groups = groups.clone();
	this.serviceID = serviceID;
    }

    /**
     * Returns the announcement sequence number.
     *
     * @return the announcement sequence number
     */
    public long getSequenceNumber() {
	return sequenceNumber;
    }

    /**
     * Returns the lookup service host name.
     *
     * @return the lookup service host name
     */
    public String getHost() {
	return host;
    }

    /**
     * Returns the lookup service listen port.
     *
     * @return the lookup service listen port
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns the member groups of the lookup service.
     *
     * @return the member groups of the lookup service
     */
    public String[] getGroups() {
	return groups.clone();
    }

    /**
     * Returns the service ID of the lookup service.
     *
     * @return the service ID of the lookup service
     */
    public ServiceID getServiceID() {
	return serviceID;
    }
    
    /**
     * Checks if the constraints whose checking was delayed when this instance
     * was decoded, if any, are satisfied. If the instance was not decoded,
     * but directly constructed, this method does nothing.
     * @throws UnsupportedConstraintException if unable to satisfy the specified
     * constraints
     * @throws SecurityException if the given constraints cannot be satisfied
     * due to insufficient caller permissions
     * @throws IOException if an error occurs in interpreting the data
     */
    public void checkConstraints() throws IOException {
        // do nothing by default
    }
    
    /**
     * Returns a string representation of this announcement.
     *
     * @return a string representation of this announcement
     */
    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder(120);
	sb.append("MulticastAnnouncement[").append(sequenceNumber).append(",")
	    .append(host).append(":").append(port).append(",")
	    .append(Arrays.asList(groups)).append(",")
	    .append(serviceID).append("]");
	return sb.toString();
    }
}
