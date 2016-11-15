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

import java.util.List;
import java.util.Set;
import javax.security.auth.Subject;
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
	this.connectionTime = connectionTime;
    }

    public String toString() {
	StringBuffer buff = new StringBuffer("CallContext[");
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
	if (integrityRequired) {
	    return INTEGRITY_REQUIRED;
	} else if (integrityPreferred) {
	    return INTEGRITY_PREFERRED;
	} else {
	    return InvocationConstraints.EMPTY;
	}
    }
}
