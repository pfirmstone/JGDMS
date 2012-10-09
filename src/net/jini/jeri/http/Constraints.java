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

package net.jini.jeri.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
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
import net.jini.core.constraint.RelativeTimeConstraint;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;

/**
 * Constraint support for this transport provider.
 *
 * This code makes some significant simplifying assumptions:
 *
 * - The transport layer aspects of all constraints supported by this
 *   provider are always satisfied by all open connections and
 *   requests.
 *
 * - No combination of individual constraints supported by this
 *   provider can contain conflicting constraints.
 *
 * 
 **/
class Constraints {

    /**
     * indicates that this provider does not support implementing (or
     * does not understand how to implement) the transport layer
     * aspects of satisfying a given constraint
     **/
    private static final int NO_SUPPORT = 0;

    /**
     * indicates that this provider supports implementing all aspects
     * of satisfying a given constraint
     **/
    private static final int FULL_SUPPORT = 1;

    /**
     * indicates that this provider supports implementing the
     * transport layer aspects of satisfying a given constraint, but
     * at least partial implementation by higher layers is also needed
     * in order to fully satisfy the constraint
     **/
    private static final int PARTIAL_SUPPORT = 2;

    /**
     * maps constraint values that are supported to Boolean indicating
     * whether or not they must be at least partially implemented by
     * higher layers to be fully satisfied
     **/
    private static final Map supportedValues = new HashMap();
    static {
	supportedValues.put(Integrity.NO,		Boolean.TRUE);
	supportedValues.put(Confidentiality.NO,		Boolean.FALSE);
	supportedValues.put(ClientAuthentication.NO,	Boolean.FALSE);
	supportedValues.put(ServerAuthentication.NO,	Boolean.FALSE);
	supportedValues.put(Delegation.NO,		Boolean.FALSE);
    }

    /**
     * maps constraint classes that are supported to Boolean
     * indicating whether or not such constraints must be at least
     * partially implemented by higher layers to be fully satisfied
     **/
    private static final Map supportedClasses = new HashMap();
    static {
	// ConstraintAlternatives is supported but handled specially in code
	supportedClasses.put(ConnectionAbsoluteTime.class,	Boolean.FALSE);
	supportedClasses.put(ConnectionRelativeTime.class,	Boolean.FALSE);
	/*
	 * The following classes are (trivially) supported just
	 * because ClientAuthentication.YES, ServerAuthentication.YES,
	 * and Delegation.YES are not supported.
	 */
	supportedClasses.put(ClientMaxPrincipal.class,		Boolean.FALSE);
	supportedClasses.put(ClientMaxPrincipalType.class,	Boolean.FALSE);
	supportedClasses.put(ClientMinPrincipal.class,		Boolean.FALSE);
	supportedClasses.put(ClientMinPrincipalType.class,	Boolean.FALSE);
	supportedClasses.put(ServerMinPrincipal.class,		Boolean.FALSE);
	supportedClasses.put(DelegationAbsoluteTime.class,	Boolean.FALSE);
	supportedClasses.put(DelegationRelativeTime.class,	Boolean.FALSE);
    }

    /**
     * Returns this provider's general support for the given
     * constraint.
     **/
    private static int getSupport(InvocationConstraint c) {
	Boolean support = (Boolean) supportedValues.get(c);
	if (support == null) {
	    support = (Boolean) supportedClasses.get(c.getClass());
	}
	return support == null ? NO_SUPPORT :
	    support.booleanValue() ? PARTIAL_SUPPORT : FULL_SUPPORT;
    }

    /**
     * Checks that we support at least the transport layer aspects of
     * the given requirements (and throws an
     * UnsupportedConstraintException if not), and returns the
     * requirements that must be at least partially implemented by
     * higher layers and the supported preferences that must be at
     * least partially implemented by higher layers.
     *
     * [If this provider supported constraints whose transport layer
     * aspects were not always satisfied by open connections or
     * requests, then we would need a variant of this method that
     * checks the given constraints against an open connection or
     * request.  If this provider supported constraints that could
     * conflict with each other then (when not checking against an
     * open connection or request) we would need to check for possible
     * conflicts.]
     **/
    static InvocationConstraints check(InvocationConstraints constraints,
				       boolean relativeOK)
	throws UnsupportedConstraintException
    {
	return distill(constraints, relativeOK).getUnfulfilledConstraints();
    }

    /**
     * Distills the given constraints to a form more directly usable
     * by this provider.  Throws an UnsupportedConstraintException if
     * we do not support at least the transport layer aspects of the
     * requirements.
     **/
    static Distilled distill(InvocationConstraints constraints,
			     boolean relativeOK)
	throws UnsupportedConstraintException
    {
	return new Distilled(constraints, relativeOK);
    }

    private Constraints() { throw new AssertionError(); }

    /**
     * A distillation of constraints to a form more directly usable by
     * this provider.
     **/
    static class Distilled {

	/**
	 * true if relative time constraints are allowed (in other
	 * words, not for client-side use)
	 */
	private final boolean relativeOK;

	private Collection unfulfilledRequirements = null; // lazily created
	private Collection unfulfilledPreferences = null; // lazily created

	private boolean hasConnectDeadline = false;
	private long connectDeadline;

	Distilled(InvocationConstraints constraints, boolean relativeOK)
	    throws UnsupportedConstraintException
	{
	    this.relativeOK = relativeOK;
	    for (Iterator i = constraints.requirements().iterator();
		 i.hasNext();)
	    {
		addConstraint((InvocationConstraint) i.next(), true);
	    }
	    for (Iterator i = constraints.preferences().iterator();
		 i.hasNext();)
	    {
		addConstraint((InvocationConstraint) i.next(), false);
	    }
	}

	/**
	 * Returns the requirements and supported preferences that
	 * must be at least partially implemented by higher layers.
	 **/
	InvocationConstraints getUnfulfilledConstraints() {
	    if (unfulfilledRequirements == null &&
		unfulfilledPreferences == null)
	    {
		return InvocationConstraints.EMPTY;
	    } else {
		return new InvocationConstraints(unfulfilledRequirements,
						 unfulfilledPreferences);
	    }
	}

	/**
	 * Returns true if a there is a socket connect deadline.
	 **/
	boolean hasConnectDeadline() {
	    return hasConnectDeadline;
	}

	/**
	 * Returns the absolute time of the socket connect deadline.
	 **/
	long getConnectDeadline() {
	    assert hasConnectDeadline;
	    return connectDeadline;
	}

	/**
	 * If "isRequirement" is true, throws an
	 * UnsupportedConstraintException if we do not support at
	 * least the transport layer aspects of the given constraint.
	 *
	 * If we do support at least the transport layer aspects of
	 * the given constraint, then if appropriate, adds it to the
	 * collection of requirements or preferences that must be at
	 * least partially implemented by higher layers.
	 **/
	private void addConstraint(InvocationConstraint constraint,
				   boolean isRequirement)
	    throws UnsupportedConstraintException
	{
	    if (!(constraint instanceof ConstraintAlternatives)) {
		int support = getSupport(constraint);
		if (support == NO_SUPPORT ||
		    (!relativeOK &&
		     constraint instanceof RelativeTimeConstraint))
		{
		    if (isRequirement) {
			throw new UnsupportedConstraintException(
			    "cannot satisfy constraint: " + constraint);
		    } else {
			return;
		    }
		}
		if (support == PARTIAL_SUPPORT) {
		    if (isRequirement) {
			if (unfulfilledRequirements == null) {
			    unfulfilledRequirements = new ArrayList();
			}
			unfulfilledRequirements.add(constraint);
		    } else {
			if (unfulfilledPreferences == null) {
			    unfulfilledPreferences = new ArrayList();
			}
			unfulfilledPreferences.add(constraint);
		    }
		}
		if (constraint instanceof ConnectionAbsoluteTime) {
		    // REMIND: only bother with this on client side?
		    addConnectDeadline(
			((ConnectionAbsoluteTime) constraint).getTime());
		}
	    } else {
		addAlternatives((ConstraintAlternatives) constraint,
				isRequirement);
	    }
	}

	/**
	 * If "isRequirement" is true, throws an
	 * UnsupportedConstraintException if we do not support at
	 * least the transport layer aspects of at least one of the
	 * constraints in the given alternatives.
	 *
	 * If we do support at least the transport layer aspects of at
	 * least one of the constraints in the given alternatives,
	 * then if appropriate, adds a ConstraintAlternatives of the
	 * supported alternatives to the collection of requirements or
	 * preferences that must be at least partially implemented by
	 * higher layers.
	 *
	 * If all of the supported alternatives need at least partial
	 * implementation by higher layers, then adds a
	 * ConstraintAlternatives with all of the supported
	 * alternatives to the unfulfilled collection or preferences,
	 * because higher layers must support at least one of them.
	 * But if at least one of the supported alternatives can be
	 * fully satisfied by the transport layer, then add nothing to
	 * the unfulfilled collection, because it is possible that
	 * higher layers need not support any of them (and there is no
	 * way to express no constraint).
	 *
	 * The weakest connect deadline (with no deadline being the
	 * the weakest possibility) is chosen among alternatives.
	 **/
	private void addAlternatives(ConstraintAlternatives constraint,
				     boolean isRequirement)
	    throws UnsupportedConstraintException
	{
	    Collection alts = constraint.elements();
	    boolean supported = false;
	    long maxConnectDeadline = Long.MIN_VALUE;
	    Collection unfulfilledAlts = null; // lazily created
	    boolean forgetUnfulfilled = false;
	    for (Iterator i = alts.iterator(); i.hasNext();) {
		InvocationConstraint c = (InvocationConstraint) i.next();

		// nested ConstraintAlternatives not allowed
		int support = getSupport(c);
		if (support == NO_SUPPORT ||
		    (!relativeOK && c instanceof RelativeTimeConstraint))
		{
		    continue;
		}
		supported = true;	// we support at least one
		if (!forgetUnfulfilled) {
		    if (support == PARTIAL_SUPPORT) {
			if (unfulfilledAlts == null) {
			    unfulfilledAlts = new ArrayList();
			}
			unfulfilledAlts.add(c);
		    } else {
			assert support == FULL_SUPPORT;
			unfulfilledAlts = null;
			forgetUnfulfilled = true;
		    }
		}
		if (c instanceof ConnectionAbsoluteTime) {
		    assert support == FULL_SUPPORT; // else more care required
		    maxConnectDeadline =
			Math.max(maxConnectDeadline,
				 ((ConnectionAbsoluteTime) c).getTime());
		} else {
		    maxConnectDeadline = Long.MAX_VALUE;
		}
	    }
	    if (!supported) {
		if (isRequirement) {
		    throw new UnsupportedConstraintException(
			"cannot satisfy constraint: " + constraint);
		} else {
		    return; // maxConnectDeadline is bogus in this case
		}
	    }
	    if (!forgetUnfulfilled && unfulfilledAlts != null) {
		if (isRequirement) {
		    if (unfulfilledRequirements == null) {
			unfulfilledRequirements = new ArrayList();
		    }
		    unfulfilledRequirements.add(
			ConstraintAlternatives.create(unfulfilledAlts));
		} else {
		    if (unfulfilledPreferences == null) {
			unfulfilledPreferences = new ArrayList();
		    }
		    unfulfilledPreferences.add(
			ConstraintAlternatives.create(unfulfilledAlts));
		}
	    }
	    if (maxConnectDeadline < Long.MAX_VALUE) {
		assert maxConnectDeadline != Long.MIN_VALUE;
		addConnectDeadline(maxConnectDeadline);
	    }
	}

	/**
	 * Adds the given connect deadline to this object's state.
	 * The earliest connect deadline is what gets remembered.
	 **/
	private void addConnectDeadline(long deadline) {
	    if (!hasConnectDeadline) {
		hasConnectDeadline = true;
		connectDeadline = deadline;
	    } else {
		connectDeadline = Math.min(connectDeadline, deadline);
	    }
	}
    }
}
