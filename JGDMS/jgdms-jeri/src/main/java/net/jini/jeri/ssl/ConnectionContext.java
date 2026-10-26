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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.AtomicInputValidation;
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
import net.jini.core.constraint.MarshallingFormat;
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
    
    /** Constraints supported with deserialization atomic input validation */
    private static final long ATOMICITY = -2;

    /** Constraints supported with codebase integrity */
    private static final long INTEGRITY = -3;

    /** Constraints not supported */
    private static final long NOT_SUPPORTED = -4;

    /**
     * Constraint deferred to the invocation layer: a MarshallingFormat. The
     * transport does NOT implement it and must NEVER claim to satisfy it
     * (return OK) -- doing so would skip the invocation-layer check
     * (BasicInvocationHandler.requireMarshallingFormat /
     * BasicInvocationDispatcher.verifyAndStripMarshallingFormat) and let an
     * Atomic-JOSS codec bypass a required ATOMIC_DER format (JGDMS-STD-008
     * sec.18.3). The actual constraint object is carried up (marshallingRequired
     * / marshallingPreferred) rather than a boolean, because MarshallingFormat is
     * not a singleton -- it carries a format string that the invocation layer
     * must see verbatim.
     */
    private static final long MARSHALLING = -5;

    /** The ClientMinPrincipalType supported by the provider. */
    private static final ClientMinPrincipalType clientMinPrincipalType =
	new ClientMinPrincipalType(X500Principal.class);

    /** The cipher suite */
    final String cipherSuite;

    /** The client principal, or null for an anonymous client */
    final Principal client;

    /** The server principal, or null for an anonymous server */
    final Principal server;

    /** Whether codebase integrity and atomicity should be enforced */
    private final boolean upperLayerConstraints;

    /**
     * Whether the connection is being considered on the client side, which
     * does not support relative time constraints.
     */
    private final boolean clientSide;

    /** Set to true if the principals and cipher suite perform authentication */
    private final boolean supported;

    /** Whether the requirements specify Integrity.YES */
    private boolean integrityRequired;

    /** Whether the preferences specify Integrity.YES */
    private boolean integrityPreferred;
    
    /** Whether the requirements specify AtomicInputValidation.YES */
    private boolean atomicityRequired;

    /** Whether the preferences specify AtomicInputValidation.YES */
    private boolean atomicityPreferred;

    /**
     * The MarshallingFormat constraints required by the requirements, deferred
     * to the invocation layer. Never empty implies "at least one MarshallingFormat
     * requirement present" (parallel to integrityRequired).
     */
    private final Set<MarshallingFormat> marshallingRequired =
	new LinkedHashSet<MarshallingFormat>(1);

    /** The MarshallingFormat constraints from the preferences. */
    private final Set<MarshallingFormat> marshallingPreferred =
	new LinkedHashSet<MarshallingFormat>(1);

    /** The absolute connection time, or Long.MAX_VALUE if not specified */
    private long connectionTime = Long.MAX_VALUE;

    /** The number of preferences satisfied */
    private int preferences;

    /**
     * Creates an instance that represents using the specified cipher suite,
     * client and server principals, whether to guarantee codebase integrity,
     * input validation failure atomicity during deserialization
     * and constraints.  Null values for the principals mean they are
     * anonymous.  Non-X.500 principals are permitted to allow specifying a
     * dummy principal if the principal is unknown.  Returns null if the
     * constraints are not supported.
     */
    static ConnectionContext getInstance(
	    String cipherSuite, 
	    Principal client, 
	    Principal server,
	    boolean upperLayerConstraints, 
	    boolean clientSide, 
	    InvocationConstraints constraints)
    {
	ConnectionContext context = new ConnectionContext(
	    cipherSuite, client, server, upperLayerConstraints, clientSide);
	return context.supported(constraints) ? context : null;
                        }

    /** Creates an instance of this class. */
    private ConnectionContext(String cipherSuite,
				Principal client,
				Principal server,
				boolean upperLayerConstraints,
				boolean clientSide)
    {
	this.cipherSuite = cipherSuite;
	this.client = client;
	this.server = server;
	this.upperLayerConstraints = upperLayerConstraints;
	this.clientSide = clientSide;
        boolean serverAuth = doesServerAuthentication(cipherSuite);
        supported = !(!serverAuth || server == null || client == null);
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
	if (atomicityRequired) {
	    sb.append(", input validation failure atomicity: required");
	} else if (atomicityPreferred) {
	    sb.append(", input validation failure atomicity: preferred");
	}
	if (!marshallingRequired.isEmpty()) {
	    sb.append(", marshallingFormat required: ").append(marshallingRequired);
	}
	if (!marshallingPreferred.isEmpty()) {
	    sb.append(", marshallingFormat preferred: ").append(marshallingPreferred);
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
    
     /** Returns whether atomicity is required. */
    boolean getAtomicityRequired() {
	return atomicityRequired;
    }

    /** Returns whether atomicity is preferred. */
    boolean getAtomicityPreferred() {
	return atomicityPreferred;
    }

    /**
     * Returns the required MarshallingFormat constraints deferred to the
     * invocation layer (verbatim, preserving each format), possibly empty.
     */
    Set<MarshallingFormat> getMarshallingRequired() {
	return Collections.unmodifiableSet(marshallingRequired);
    }

    /**
     * Returns the preferred MarshallingFormat constraints deferred to the
     * invocation layer, possibly empty.
     */
    Set<MarshallingFormat> getMarshallingPreferred() {
	return Collections.unmodifiableSet(marshallingPreferred);
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
	if (!supported) {
	    return false;
	}
	for (Iterator i = constraints.requirements().iterator(); i.hasNext(); )
	{
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    long r = supported(c);
	    if (r == NOT_SUPPORTED) {
		return false;
	    }
	    if (r == INTEGRITY) {
		integrityRequired = true;
	    } else if (r == ATOMICITY){
		atomicityRequired = true;
	    } else if (r == MARSHALLING){
		// A MARSHALLING verdict comes only from a direct MarshallingFormat
		// constraint (never via ConstraintAlternatives -- see supported
		// (ConstraintAlternatives)), so this cast is safe. Carry the actual
		// constraint up; the invocation layer enforces the format.
		marshallingRequired.add((MarshallingFormat) c);
	    } else if (connectionTime > r) {
		connectionTime = r;
	    }
	}
	for (Iterator i = constraints.preferences().iterator(); i.hasNext(); ) {
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    long r = supported(c);
	    if (r == NOT_SUPPORTED) {
	      continue;
	    }
	    preferences++;
	    if (r == INTEGRITY) {
		if (!integrityRequired) {
		    integrityPreferred = true;
		}
	    } else if (r == ATOMICITY){
		if (!atomicityRequired){
		    atomicityPreferred = true;
		}
	    } else if (r == MARSHALLING){
		// A preferred MarshallingFormat: carry it up as a preference. The
		// invocation layer only ENFORCES requirements, so a preferred format
		// that the codec cannot satisfy is simply not applied.
		marshallingPreferred.add((MarshallingFormat) c);
	    } else if (connectionTime > r) {
		connectionTime = r;
	    }
	}
	if (upperLayerConstraints){
	    // A lone MarshallingFormat requirement/preference must keep the
	    // upper-layer context usable, exactly like a lone Integrity/Atomicity.
	    if (!integrityRequired && !integrityPreferred && !atomicityRequired
		    && !atomicityPreferred && marshallingRequired.isEmpty()
		    && marshallingPreferred.isEmpty()) return false;
	}
	return true;
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
	    return upperLayerConstraints && constraint == Integrity.YES
		? INTEGRITY : NOT_SUPPORTED;
	} else if (constraint instanceof AtomicInputValidation){
	    return upperLayerConstraints && constraint == AtomicInputValidation.YES
		? ATOMICITY : NOT_SUPPORTED;
	} else if (constraint instanceof MarshallingFormat) {
	    /*
	     * DEFER to the invocation layer (JGDMS-STD-008 sec.18.3). NEVER return
	     * OK here: OK would tell the endpoint the transport satisfies the
	     * format, so the invocation-layer check that rejects a mismatched codec
	     * (e.g. Atomic JOSS vs required ATOMIC_DER) would be skipped -- the
	     * exact hole to avoid. Gate on upperLayerConstraints exactly like
	     * Integrity/AtomicInputValidation.
	     */
	    return upperLayerConstraints ? MARSHALLING : NOT_SUPPORTED;
	} else if (constraint instanceof Confidentiality) {
	    return ok(doesEncryption(cipherSuite) ==
		      (constraint == Confidentiality.YES));
	} else if (constraint instanceof ConfidentialityStrength) {
            return ok((constraint == ConfidentialityStrength.WEAK && 
                        doesEncryption(cipherSuite) && 
                        !hasStrongKeyCipherAlgorithms(cipherSuite))
                    ||(constraint == ConfidentialityStrength.STRONG && 
                        hasStrongKeyCipherAlgorithms(cipherSuite)));
	} else if (constraint instanceof ClientAuthentication) {
	    return ok((constraint == ClientAuthentication.YES));
	} else if (constraint instanceof ClientMinPrincipalType) {
	    return ok(constraint.equals(clientMinPrincipalType));
	} else if (constraint instanceof ClientMaxPrincipalType) {
	    return ok(((ClientMaxPrincipalType) constraint).elements().contains(
			  X500Principal.class));
	} else if (constraint instanceof ClientMinPrincipal) {
	    Set elements = ((ClientMinPrincipal) constraint).elements();
	    return ok(elements.size() == 1 && elements.contains(client));
	} else if (constraint instanceof ClientMaxPrincipal) {
	    return ok(((ClientMaxPrincipal) constraint).elements().contains(client));
	} else if (constraint instanceof Delegation) {
	    return ok((constraint == Delegation.NO));
	} else if (constraint instanceof DelegationAbsoluteTime) {
	    return OK;
	} else if (constraint instanceof DelegationRelativeTime) {
	    return ok(!clientSide);
	} else if (constraint instanceof ServerAuthentication) {
	    return ok((constraint == ServerAuthentication.YES));
	} else if (constraint instanceof ServerMinPrincipal) {
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
	    if (r == MARSHALLING) {
		/*
		 * A MarshallingFormat inside a ConstraintAlternatives cannot be
		 * safely deferred through the single-format invocation-layer check
		 * (which enforces one required format, not a choice). Refusing here
		 * (NOT_SUPPORTED) is fail-closed; returning OK would be a JOSS-bypass
		 * hole. No JGDMS DER proxy uses MarshallingFormat in alternatives.
		 */
		return NOT_SUPPORTED;
	    }
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
    
    @Override
    public int hashCode() {
	int hash = 7;
	hash = 17 * hash + (this.cipherSuite != null ? this.cipherSuite.hashCode() : 0);
	hash = 17 * hash + (this.client != null ? this.client.hashCode() : 0);
	hash = 17 * hash + (this.server != null ? this.server.hashCode() : 0);
	hash = 17 * hash + (this.upperLayerConstraints ? 1 : 0);
	hash = 17 * hash + (this.clientSide ? 1 : 0);
	hash = 17 * hash + (this.supported ? 1 : 0);
	hash = 17 * hash + (this.integrityRequired ? 1 : 0);
	hash = 17 * hash + (this.integrityPreferred ? 1 : 0);
	hash = 17 * hash + (this.atomicityRequired ? 1 : 0);
	hash = 17 * hash + (this.atomicityPreferred ? 1 : 0);
	hash = 17 * hash + this.marshallingRequired.hashCode();
	hash = 17 * hash + this.marshallingPreferred.hashCode();
	hash = 17 * hash + (int) (this.connectionTime ^ (this.connectionTime >>> 32));
	hash = 17 * hash + this.preferences;
	return hash;
    }
    
    @Override
    public boolean equals(Object o){
	if (!(o instanceof ConnectionContext)) return false;
	ConnectionContext that = (ConnectionContext) o;
	if (this.atomicityPreferred != that.atomicityPreferred) return false;
	if (this.atomicityRequired != that.atomicityRequired) return false;
	if (!this.marshallingRequired.equals(that.marshallingRequired)) return false;
	if (!this.marshallingPreferred.equals(that.marshallingPreferred)) return false;
	if (this.clientSide != that.clientSide) return false;
	if (this.integrityPreferred != that.integrityPreferred) return false;
	if (this.integrityRequired != that.integrityRequired) return false;
	if (this.supported != that.supported) return false;
	if (this.upperLayerConstraints != that.upperLayerConstraints) return false;
	if (this.connectionTime != that.connectionTime) return false;
	if (this.preferences != that.preferences) return false;
	if (!this.cipherSuite.equals(that.cipherSuite)) return false;
	if (!this.client.equals(that.client)) return false;
	return this.server.equals(that.server);
    }
}
