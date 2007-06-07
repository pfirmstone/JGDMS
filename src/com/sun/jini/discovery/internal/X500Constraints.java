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

package com.sun.jini.discovery.internal;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;

/**
 * Processes constraints specified to net.jini.discovery.x500.* discovery
 * format providers.
 */
class X500Constraints {

    private static final Object SUPPORTED = new Object();
    private static final Object UNSUPPORTED = new Object();

    private static final Set supportedRequestConstraints;
    private static final Set supportedAnnouncementConstraints;
    static {
	Collection baseSupported = Arrays.asList(new Object[]{
	    Confidentiality.NO,
	    Delegation.NO,
	    DelegationAbsoluteTime.class,
	    DelegationRelativeTime.class,
	    Integrity.YES
	});

	supportedRequestConstraints = new HashSet(baseSupported);
	supportedRequestConstraints.add(ClientAuthentication.YES);
	supportedRequestConstraints.add(ServerAuthentication.NO);

	supportedAnnouncementConstraints = new HashSet(baseSupported);
	supportedAnnouncementConstraints.add(ClientAuthentication.NO);
	supportedAnnouncementConstraints.add(ServerAuthentication.YES);
    }

    private static final Set principalConstraints = new HashSet();
    static {
	principalConstraints.add(ClientMaxPrincipal.class);
	principalConstraints.add(ClientMaxPrincipalType.class);
	principalConstraints.add(ClientMinPrincipal.class);
	principalConstraints.add(ClientMinPrincipalType.class);
	principalConstraints.add(ServerMinPrincipal.class);
    }

    private final InvocationConstraints distilled;

    private X500Constraints(InvocationConstraints distilled) {
	this.distilled = distilled;
    }

    /**
     * Returns X500Constraints instance representing the processed constraints.
     * If request is true, the constraints apply to multicast requests;
     * otherwise, they apply to a multicast announcements.  Throws an
     * UnsupportedConstraintException if the constraints are unfulfillable
     * (note that a successful return does not imply that the constraints are
     * necessarily fulfillable).
     */
    static X500Constraints process(InvocationConstraints constraints,
				   boolean request)
	throws UnsupportedConstraintException
    {
	if (constraints == null) {
	    constraints = InvocationConstraints.EMPTY;
	}
	return new X500Constraints(new InvocationConstraints(
	    distill(constraints.requirements(), request, true),
	    distill(constraints.preferences(), request, false)));
    }

    /**
     * Checks the given client principal against the constraints represented by
     * this instance, returning the number of preferences satisfied, or -1 if
     * the constraint requirements are not satisfied by the principal.
     */
    int checkClientPrincipal(X500Principal principal) {
	for (Iterator i = distilled.requirements().iterator(); i.hasNext(); ) {
	    if (!clientPrincipalSatisfies(
		    principal, (InvocationConstraint) i.next()))
	    {
		return -1;
	    }
	}
	int satisfied = 0;
	for (Iterator i = distilled.preferences().iterator(); i.hasNext(); ) {
	    if (clientPrincipalSatisfies(
		    principal, (InvocationConstraint) i.next()))
	    {
		satisfied++;
	    }
	}
	return satisfied;
    }

    /**
     * Checks the given server principal against the constraints represented by
     * this instance, returning the number of preferences satisfied, or -1 if
     * the constraint requirements are not satisfied by the principal.
     */
    int checkServerPrincipal(X500Principal principal) {
	for (Iterator i = distilled.requirements().iterator(); i.hasNext(); ) {
	    if (!serverPrincipalSatisfies(
		    principal, (InvocationConstraint) i.next()))
	    {
		return -1;
	    }
	}
	int satisfied = 0;
	for (Iterator i = distilled.preferences().iterator(); i.hasNext(); ) {
	    if (serverPrincipalSatisfies(
		    principal, (InvocationConstraint) i.next()))
	    {
		satisfied++;
	    }
	}
	return satisfied;
    }

    /**
     * Returns principal-dependent constraints distilled from the given set of
     * overall constraints.  If request is true, the given constraints apply to
     * multicast requests; otherwise, they apply to multicast announcements.
     * If required is true, then an UnsupportedConstraintException is thrown if
     * an unsupported constraint is encountered; otherwise, unsupported
     * constraints are ignored.
     */
    private static Collection distill(Set constraints,
				      boolean request,
				      boolean required)
	throws UnsupportedConstraintException
    {
	Collection dist = new ArrayList();
	for (Iterator i = constraints.iterator(); i.hasNext(); ) {
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    Object d = distill(c, request);
	    if (d instanceof InvocationConstraint) {
		dist.add(d);
	    } else if (required && d == UNSUPPORTED) {
		throw new UnsupportedConstraintException("unsupported: " + c);
	    }
	}
	return dist;
    }

    /**
     * Returns distilled (principal-dependent) constraint, or
     * SUPPORTED/UNSUPPORTED if the constraint is unconditionally supported or
     * not supported.  If request is true, the given constraint applies to
     * multicast requests; otherwise, it applies to multicast announcements.
     */
    private static Object distill(InvocationConstraint constraint,
				  boolean request)
    {
	Class cl = constraint.getClass();
	Set supported = request ?
	    supportedRequestConstraints : supportedAnnouncementConstraints;
	if (supported.contains(constraint) || supported.contains(cl)) {
	    return SUPPORTED;
	}
	if (principalConstraints.contains(cl)) {
	    return constraint;
	}
	if (constraint instanceof ConstraintAlternatives) {
	    ConstraintAlternatives ca = (ConstraintAlternatives) constraint;
	    Collection dist = new ArrayList();
	    for (Iterator i = ca.elements().iterator(); i.hasNext(); ) {
		Object d = distill((InvocationConstraint) i.next(), request);
		if (d == SUPPORTED) {
		    return SUPPORTED;
		} else if (d instanceof InvocationConstraint) {
		    dist.add(d);
		}
	    }
	    return dist.isEmpty() ?
		UNSUPPORTED : ConstraintAlternatives.create(dist);
	}
	return UNSUPPORTED;
    }

    /**
     * Returns true if the specified client principal satisfies the given
     * constraint; returns false otherwise.
     */
    private static boolean clientPrincipalSatisfies(
					    X500Principal principal,
					    InvocationConstraint constraint)
    {
	if (constraint instanceof ClientMaxPrincipal) {
	    return ((ClientMaxPrincipal) constraint).elements().contains(
		principal);
	}
	if (constraint instanceof ClientMaxPrincipalType) {
	    Set s = ((ClientMaxPrincipalType) constraint).elements();
	    for (Iterator i = s.iterator(); i.hasNext(); ) {
		if (((Class) i.next()).isInstance(principal)) {
		    return true;
		}
	    }
	    return false;
	}
	if (constraint instanceof ClientMinPrincipal) {
	    Set s = ((ClientMinPrincipal) constraint).elements();
	    return s.size() == 1 && s.contains(principal);
	}
	if (constraint instanceof ClientMinPrincipalType) {
	    Set s = ((ClientMinPrincipalType) constraint).elements();
	    for (Iterator i = s.iterator(); i.hasNext(); ) {
		if (!((Class) i.next()).isInstance(principal)) {
		    return false;
		}
	    }
	    return true;
	}
	if (constraint instanceof ConstraintAlternatives) {
	    Set s = ((ConstraintAlternatives) constraint).elements();
	    for (Iterator i = s.iterator(); i.hasNext(); ) {
		if (clientPrincipalSatisfies(
			principal, (InvocationConstraint) i.next()))
		{
		    return true;
		}
	    }
	    return false;
	}
	/*
	 * Server principal constraints vacuously satisfied, since distilling
	 * constraints with request == true would have rejected
	 * ServerAuthentication.YES.
	 */
	return true;
    }

    /**
     * Returns true if the specified server principal satisfies the given
     * constraint; returns false otherwise.
     */
    private static boolean serverPrincipalSatisfies(
					    X500Principal principal,
					    InvocationConstraint constraint)
    {
	if (constraint instanceof ServerMinPrincipal) {
	    Set s = ((ServerMinPrincipal) constraint).elements();
	    return s.size() == 1 && s.contains(principal);
	}
	if (constraint instanceof ConstraintAlternatives) {
	    Set s = ((ConstraintAlternatives) constraint).elements();
	    for (Iterator i = s.iterator(); i.hasNext(); ) {
		if (serverPrincipalSatisfies(
			principal, (InvocationConstraint) i.next()))
		{
		    return true;
		}
	    }
	    return false;
	}
	/*
	 * Client principal constraints vacuously satisfied, since distilling
	 * constraints with request == false would have rejected
	 * ClientAuthentication.YES.
	 */
	return true;
    }
}
