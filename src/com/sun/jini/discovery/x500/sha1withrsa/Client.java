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

package com.sun.jini.discovery.x500.sha1withrsa;

import com.sun.jini.discovery.DatagramBufferFactory;
import com.sun.jini.discovery.DelayedMulticastAnnouncementDecoder;
import com.sun.jini.discovery.MulticastAnnouncement;
import com.sun.jini.discovery.MulticastRequest;
import com.sun.jini.discovery.MulticastRequestEncoder;
import com.sun.jini.discovery.internal.X500Client;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.jini.core.constraint.InvocationConstraints;

/**
 * Implements the client side of the
 * <code>net.jini.discovery.x500.SHA1withRSA</code> format.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class Client
    implements DelayedMulticastAnnouncementDecoder,
	       MulticastRequestEncoder
{
    
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.
    private final ClientImpl impl;

    /**
     * Constructs a new instance.
     */
    public Client() {
	impl = new ClientImpl();
    }

    // Inherit javadoc from DiscoveryFormatProvider
    public String getFormatName() {
	return impl.getFormatName();
    }

    // Inherit javadoc from MulticastRequestEncoder
    public void encodeMulticastRequest(
		    MulticastRequest request,
		    DatagramBufferFactory bufs,
		    InvocationConstraints constraints)
	throws IOException
    {
	impl.encodeMulticastRequest(request, bufs, constraints);
    }

    // Inherit javadoc from MulticastAnnouncementDecoder
    public MulticastAnnouncement decodeMulticastAnnouncement(
				    ByteBuffer buf,
				    InvocationConstraints constraints)
	throws IOException
    {
	return impl.decodeMulticastAnnouncement(buf, constraints);
    }
    
    // Inherit javadoc from DelayedMulticastAnnouncementDecoder
    public MulticastAnnouncement decodeMulticastAnnouncement(
				    ByteBuffer buf,
				    InvocationConstraints constraints,
				    boolean delayConstraintCheck)
	throws IOException 
    {
	return impl.decodeMulticastAnnouncement(buf,
						constraints,
						delayConstraintCheck);
    }
    
    private static final class ClientImpl extends X500Client {
	ClientImpl() {
	    super(Constants.FORMAT_NAME,
		  Constants.SIGNATURE_ALGORITHM,
		  Constants.MAX_SIGNATURE_LEN,
		  Constants.KEY_ALGORITHM,
		  Constants.KEY_ALGORITHM_OID);
	}
    }
}
