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

package com.sun.jini.discovery.plaintext;

import com.sun.jini.discovery.ClientSubjectChecker;
import com.sun.jini.discovery.DatagramBufferFactory;
import com.sun.jini.discovery.MulticastAnnouncement;
import com.sun.jini.discovery.MulticastAnnouncementEncoder;
import com.sun.jini.discovery.MulticastRequest;
import com.sun.jini.discovery.MulticastRequestDecoder;
import com.sun.jini.discovery.UnicastDiscoveryServer;
import com.sun.jini.discovery.UnicastResponse;
import com.sun.jini.discovery.internal.Plaintext;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Implements the server side of the <code>net.jini.discovery.plaintext</code>
 * discovery format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Server
    implements MulticastRequestDecoder,
	       MulticastAnnouncementEncoder,
	       UnicastDiscoveryServer
{
    /**
     * Constructs a new instance.
     */
    public Server() {
    }

    // documentation inherited from DiscoveryFormatProvider
    public String getFormatName() {
	return "net.jini.discovery.plaintext";
    }
    
    // documentation inherited from MulticastRequestDecoder
    public MulticastRequest decodeMulticastRequest(
					    ByteBuffer buf,
					    InvocationConstraints constraints,
					    ClientSubjectChecker checker)
	throws IOException
    {
	Plaintext.checkConstraints(constraints);
	if (checker != null) {
	    checker.checkClientSubject(null);
	}
	return Plaintext.decodeMulticastRequest(buf);
    }

    // documentation inherited from MulticastAnnouncementEncoder
    public void encodeMulticastAnnouncement(MulticastAnnouncement announcement,
					    DatagramBufferFactory bufs,
					    InvocationConstraints constraints)
	throws IOException
    {
	Plaintext.checkConstraints(constraints);
	Plaintext.encodeMulticastAnnouncement(announcement, bufs);
    }

    // documentation inherited from UnicastDiscoveryServer
    public void checkUnicastDiscoveryConstraints(
					    InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	Plaintext.checkConstraints(constraints);
    }

    // documentation inherited from UnicastDiscoveryServer
    public void handleUnicastDiscovery(UnicastResponse response,
				       Socket socket,
				       InvocationConstraints constraints,
				       ClientSubjectChecker checker,
				       Collection context,
				       ByteBuffer received,
				       ByteBuffer sent)
	throws IOException
    {
	Plaintext.checkConstraints(constraints);
	if (checker != null) {
	    checker.checkClientSubject(null);
	}
	OutputStream out = new BufferedOutputStream(socket.getOutputStream());
	Plaintext.writeUnicastResponse(out, response, context);
	out.flush();
    }
}
