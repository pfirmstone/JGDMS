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
 * Encapsulate the details of marshaling a multicast request into one or
 * more packets.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see IncomingMulticastRequest
 */
public class OutgoingMulticastRequest {
    /**
     * The minimum size we allow for an outgoing packet.
     */
    protected static final int minMaxPacketSize = 512;

    /**
     * The current version of the multicast announcement protocol.
     */
    protected static final int protocolVersion = 1;

    /**
     * Using the default maximum packet size, marshal a multicast request
     * into one or more datagram packets. These packets are guaranteed
     * to contain, between them, all of the groups in which the requestor
     * is interested.  However, the set of ServiceIDs from which the
     * requestor has heard may be incomplete.
     * <p>
     * The datagram packets returned will have been initialized for
     * sending to the appropriate multicast address and UDP port.
     *
     * @param responsePort  the port to which respondents should
     *                      connect in order to start unicast discovery
     * @param groups        the set of groups in which the requestor is
     *                      interested
     * @param heard         the set of ServiceIDs from which the requestor has
     *                      already heard
     *
     * @return an array of datagram packets, which will always
     *         contain at least one member
     *
     * @exception IOException an error occurred during marshalling.
     *
     * @exception IllegalArgumentException when the number and length of the
     *                                     group names to marshal, relative
     *                                     to the value of the default packet
     *                                     size maximum, is too large.
      */
    public static DatagramPacket[] marshal(int responsePort,
					   String[] groups,
					   ServiceID[] heard) 
                                                           throws IOException
    {
        return marshal(responsePort, groups, heard, minMaxPacketSize );
    }

    /**
     * Using the given maximum packet size, marshal a multicast request
     * into one or more datagram packets. These packets are guaranteed
     * to contain, between them, all of the groups in which the requestor
     * is interested.  However, the set of ServiceIDs from which the
     * requestor has heard may be incomplete.
     * <p>
     * The datagram packets returned will have been initialized for
     * sending to the appropriate multicast address and UDP port.
     *
     * @param responsePort  the port to which respondents should
     *                      connect in order to start unicast discovery
     * @param groups        the set of groups in which the requestor is
     *                      interested
     * @param heard         the set of ServiceIDs from which the requestor has
     *                      already heard
     * @param maxPacketSize the maximum size to allow for an outgoing packet
     *
     * @return an array of datagram packets, which will always
     *         contains at least one member
     *
     * @exception IOException              an error occurred during marshalling
     *
     * @exception IllegalArgumentException when the value of the 
     *                                     <code>maxPacketSize</code> argument
     *                                     is less than the default packet
     *                                     size maximum; or when the number
     *                                     and length of the group names to
     *                                     marshal, relative to the value of
     *                                     the <code>maxPacketSize</code>
     *                                     argument, is too large.
     */
    public static DatagramPacket[] marshal(int responsePort,
					   String[] groups,
					   ServiceID[] heard,
                                           int maxPacketSize)
                                                           throws IOException
    {
        if (maxPacketSize < minMaxPacketSize) {
            throw new IllegalArgumentException("maxPacketSize ("
                                               +maxPacketSize+") is "
                                               +"less than minMaxPacketSize ("
                                               +minMaxPacketSize+")");
        }//endif
	// Marshal the fixed header stuff.
	byte[] marshaledHeader;
	{
	    ByteArrayOutputStream hbs = new ByteArrayOutputStream();
	    DataOutputStream hos = new DataOutputStream(hbs);
	    // Write out the relatively fixed stuff first.
	    hos.writeInt(protocolVersion);
	    hos.writeInt(responsePort);
	    marshaledHeader = hbs.toByteArray();
	}
	// Marshal all group names, and find the length of the longest
	// marshaled representation as we go.
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
		throw new IllegalArgumentException("group name marshals too "
                                                   +"large (" +
						   marshaledGroups[i].length 
                                                   + " bytes)");
	    if (marshaledGroups[i].length > longestGroup)
		longestGroup = marshaledGroups[i].length;
	}
	// Marshal the set of service IDs we've heard from.  We have
	// to make sure that we can fit at least one group name into a
	// request, so if we have heard from too many lookup services,
	// we have to truncate this set.  This is messy.
	byte[] marshaledHeard;
	{
	    int prevTotal = 0;
	    int total = 0;
	    int count;
	    ByteArrayOutputStream dbs = new ByteArrayOutputStream();
	    DataOutputStream dos = new DataOutputStream(dbs);
	    // Write out heard service IDs.  We keep writing until we
	    // have one too many.
	    for (count = 0; count < heard.length; count++) {
		heard[count].writeBytes(dos);
		prevTotal = total;
		total = dbs.size();
		// @@@ We are hard-coding knowledge about the packet
		// format here.  This can't be avoided.
		if (marshaledHeader.length +
		    4 /* group count */ + longestGroup +
		    4 /* heard count */ + total > maxPacketSize)
		    break;
	    }
	    dos.flush();
	    dbs.flush();
	    byte[] tmp = dbs.toByteArray();
	    ByteArrayOutputStream hbs = new ByteArrayOutputStream();
	    DataOutputStream hos = new DataOutputStream(hbs);
	    hos.writeInt(count);
	    hos.write(tmp, 0, count == heard.length ? total : prevTotal);
	    hos.flush();
	    marshaledHeard = hbs.toByteArray();
	}
	// We now know the size of the header and the set of marshaled
	// service IDs.  Fill in the requests using the groups.
	// Build up a collection of requests.
	Collection reqs = new Vector();
	InetAddress addr = Constants.getRequestAddress();
	if (groups.length == 0) {
	    ByteArrayOutputStream bs =
		new ByteArrayOutputStream(maxPacketSize);
	    DataOutputStream os = new DataOutputStream(bs);
	    os.write(marshaledHeader);
	    os.write(marshaledHeard);
	    os.writeInt(0); // no groups to write
	    os.flush();
	    byte[] payload = bs.toByteArray();
	    reqs.add(new DatagramPacket(payload, payload.length, addr,
					Constants.discoveryPort));
	} else {
	    for (int curr = 0; curr < marshaledGroups.length; ) {
		ByteArrayOutputStream bs =
		    new ByteArrayOutputStream(maxPacketSize);
		DataOutputStream os = new DataOutputStream(bs);
		os.write(marshaledHeader);
		os.write(marshaledHeard);
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
	Iterator iter = reqs.iterator();
	DatagramPacket[] ary = new DatagramPacket[reqs.size()];
	for (int i = 0; iter.hasNext(); i++) {
	    ary[i] = (DatagramPacket) iter.next();
	}
	return ary;
    }
}
