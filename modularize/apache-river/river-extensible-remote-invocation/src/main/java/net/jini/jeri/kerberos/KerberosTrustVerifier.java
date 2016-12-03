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
package net.jini.jeri.kerberos;

import java.rmi.RemoteException;
import javax.net.SocketFactory;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.security.TrustVerifier;

/**
 * Trust verifier for verifying the Jini extensible remote
 * invocation (Jini ERI) endpoints of type {@link KerberosEndpoint},
 * and principals of type {@link KerberosPrincipal}. This class is
 * intended to be specified in a resource to configure the operation
 * of {@link net.jini.security.Security#verifyObjectTrust
 * Security.verifyObjectTrust}.
 *
 * 
 * @see KerberosEndpoint
 * @since 2.0
 */
public class KerberosTrustVerifier implements TrustVerifier {

    /**
     * Creates a <code>Security.TrustVerifier</code> for this package.
     */
    public KerberosTrustVerifier() {}

    /**
     * Returns <code>true</code> if the object is an instance of
     * {@link KerberosEndpoint} and the <code>SocketFactory</code> it
     * uses internally, if not <code>null</code>, is trusted by the
     * given <code>TrustVerifier.Context</code>, or the object is an
     * instance of {@link KerberosPrincipal}.  Returns
     * <code>false</code> otherwise.
     *
     * @throws RemoteException if a communication-related exception
     *         occurs when verifying a socket factory
     * @throws SecurityException if a security exception occurs when
     *	       verifying a socket factory
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null)
	    throw new NullPointerException("null argument encountered");

	if (obj instanceof KerberosEndpoint) {
	    SocketFactory csf =
		((KerberosEndpoint) obj).getSocketFactory();
	    if (csf == null) {
		return true;
	    } else {
		return ctx.isTrustedObject(csf);
	    }
	} else if (obj instanceof KerberosPrincipal) {
	    return true;
	}

	return false;
    }
}
