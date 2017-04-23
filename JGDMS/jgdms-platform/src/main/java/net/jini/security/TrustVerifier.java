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

package net.jini.security;

import java.rmi.RemoteException;
import java.util.Collection;

/**
 * Defines the interface for trust verifiers used by
 * {@link Security#verifyObjectTrust Security.verifyObjectTrust}, allowing
 * the objects that are trusted to be extended.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface TrustVerifier {
    /**
     * Returns <code>true</code> if the specified object is known to be
     * trusted to correctly implement its contract; returns <code>false</code>
     * otherwise.
     *
     * @param obj the object in which to verify trust
     * @param ctx the trust verifier context, to aid in verification of
     * the specified object and its components
     * @return <code>true</code> if the specified object is known to be
     * trusted to correctly implement its contract; <code>false</code>
     * otherwise
     * @throws RemoteException if a communication-related exception occurs
     * @throws SecurityException if a security exception occurs
     * @throws NullPointerException if any argument is <code>null</code>
     */
    boolean isTrustedObject(Object obj, Context ctx)
	throws RemoteException;

    /**
     * Defines the context for trust verification used by
     * {@link TrustVerifier} instances and {@link Security#verifyObjectTrust
     * Security.verifyObjectTrust}. A context contains an ordered list of
     * {@link TrustVerifier} instances, a class loader, and a collection of
     * other context objects typically provided by the caller of
     * <code>Security.verifyObjectTrust</code>.
     *
     * @since 2.0
     */
    public interface Context {
	/**
	 * Returns <code>true</code> if the specified object is trusted to
	 * correctly implement its contract; returns <code>false</code>
	 * otherwise.
	 * <p>
	 * If the specified object is <code>null</code>, this method returns
	 * <code>true</code>. Otherwise, the
	 * {@link TrustVerifier#isTrustedObject isTrustedObject} method of
	 * each verifier contained in this context is called (in order) with
	 * the specified object and this context. If any verifier call returns
	 * <code>true</code>, the object is trusted and this method returns
	 * <code>true</code>. If all of the verifier calls return
	 * <code>false</code>, this method returns <code>false</code>.
	 * If one or more verifier calls throw a <code>RemoteException</code>
	 * or <code>SecurityException</code>, the last such exception is
	 * thrown to the caller (unless some verifier call returns
	 * <code>true</code>).
	 *
	 * @param obj the object in which to verify trust
	 * @return <code>true</code> if the specified object is trusted to
	 * correctly implements its contract; <code>false</code> otherwise
	 * @throws RemoteException if a communication-related exception occurs
	 * @throws SecurityException if a security exception occurs
	 */
	boolean isTrustedObject(Object obj) throws RemoteException;

	/**
	 * Returns the class loader that can be used as a basis for trust
	 * verification. In particular, classes and resources reachable from
	 * this class loader can be assumed to be trustworthy. A
	 * <code>null</code> value is interpreted to mean the current context
	 * class loader.
	 *
	 * @return the class loader that can be used as a basis for trust
	 * verification
	 */
	ClassLoader getClassLoader();

	/**
	 * Returns a collection of context objects for use by trust verifiers.
	 * The meaning of an element in this collection is determined by its
	 * type. As a specific example, a
	 * {@link net.jini.core.constraint.MethodConstraints}
	 * instance could be used to specify client constraints for any remote
	 * calls that trust verifiers might need to perform.
	 *
	 * @return a collection of context objects for use by trust verifiers
	 */
	Collection getCallerContext();
    }
}
