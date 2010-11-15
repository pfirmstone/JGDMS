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

package com.sun.jini.discovery.x500.sha1withdsa;

import com.sun.jini.discovery.ClientSubjectChecker;
import com.sun.jini.discovery.DatagramBufferFactory;
import com.sun.jini.discovery.DelayedMulticastRequestDecoder;
import com.sun.jini.discovery.MulticastAnnouncement;
import com.sun.jini.discovery.MulticastAnnouncementEncoder;
import com.sun.jini.discovery.MulticastRequest;
import com.sun.jini.discovery.internal.X500Server;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.jini.core.constraint.InvocationConstraints;

/**
 * Implements the server side of the
 * <code>net.jini.discovery.x500.SHA1withDSA</code> format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Server
    implements DelayedMulticastRequestDecoder,
	       MulticastAnnouncementEncoder
{

    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.
    private final ServerImpl impl;
   
    /**
     * Constructs a new instance.
     */
    public Server() {
	impl = new ServerImpl();
    }

    // inherit javadoc from DiscoveryFormatProvider
    public String getFormatName() {
	return impl.getFormatName();
    }

    // inherit javadoc from MulticastAnnouncementEncoder
    public void encodeMulticastAnnouncement(
		    MulticastAnnouncement announcement,
		    DatagramBufferFactory bufs,
		    InvocationConstraints constraints)
	throws IOException {
	    impl.encodeMulticastAnnouncement(announcement, bufs, constraints);
    }

    // inherit javadoc from MulticastRequestDecoder
    public MulticastRequest decodeMulticastRequest(
				ByteBuffer buf,
				InvocationConstraints constraints, 
				ClientSubjectChecker checker)
	throws IOException {
	    return impl.decodeMulticastRequest(buf, constraints, checker);
    }

    // inherit javadoc from DelayedMulticastRequestDecoder
    public MulticastRequest decodeMulticastRequest(
				ByteBuffer buf,
				InvocationConstraints constraints,
				ClientSubjectChecker checker,
				boolean delayConstraintCheck)
	throws IOException {
	    return impl.decodeMulticastRequest(
			    buf, constraints, checker, delayConstraintCheck);
    }

    private static final class ServerImpl extends X500Server {
	ServerImpl() {
	    super(Constants.FORMAT_NAME,
		  Constants.SIGNATURE_ALGORITHM,
		  Constants.MAX_SIGNATURE_LEN,
		  Constants.KEY_ALGORITHM,
		  Constants.KEY_ALGORITHM_OID);
	}
    }
}
