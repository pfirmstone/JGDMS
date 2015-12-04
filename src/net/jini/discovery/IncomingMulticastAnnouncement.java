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
package net.jini.discovery;

import net.jini.core.discovery.LookupLocator;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import net.jini.core.lookup.ServiceID;

/**
 * Encapsulate the details of unmarshaling an incoming multicast
 * announcement packet.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutgoingMulticastAnnouncement
 */
public class IncomingMulticastAnnouncement {
    /**
     * The ServiceID that has been announced.
     */
    protected ServiceID serviceID;
    /**
     * The LookupLocator that has been announced.
     */
    protected LookupLocator locator;
    /**
     * The groups of which the announcing lookup service is a member.
     */
    protected String[] groups;
    /**
     * Current version of the multicast announcement protocol.
     */
    protected final int protoVersion = 1;

    /**
     * Construct a new object, initialized by unmarshaling the
     * contents of a multicast announcement packet.
     *
     * @param p the packet to unmarshal
     * @exception IOException a problem occurred in unmarshaling the
     * packet
     */
    public IncomingMulticastAnnouncement(DatagramPacket p) throws IOException {
	ByteArrayInputStream bs = new ByteArrayInputStream(p.getData());
	DataInputStream ds = new DataInputStream(bs);
	int proto = ds.readInt();
	if (proto != protoVersion)
	    throw new IOException("unsupported protocol version: " + proto);
	String host = ds.readUTF();
	int port = ds.readInt();
	if (port <= 0 || port >= 65536)
	    throw new IOException("port number out of range: " + port);
	locator = new LookupLocator(host, port);
	serviceID = new ServiceID(ds);
	int groupCount = ds.readInt();
	// We know that the minimal length of a string written with
	// DataOutput.writeUTF is two bytes (for the empty string), so
	// we make some attempt to see if the count can plausibly fit
	// into this packet.
	if (groupCount < 0 || groupCount > bs.available() / 2)
	    throw new IOException("group count invalid: " + groupCount);
	groups = new String[groupCount];
	for (int i = 0; i < groups.length; i++) {
	    groups[i] = ds.readUTF();
	}
    }

    /**
     * Return the ServiceID of the announcing lookup service.
     * @return the service ID of the announcing lookup service
     */
    public ServiceID getServiceID() {
	return serviceID;
    }

    /**
     * Return a LookupLocator for performing unicast discovery of the
     * announcing lookup service.
     * @return a <code>LookupLocator</code> for performing unicast 
     *         discovery of the announcing lookup service
     */
    public LookupLocator getLocator() {
	return locator;
    }

    /**
     * Return the groups of which the announcing lookup service is a
     * member.
     * @return the groups of which the announcing lookup service is a
     *         member. 
     */
    public String[] getGroups() {
	return groups;
    }

    public int hashCode() {
	return serviceID.hashCode();
    }

    /**
     * Two announcements are equal if they have the same service ID.
     */
    public boolean equals(Object o) {
	return o instanceof IncomingMulticastAnnouncement &&
	    ((IncomingMulticastAnnouncement) o).serviceID.equals(serviceID);
    }
}
