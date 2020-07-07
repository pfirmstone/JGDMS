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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.security.auth.Subject;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.connection.OutboundRequestHandle;

/**
 * Records information needed to make a remote call.  The information consists
 * of the endpoint, client subject, requirement for client authentication,
 * permitted client and server principals, cipher suites, and integrity and
 * connection time constraints.
 *
 * 
 */
class CallContext extends Utilities implements OutboundRequestHandle {

    /** The endpoint. */
    final Endpoint endpoint;

    /** The associated endpoint implementation delegate. */
    final SslEndpointImpl endpointImpl;

    /** The client subject. */
    final Subject clientSubject;

    /** Whether client authentication is required. */
    final boolean clientAuthRequired;

    /**
     * The principals in the client subject that may be used for
     * authentication, or null if the client is anonymous.
     */
    final Set clientPrincipals;

    /**
     * The principals that will be accepted for authentication of the server
     * subject, or null if all principals are permitted or if the server is
     * anonymous.
     */
    final Set serverPrincipals;

    /** An ordered list of cipher suites to use. */
    final String[] cipherSuites;

    /** Whether codebase integrity is required. */
    final boolean integrityRequired;

    /** Whether codebase integrity is preferred. */
    final boolean integrityPreferred;
    
    /** Whether deserialization atomic input validation is required. */
    final boolean atomicityRequired;

    /** Whether deserialization atomic input validation is preferred. */
    final boolean atomicityPreferred;

    /**
     * The absolute time by which a new connection must be completed, or
     * Long.MAX_VALUE for no restriction.
     */
    final long connectionTime;

    /**
     * Converts an OutboundRequestHandle intended for the specified endpoint
     * into a CallContext.  Throws NullPointerException if the handle is null.
     * Throws IllegalArgumentException if the instance is not associated with
     * the endpoint.
     */
    static CallContext coerce(OutboundRequestHandle handle, Endpoint endpoint) {
	if (handle == null) {
	    throw new NullPointerException("Handle cannot be null");
	} else if (!(handle instanceof CallContext)) {
	    throw new IllegalArgumentException(
		"Handle must be of type CallContext: " + handle);
	}
	CallContext context = (CallContext) handle;
	if (!endpoint.equals(context.endpoint)) {
	    throw new IllegalArgumentException(
		"Handle has wrong endpoint -- was " + context.endpoint +
		", should be " + endpoint);
	}
	return context;
    }

    /** Creates a CallContext. */
    CallContext(Endpoint endpoint,
		SslEndpointImpl endpointImpl,
		Subject clientSubject,
		boolean clientAuthRequired,
		Set clientPrincipals,
		Set serverPrincipals,
		List cipherSuites,
		boolean integrityRequired,
		boolean integrityPreferred,
		boolean atomicityRequired,
		boolean atomicityPreferred,
		long connectionTime)
    {
	this.endpoint = endpoint;
	this.endpointImpl = endpointImpl;
	this.clientSubject = clientSubject;
	this.clientAuthRequired = clientAuthRequired;
	this.clientPrincipals = clientPrincipals;
	this.serverPrincipals = serverPrincipals;
	this.cipherSuites =
	    (String[]) cipherSuites.toArray(new String[cipherSuites.size()]);
	this.integrityRequired = integrityRequired;
	this.integrityPreferred = integrityPreferred;
	this.atomicityRequired = atomicityRequired;
	this.atomicityPreferred = atomicityPreferred;
	this.connectionTime = connectionTime;
    }
    
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof CallContext)) return false;
        CallContext that = (CallContext) o;
        if (!clientAuthRequired == that.clientAuthRequired) return false;
        if (!integrityRequired == that.integrityRequired) return false;
        if (!integrityPreferred == that.integrityPreferred) return false;
        if (!atomicityRequired == that.atomicityRequired) return false;
        if (!atomicityPreferred == that.atomicityPreferred) return false;
        if (!(connectionTime == that.connectionTime)) return false;
        if (!Objects.equals(endpoint, that.endpoint)) return false;
        if (!Objects.equals(endpointImpl, that.endpointImpl)) return false;
        if (!Objects.equals(clientSubject, that.clientSubject)) return false;
        if (!Objects.equals(clientPrincipals, that.clientPrincipals)) return false;
        if (!Objects.equals(serverPrincipals, that.serverPrincipals)) return false;
        return Objects.deepEquals(cipherSuites, that.cipherSuites);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.endpoint);
        hash = 71 * hash + Objects.hashCode(this.endpointImpl);
        hash = 71 * hash + Objects.hashCode(this.clientSubject);
        hash = 71 * hash + (this.clientAuthRequired ? 1 : 0);
        hash = 71 * hash + Objects.hashCode(this.clientPrincipals);
        hash = 71 * hash + Objects.hashCode(this.serverPrincipals);
        hash = 71 * hash + Arrays.deepHashCode(this.cipherSuites);
        hash = 71 * hash + (this.integrityRequired ? 1 : 0);
        hash = 71 * hash + (this.integrityPreferred ? 1 : 0);
        hash = 71 * hash + (this.atomicityRequired ? 1 : 0);
        hash = 71 * hash + (this.atomicityPreferred ? 1 : 0);
        hash = 71 * hash + (int) (this.connectionTime ^ (this.connectionTime >>> 32));
        return hash;
    }

    @Override
    public String toString() {
	StringBuilder buff = new StringBuilder("CallContext[");
	buff.append("\n  ").append(endpoint);
	buff.append("\n  clientSubject=");
	if (clientSubject == null) {
	    buff.append("null");
	} else {
	    buff.append("Subject@");
	    buff.append(
		Integer.toHexString(
		    System.identityHashCode(clientSubject)));
	}
	buff.append("\n  clientAuthRequired=").append(clientAuthRequired);
	buff.append("\n  clientPrincipals=").append(clientPrincipals);
	buff.append("\n  serverPrincipals=").append(serverPrincipals);
	buff.append("\n  cipherSuites=").append(toString(cipherSuites));
	if (integrityRequired) {
	    buff.append("\n  integrity=required");
	} else if (integrityPreferred) {
	    buff.append("\n  integrity=preferred");
	}
	if (atomicityRequired) {
	    buff.append("\n  deserialization input validation failure atomicity=required");
	} else if (atomicityPreferred) {
	    buff.append("\n  deserialization input validation failure atomicity=preferred");
	}
	if (connectionTime != Long.MAX_VALUE) {
	    buff.append("\n  connectionTime=").append(connectionTime);
	}
	buff.append("\n]");
	return buff.toString();
    }

    /**
     * Returns any constraints that must be partially or fully implemented by
     * higher layers for this outbound request.
     */
    InvocationConstraints getUnfulfilledConstraints() {
	List<InvocationConstraint> required = new ArrayList<InvocationConstraint>(2);
	List<InvocationConstraint> preferred = new ArrayList<InvocationConstraint>(2);
	if (integrityRequired) {
	    required.add(Integrity.YES);
	} else if (integrityPreferred) {
	    preferred.add(Integrity.YES);
	}
	if (atomicityRequired){
	    required.add(AtomicInputValidation.YES);
	} else if (atomicityPreferred){
	    preferred.add(AtomicInputValidation.YES);   
	}
	if (!required.isEmpty() || !preferred.isEmpty()){
	    return new InvocationConstraints(required, preferred);
	}
	return InvocationConstraints.EMPTY;
    }
}
