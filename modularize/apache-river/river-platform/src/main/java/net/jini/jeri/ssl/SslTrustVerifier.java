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

package net.jini.jeri.ssl;

import java.rmi.RemoteException;
import javax.net.SocketFactory;
import javax.security.auth.x500.X500Principal;
import net.jini.security.Security;
import net.jini.security.TrustVerifier;

/**
 * Trust verifier for the {@link SslEndpoint}, {@link HttpsEndpoint}, and
 * {@link ConfidentialityStrength} classes. Also trusts principals of type
 * {@link X500Principal}. This class is intended to be specified in a resource
 * to configure the operation of {@link Security#verifyObjectTrust
 * Security.verifyObjectTrust}.
 *
 * 
 * @since 2.0
 * @see SslEndpoint
 * @see HttpsEndpoint
 * @see ConfidentialityStrength
 */
public final class SslTrustVerifier implements TrustVerifier {

    /** Creates an instance of this class. */
    public SslTrustVerifier() { }

    /**
     * Returns <code>true</code> if the object is an instance of {@link
     * SslEndpoint} or {@link HttpsEndpoint}, and it's {@link SocketFactory} is
     * either <code>null</code> or trusted by the specified
     * <code>TrustVerifier.Context</code>; or if the object is an instance of
     * {@link ConfidentialityStrength} or {@link X500Principal}; and returns
     * <code>false</code> otherwise.
     *
     * @throws RemoteException if a communication-related exception occurs
     *	       when verifying a socket factory
     * @throws SecurityException if a security exception occurs when verifying
     *	       a socket factory
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	SocketFactory factory;
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	} else if (obj instanceof ConfidentialityStrength ||
		   obj instanceof X500Principal)
	{
	    return true;
	} else if (obj instanceof SslEndpoint) {
	    factory = ((SslEndpoint) obj).getSocketFactory();
	} else if (obj instanceof HttpsEndpoint) {
	    factory = ((HttpsEndpoint) obj).getSocketFactory();
	} else {
	    return false;
	}
	return factory == null || ctx.isTrustedObject(factory);
    }
}
