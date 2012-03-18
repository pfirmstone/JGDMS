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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.security.AccessController;
import java.security.PrivilegedAction;
import net.jini.core.lookup.ServiceID;

/**
 * Encapsulate the details of marshaling a multicast announcement into
 * one or more packets.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see IncomingMulticastAnnouncement
 */
public class OutgoingMulticastAnnouncement {
    /**
     * The minimum size we allow for an outgoing packet.
     */
    protected static final int minMaxPacketSize = 512;
    /**
     * The maximum size we allow for an outgoing packet.  This may be
     * controlled using the <tt>net.jini.discovery.mtu</tt> system
     * property.  The value cannot be less than 512.  The default is 512.
     */
    protected static final int maxPacketSize =
	((Integer)AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		try {
		    return Integer.getInteger("net.jini.discovery.mtu",
					      minMaxPacketSize);
		} catch (SecurityException e) {
		    return Integer.valueOf(minMaxPacketSize);
		}
	    }
	})).intValue();
    /**
     * The current version of the multicast announcement protocol.
     */
    protected static final int protocolVersion = 1;

    /**
     * Marshal a multicast announcement into one or more datagram
     * packets.  These packets are guaranteed to contain, between
     * them, all of the groups of which the to-be-announced lookup
     * service is a member. <p>
     *
     * The datagram packets returned will have been initialized for
     * sending to the appropriate multicast address and UDP port.
     *
     * @param id the ServiceID we are announcing
     * @param loc a LookupLocator that will allow unicast discovery of
     * the lookup service we are announcing
     * @param groups the groups of which the announced lookup service
     * is a member
     * @return an array of datagram packets, which will always contain
     * at least one member
     * @exception IOException a problem occurred during marshaling
     */
    public static DatagramPacket[] marshal(ServiceID id,
					   LookupLocator loc,
					   String[] groups)
	throws IOException
    {
	if (maxPacketSize < minMaxPacketSize)
	    throw new RuntimeException("value of net.jini.discovery.mtu property is less than " + minMaxPacketSize);
	// Marshal the fixed header stuff.
	byte[] marshaledHeader;
	{
	    ByteArrayOutputStream hbs = new ByteArrayOutputStream();
	    DataOutputStream hos = new DataOutputStream(hbs);
	    // Write out the relatively fixed stuff first.
	    hos.writeInt(protocolVersion);
	    hos.writeUTF(loc.getHost());
	    hos.writeInt(loc.getPort());
	    id.writeBytes(hos);
	    marshaledHeader = hbs.toByteArray();
	}
	if (marshaledHeader.length > maxPacketSize)
	    throw new IllegalArgumentException("host name marshals too large");
	byte[][] marshaledGroups = new byte[groups.length][];
	// Length of the longest group.
	int longestGroup = -1;
	for (int i = 0; i < groups.length; i++) {
	    ByteArrayOutputStream gbs = new ByteArrayOutputStream();
	    DataOutputStream gos = new DataOutputStream(gbs);
	    gos.writeUTF(groups[i]);
	    gos.flush();
	    marshaledGroups[i] = gbs.toByteArray();
	    if (marshaledHeader.length +
		4 /* heard count */ + /* assume no service IDs heard */
		4 /* group count */ +
		marshaledGroups[i].length > maxPacketSize)
		throw new IllegalArgumentException("group name marshals too large (" +
						   marshaledGroups[i].length +
						   " bytes)");
	    if (marshaledGroups[i].length > longestGroup)
		longestGroup = marshaledGroups[i].length;
	}
	// Build up a collection of requests.
	Collection reqs = new Vector();
	{
	    InetAddress addr = Constants.getAnnouncementAddress();
	    if (groups.length == 0) {
		ByteArrayOutputStream bs =
		    new ByteArrayOutputStream(maxPacketSize);
		DataOutputStream os = new DataOutputStream(bs);
		os.write(marshaledHeader);
		os.writeInt(0);
		byte[] payload = bs.toByteArray();
		reqs.add(new DatagramPacket(payload, payload.length,
					    addr, Constants.discoveryPort));
	    } else {
		for (int curr = 0; curr < marshaledGroups.length; ) {
		    ByteArrayOutputStream bs =
			new ByteArrayOutputStream(maxPacketSize);
		    DataOutputStream os = new DataOutputStream(bs);
		    os.write(marshaledHeader);
		    os.flush();
		    int bytes = bs.size() + 4 /* for group count */;
		    int last = curr;
		    while (last < marshaledGroups.length &&
			   bytes + marshaledGroups[last].length <= maxPacketSize)
		    {
			bytes += marshaledGroups[last].length;
			last += 1;
		    }
		    os.writeInt(last - curr);
		    while (curr < last) {
			os.write(marshaledGroups[curr++]);
		    }
		    os.flush();
		    byte[] payload = bs.toByteArray();
		    reqs.add(new DatagramPacket(payload, payload.length,
						addr, Constants.discoveryPort));
		}
	    }
	}
	Iterator iter = reqs.iterator();
	DatagramPacket[] ary = new DatagramPacket[reqs.size()];
	for (int i = 0; iter.hasNext(); i++) {
	    ary[i] = (DatagramPacket) iter.next();
	}
	return ary;
    }
}
