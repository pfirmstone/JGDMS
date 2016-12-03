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

import java.security.Principal;
import java.util.Iterator;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;

/**
 * Records information about a connection used for a remote call and determines
 * whether the connection could support specific constraints. <p>
 *
 * Does not support heterogeneous constraint alternatives.  As a result,
 * callers can be assured that the choice of possible principals or of
 * Integrity.YES is independent of the suites, insuring that those items can be
 * picked before negotiating the cipher suite.
 *
 * 
 */
final class ConnectionContext extends Utilities {

    /** Constraints supported, without integrity or connection timeout */
    private static final long OK = Long.MAX_VALUE;

    /** Constraints supported with codebase integrity */
    private static final long INTEGRITY = -3;

    /** Constraints not supported */
    private static final long NOT_SUPPORTED = -4;

    /** The ClientMinPrincipalType supported by the provider. */
    private static final ClientMinPrincipalType clientMinPrincipalType =
	new ClientMinPrincipalType(X500Principal.class);

    /** The cipher suite */
    final String cipherSuite;

    /** The client principal, or null for an anonymous client */
    final Principal client;

    /** The server principal, or null for an anonymous server */
    final Principal server;

    /** Whether codebase integrity should be enforced */
    private final boolean integrity;

    /**
     * Whether the connection is being considered on the client side, which
     * does not support relative time constraints.
     */
    private final boolean clientSide;

    /** Set to true if the principals and cipher suite conflict */
    private boolean notSupported;

    /** Whether the requirements specify Integrity.YES */
    private boolean integrityRequired;

    /** Whether the preferences specify Integrity.YES */
    private boolean integrityPreferred;

    /** The absolute connection time, or Long.MAX_VALUE if not specified */
    private long connectionTime = Long.MAX_VALUE;

    /** The number of preferences satisfied */
    private int preferences;

    /**
     * Creates an instance that represents using the specified cipher suite,
     * client and server principals, whether to guarantee codebase integrity,
     * and constraints.  Null values for the principals mean they are
     * anonymous.  Non-X.500 principals are permitted to allow specifying a
     * dummy principal if the principal is unknown.  Returns null if the
     * constraints are not supported.
     */
    static ConnectionContext getInstance(String cipherSuite,
					 Principal client,
					 Principal server,
					 boolean integrity,
					 boolean clientSide,
					 InvocationConstraints constraints)
    {
	ConnectionContext context = new ConnectionContext(
	    cipherSuite, client, server, integrity, clientSide);
	return context.supported(constraints) ? context : null;
    }

    /** Creates an instance of this class. */
    private ConnectionContext(String cipherSuite,
			      Principal client,
			      Principal server,
			      boolean integrity,
			      boolean clientSide)
    {
	this.cipherSuite = cipherSuite;
	this.client = client;
	this.server = server;
	this.integrity = integrity;
	this.clientSide = clientSide;
	boolean serverAuth = doesServerAuthentication(cipherSuite);
	if (serverAuth != (server != null)
	    || (client != null && server == null))
	{
	    notSupported = true;
	}
    }

    public String toString() {
	StringBuffer sb = new StringBuffer("ConnectionContext[");
	fieldsToString(sb);
	sb.append("]");
	return sb.toString();
    }

    void fieldsToString(StringBuffer sb) {
	sb.append(cipherSuite);
	if (client != null) {
	    sb.append(", client: ").append(client);
	}
	if (server != null) {
	    sb.append(", server: ").append(server);
	}
	if (integrityRequired) {
	    sb.append(", integrity: required");
	} else if (integrityPreferred) {
	    sb.append(", integrity: preferred");
	}
	if (connectionTime != Long.MAX_VALUE) {
	    sb.append(", connectionTime = ").append(connectionTime);
	}
	sb.append(", preferences: ").append(preferences);
    }

    /** Returns whether integrity is required. */
    boolean getIntegrityRequired() {
	return integrityRequired;
    }

    /** Returns whether integrity is preferred. */
    boolean getIntegrityPreferred() {
	return integrityPreferred;
    }

    /**
     * Returns the absolute time when the connection should be completed, or
     * Long.MAX_VALUE for no limit.
     */
    long getConnectionTime() {
	return connectionTime;
    }

    /** Returns the number of preferences that can be satisfied. */
    int getPreferences() {
	return preferences;
    }

    /**
     * Checks if the specified constraints are supported, computing
     * integrityRequired, integrityPreferred, connectionTime and preferences as
     * a side effect.
     */
    private boolean supported(InvocationConstraints constraints) {
	if (notSupported) {
	    return false;
	}
	for (Iterator i = constraints.requirements().iterator(); i.hasNext(); )
	{
	    long r = supported((InvocationConstraint) i.next());
	    if (r == NOT_SUPPORTED) {
		return false;
	    }
	    if (r == INTEGRITY) {
		integrityRequired = true;
	    } else if (connectionTime > r) {
		connectionTime = r;
	    }
	}
	for (Iterator i = constraints.preferences().iterator(); i.hasNext(); ) {
	    long r = supported((InvocationConstraint) i.next());
	    if (r == NOT_SUPPORTED) {
	      continue;
	    }
	    preferences++;
	    if (r == INTEGRITY) {
		if (!integrityRequired) {
		    integrityPreferred = true;
		}
	    } else if (connectionTime > r) {
		connectionTime = r;
	    }
	}
	if (integrity && !integrityRequired && !integrityPreferred) {
	    return false;
	} else {
	    return true;
	}
    }

    /**
     * Checks if the constraint is supported, returning NOT_SUPPORTED if it is
     * not supported, INTEGRITY if the constraint is Integrity.YES or
     * constraint alternatives with elements of type Integrity, the connection
     * time if the constraint is an instance of ConnectionAbsoluteTime or
     * constraint alternatives of them, and otherwise OK.
     */
    private long supported(InvocationConstraint constraint) {
	if (constraint instanceof ConstraintAlternatives) {
	    return supported((ConstraintAlternatives) constraint);
	} else if (constraint instanceof Integrity) {
	    return integrity && constraint == Integrity.YES
		? INTEGRITY : NOT_SUPPORTED;
	} else if (constraint instanceof Confidentiality) {
	    return ok(doesEncryption(cipherSuite) ==
		      (constraint == Confidentiality.YES));
	} else if (constraint instanceof ConfidentialityStrength) {
	    return ok(!doesEncryption(cipherSuite) ||
		      (hasStrongKeyCipherAlgorithms(cipherSuite)
		       == (constraint == ConfidentialityStrength.STRONG)));
	} else if (constraint instanceof ClientAuthentication) {
	    return ok((client == null) ==
		      (constraint == ClientAuthentication.NO));
	} else if (constraint instanceof ClientMinPrincipalType) {
	    return ok(client == null ||
		      constraint.equals(clientMinPrincipalType));
	} else if (constraint instanceof ClientMaxPrincipalType) {
	    return ok(client == null ||
		      ((ClientMaxPrincipalType) constraint).elements().contains(
			  X500Principal.class));
	} else if (constraint instanceof ClientMinPrincipal) {
	    if (client == null) {
		return OK;
	    } 
	    Set elements = ((ClientMinPrincipal) constraint).elements();
	    return ok(elements.size() == 1 && elements.contains(client));
	} else if (constraint instanceof ClientMaxPrincipal) {
	    return ok(client == null ||
		      ((ClientMaxPrincipal) constraint).elements().contains(
			  client));
	} else if (constraint instanceof Delegation) {
	    return ok(client == null || (constraint == Delegation.NO));
	} else if (constraint instanceof DelegationAbsoluteTime) {
	    return OK;
	} else if (constraint instanceof DelegationRelativeTime) {
	    return ok(!clientSide);
	} else if (constraint instanceof ServerAuthentication) {
	    return ok((server == null) ==
		      (constraint == ServerAuthentication.NO));
	} else if (constraint instanceof ServerMinPrincipal) {
	    if (server == null) {
		return OK;
	    }
	    Set elements = ((ServerMinPrincipal) constraint).elements();
	    return ok(elements.size() == 1 && elements.contains(server));
	} else if (constraint instanceof ConnectionAbsoluteTime) {
	    return Math.max(((ConnectionAbsoluteTime) constraint).getTime(), 0);
	} else if (constraint instanceof ConnectionRelativeTime) {
	    return ok(!clientSide);
	} else {
	    return NOT_SUPPORTED;
	}
    }

    /** Returns OK if the argument is true, else NOT_SUPPORTED. */
    private static long ok(boolean ok) {
	return ok ? OK : NOT_SUPPORTED;
    }

    /**
     * Checks if the constraint alternatives are supported, returning
     * NOT_SUPPORTED if the elements have different types or none are
     * supported, INTEGRITY if the elements are instances of Integrity, the
     * largest connection time if the elements are instances of
     * ConnectionAbsoluteTime, and otherwise OK.
     */
    private long supported(ConstraintAlternatives constraint) {
	Set alts = constraint.elements();
	long connectionTime = -1;
	Class type = null;
	boolean supported = false;
	boolean integrity = false;
	for (Iterator i = alts.iterator(); i.hasNext(); ) {
	    InvocationConstraint alt = (InvocationConstraint) i.next();
	    if (type == null) {
		type = alt.getClass();
	    } else if (type != alt.getClass()) {
		return NOT_SUPPORTED;
	    }
	    long r = supported(alt);
	    if (r != NOT_SUPPORTED) {
		supported = true;
		if (r == INTEGRITY) {
		    integrity = true;
		} else if (r > connectionTime) {
		    connectionTime = r;
		}
	    }
	}
	if (!supported) {
	    return NOT_SUPPORTED;
	} else if (integrity) {
	    return INTEGRITY;
	} else if (connectionTime >= 0) {
	    return connectionTime;
	} else {
	    return OK;
	}
    }
}
