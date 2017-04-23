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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import net.jini.core.lookup.ServiceID;

/**
 * Encapsulate the details of unmarshaling an incoming multicast
 * request packet.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutgoingMulticastRequest
 */
public class IncomingMulticastRequest {
    /**
     * The address to which any responses should go.
     */
    protected InetAddress addr;
    /**
     * The port to which any responses should connect.
     */
    protected int port;
    /**
     * The ServiceIDs from which the requestor has already heard.
     * This set may not be complete.
     */
    protected ServiceID[] serviceIDs;
    /**
     * The groups in which the requestor is interested.  This set may
     * not be complete.
     */
    protected String[] groups;
    /**
     * The current version of the multicast request protocol.
     */
    protected final int protoVersion = 1;

    /**
     * Construct a new object, initialized from the contents of the
     * given multicast request packet.
     *
     * @param dgram the packet to unmarshal
     * @exception IOException a problem occurred while unmarshaling
     * the packet
     */
    public IncomingMulticastRequest(DatagramPacket dgram) throws IOException {
	ByteArrayInputStream bs = new ByteArrayInputStream(dgram.getData());
	DataInputStream ds = new DataInputStream(bs);
	int proto = ds.readInt();
	if (proto != protoVersion)
	    throw new IOException("unsupported protocol version: " + proto);
	port = ds.readInt();
	// Valid port ranges are 1 through 65535 for IPv4.
	if (port <= 0 || port >= 65536)
	    throw new IOException("port number out of range: " + port);
	int sidCount = ds.readInt();
	// We know that a ServiceID marshals to 8 bytes, so ensure
	// that the count can plausibly fit into this packet.
	if (sidCount < 0 || sidCount > bs.available() / 8)
	    throw new IOException("service ID count invalid: " + sidCount);
	serviceIDs = new ServiceID[sidCount];
	for (int i = 0; i < serviceIDs.length; i++) {
	    serviceIDs[i] = new ServiceID(ds);
	}
	int grpCount = ds.readInt();
	// We know that the minimal length of a string written with
	// DataOutput.writeUTF is two bytes (for the empty string), so
	// we make some attempt to see if the count can plausibly fit
	// into this packet.
	if (grpCount < 0 || grpCount > bs.available() / 2)
	    throw new IOException("group count invalid: " + grpCount);
	groups = new String[grpCount];
	for (int i = 0; i < groups.length; i++) {
	    groups[i] = ds.readUTF();
	}
	addr = dgram.getAddress();
    }

    /**
     * Return the address of the host to contact in order to start
     * unicast discovery.
     *
     * @return the address of the host to contact in order to start
     *         unicast discovery
     */
    public InetAddress getAddress() {
	return addr;
    }

    /**
     * Return the number of the port to connect to on the remote host
     * in order to start unicast discovery.
     *
     * @return the number of the port to connect to on the remote host
     *         in order to start unicast discovery
     */
    public int getPort() {
	return port;
    }

    /**
     * Return the set of groups in which the requestor is interested.
     * This set may not be complete, but other incoming packets should
     * contain the rest of the set.
     *
     * @return the set of groups in which the requestor is interested.
     */
    public String[] getGroups() {
	return groups;
    }

    /**
     * Return a set of ServiceIDs from which the requestor has already
     * heard.  This set may not be complete.
     *
     * @return a set of service IDs from which the requestor has already
     *         heard
     */
    public ServiceID[] getServiceIDs() {
	return serviceIDs;
    }

    public int hashCode() {
	byte[] foo = addr.getAddress();
	int code = (foo[0] << 3) ^ (foo[1] << 7) ^ (foo[2] << 5) ^ (foo[3] << 1) ^
	    ~port;
	for (int i = 0; i < groups.length; i++) {
	    code ^= groups[i].hashCode() << i;
	}
	for (int i = 0; i < serviceIDs.length; i++) {
	    code ^= serviceIDs[i].hashCode() << i;
	}
	return code;
    }

    /**
     * Two requests are equal if they have the same address, port,
     * groups, and service IDs.
     */
    public boolean equals(Object o) {
	// We assume here that multiple requests from the same machine
	// will have the same source IP address.  This is not
	// necessarily true.
	if (o instanceof IncomingMulticastRequest) {
	    IncomingMulticastRequest oo = (IncomingMulticastRequest) o;
	    if (oo.addr.equals(addr) && oo.port == port &&
		oo.groups.length == groups.length &&
		oo.serviceIDs.length == serviceIDs.length)
	    {
		for (int i = 0; i < groups.length; i++) {
		    if (!groups[i].equals(oo.groups[i]))
			return false;
		}
		for (int i = 0; i < serviceIDs.length; i++) {
		    if (!serviceIDs[i].equals(oo.serviceIDs[i]))
			return false;
		}
		return true;
	    }
	}
	return false;
    }
}
