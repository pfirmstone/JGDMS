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

package org.apache.river.discovery.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.jini.core.constraint.InvocationConstraints;
import org.apache.river.discovery.DatagramBufferFactory;
import org.apache.river.discovery.DelayedMulticastAnnouncementDecoder;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastRequest;
import org.apache.river.discovery.MulticastRequestEncoder;

/**
 *
 * @author peter
 */
public abstract class MulticastClient implements DelayedMulticastAnnouncementDecoder,
	       MulticastRequestEncoder 
{
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.
    private final X500Client impl;

    /**
     * Constructs a new instance.
     * @param impl
     */
    protected MulticastClient(X500Client impl) {
	this.impl = impl;
    }

    // Inherit javadoc from DiscoveryFormatProvider
    @Override
    public String getFormatName() {
	return impl.getFormatName();
    }

    // Inherit javadoc from MulticastRequestEncoder
    @Override
    public void encodeMulticastRequest(
		    MulticastRequest request,
		    DatagramBufferFactory bufs,
		    InvocationConstraints constraints)
	throws IOException
    {
	impl.encodeMulticastRequest(request, bufs, constraints);
    }

    // Inherit javadoc from MulticastAnnouncementDecoder
    @Override
    public MulticastAnnouncement decodeMulticastAnnouncement(
				    ByteBuffer buf,
				    InvocationConstraints constraints)
	throws IOException
    {
	return impl.decodeMulticastAnnouncement(buf, constraints);
    }
    
    // Inherit javadoc from DelayedMulticastAnnouncementDecoder
    @Override
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
    
}
