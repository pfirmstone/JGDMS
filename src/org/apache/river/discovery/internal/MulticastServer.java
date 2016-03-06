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
import org.apache.river.discovery.ClientSubjectChecker;
import org.apache.river.discovery.DatagramBufferFactory;
import org.apache.river.discovery.DelayedMulticastRequestDecoder;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.discovery.MulticastAnnouncementEncoder;
import org.apache.river.discovery.MulticastRequest;

/**
 *
 * @author peter
 */
public abstract class MulticastServer implements DelayedMulticastRequestDecoder,
	       MulticastAnnouncementEncoder 
{
    // Internal implementation. We dont want to expose the internal base
    // classes to the outside.
    private final X500Server impl;
   
    /**
     * Constructs a new instance.
     * @param impl
     */
    protected MulticastServer(X500Server impl) {
	this.impl = impl;
    }

    // inherit javadoc from DiscoveryFormatProvider
    @Override
    public String getFormatName() {
	return impl.getFormatName();
    }

    // inherit javadoc from MulticastAnnouncementEncoder
    @Override
    public void encodeMulticastAnnouncement(
		    MulticastAnnouncement announcement,
		    DatagramBufferFactory bufs,
		    InvocationConstraints constraints)
	throws IOException {
	    impl.encodeMulticastAnnouncement(announcement, bufs, constraints);
    }

    // inherit javadoc from MulticastRequestDecoder
    @Override
    public MulticastRequest decodeMulticastRequest(
				ByteBuffer buf,
				InvocationConstraints constraints, 
				ClientSubjectChecker checker)
	throws IOException {
	    return impl.decodeMulticastRequest(buf, constraints, checker);
    }

    // inherit javadoc from DelayedMulticastRequestDecoder
    @Override
    public MulticastRequest decodeMulticastRequest(
				ByteBuffer buf,
				InvocationConstraints constraints,
				ClientSubjectChecker checker,
				boolean delayConstraintCheck)
	throws IOException {
	    return impl.decodeMulticastRequest(
			    buf, constraints, checker, delayConstraintCheck);
    }

}
