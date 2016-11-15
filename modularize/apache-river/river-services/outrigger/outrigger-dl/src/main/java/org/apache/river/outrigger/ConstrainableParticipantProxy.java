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
package org.apache.river.outrigger;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Subclass of <code>ParticipantProxy</code> that implements
 * <code>RemoteMethodControl</code>
 *
 * @author Sun Microsystems, Inc.
 */
@AtomicSerial
final class ConstrainableParticipantProxy extends ParticipantProxy
    implements RemoteMethodControl
{
    private static final long serialVersionUID = 1L;

    /**
     * Create a new <code>ConstrainableParticipantProxy</code> for the given
     * space.
     * @param space The an inner proxy that implements 
     *              <code>TransactionParticipant</code> for the 
     *              space.
     * @param spaceUuid The universally unique ID for the
     *              space
     * @throws NullPointerException if <code>space</code> or
     *         <code>spaceUuid</code> is <code>null</code>.
     */
    ConstrainableParticipantProxy(TransactionParticipant space, Uuid spaceUuid,
				  MethodConstraints methodConstraints)
    {
	super(constrainServer(space, methodConstraints), spaceUuid);
    }

    ConstrainableParticipantProxy(GetArg arg) throws IOException {
	super(check(arg));
    }
    
    private static GetArg check(GetArg arg) throws IOException {
	ParticipantProxy pp = new ParticipantProxy(arg);
	
	/* Basic validation of space and spaceUuid, was performed by
	 * ParticipantProxy.readObject(), we just need to verify than
	 * space implements RemoteMethodControl.	
	 */

	if(!(pp.space instanceof RemoteMethodControl) ) {
	    throw new InvalidObjectException(
	        "space does not implement RemoteMethodControl");
	}
	return arg;
    }

    /**
     * Returns a copy of the server proxy with the specified client
     * constraints and methods mapping.
     */
    private static TransactionParticipant constrainServer(
        TransactionParticipant participant,
	MethodConstraints methodConstraints)
    {
	return (TransactionParticipant)
            ((RemoteMethodControl)participant).setConstraints(
	        methodConstraints);
    }

    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableParticipantProxy(space, spaceUuid,
						 constraints);
    }

    public MethodConstraints getConstraints() {
	return ((RemoteMethodControl)space).getConstraints();
    }
    
    /**
     * Returns a proxy trust iterator that is used in
     * <code>ProxyTrustVerifier</code> to retrieve this object's
     * trust verifier.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(space);
    }
	
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();

	/* Basic validation of space and spaceUuid, was performed by
	 * ParticipantProxy.readObject(), we just need to verify than
	 * space implements RemoteMethodControl.	
	 */

	if(!(space instanceof RemoteMethodControl) ) {
	    throw new InvalidObjectException(
	        "space does not implement RemoteMethodControl");
	}
    }
}
