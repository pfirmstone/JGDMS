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

import java.rmi.RemoteException;
import net.jini.security.TrustVerifier;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;

/**
 * Trust verifier for instances of the constraint classes defined in the {@link
 * org.apache.river.discovery} package.  This class is intended to be specified in
 * a resource to configure the operation of {@link
 * net.jini.security.Security#verifyObjectTrust Security.verifyObjectTrust}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@Requirement(
	namespace="osgi.extender",
	filter="(osgi.extender=osgi.serviceloader.registrar)")
@Capability(
        namespace="osgi.serviceloader",
	name="net.jini.security.TrustVerifier")
public class DiscoveryConstraintTrustVerifier implements TrustVerifier {

    /**
     * Creates an instance.
     */
    public DiscoveryConstraintTrustVerifier() {
    }

    /**
     * Returns <code>true</code> if the specified object is known to be trusted
     * to correctly implement its contract; returns <code>false</code>
     * otherwise.  Returns <code>true</code> if the object is an instance of
     * any of the following classes, and returns <code>false</code> otherwise:
     * <ul>
     * <li> {@link DiscoveryProtocolVersion}
     * <li> {@link MulticastMaxPacketSize}
     * <li> {@link MulticastTimeToLive}
     * <li> {@link UnicastSocketTimeout}
     * </ul>
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	}
	return obj instanceof DiscoveryProtocolVersion ||
	       obj instanceof MulticastMaxPacketSize ||
	       obj instanceof MulticastTimeToLive ||
	       obj instanceof UnicastSocketTimeout;
    }
}
