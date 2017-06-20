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
package com.sun.jini.landlord;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
public final class LandlordProxyVerifier implements Serializable, TrustVerifier {

    private static final long serialVersionUID = 1L;

    /** 
     * The canonical instance of the server reference. This
     * instance will be used by the <code>isTrusted</code> method 
     * as the known trusted object used to determine whether or not a
     * given proxy is equivalent in trust, content, and function.
     *
     * @serial
     */
    private final RemoteMethodControl landlord;

    /**
     * The <code>Uuid</code> associated <code>landlord</code>.
     * @serial
     */
    private final Uuid landlordUuid;
    
    private transient org.apache.river.landlord.LandlordProxyVerifier lpv;

    public LandlordProxyVerifier(Landlord landlord, Uuid landlordUuid) {
        lpv = new org.apache.river.landlord.LandlordProxyVerifier(landlord, landlordUuid);
        this.landlord = (RemoteMethodControl) landlord;
        this.landlordUuid = landlordUuid;
    }
    
    
    @Override
    public boolean isTrustedObject(Object obj, Context ctx) throws RemoteException {
        return lpv.isTrustedObject(obj, ctx);
    }
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (landlord == null)
	    throw new InvalidObjectException("null landlord reference");

	if (landlordUuid == null)
	    throw new InvalidObjectException("null landlordUuid reference");

	if (!(landlord instanceof TrustEquivalence)) {
	    throw new InvalidObjectException(
		"server does not implement TrustEquivalence");
	}
        lpv = new org.apache.river.landlord.LandlordProxyVerifier((Landlord) landlord, landlordUuid);
    }
    
}
