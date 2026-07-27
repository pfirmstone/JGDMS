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

package net.jini.jeri.uds;

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
import net.jini.core.constraint.MarshallingFormat;
import net.jini.core.constraint.RelativeTimeConstraint;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.io.UnsupportedConstraintException;

/**
 * Constraint support for the plaintext Unix domain socket (UDS) transport
 * provider.
 *
 * <p>This class is modelled directly on {@code net.jini.jeri.tcp.Constraints},
 * and shares its two simplifying assumptions:
 *
 * <ul>
 * <li>The transport-layer aspects of all constraints supported by this
 *     provider are always satisfied by all open connections and requests.
 * <li>No combination of individual supported constraints can conflict.
 * </ul>
 *
 * <h2>Security semantics (increment 1: byte-identical to TCP)</h2>
 *
 * For increment 1 this table is <em>byte-identical to
 * {@code net.jini.jeri.tcp.Constraints}</em>: the transport claims neither
 * {@link Integrity#YES} nor {@link Confidentiality#YES}.  Both are deferred, for
 * the same reason the {@code …Authentication.YES} variants are: the transport
 * cannot, from its own static self-description, verify the far end really is a
 * local, owner-only peer.
 *
 * <ul>
 * <li>{@link Integrity#YES} &mdash; <b>owned by the object layer, NOT claimed
 *     here.</b>  In JERI {@code Integrity.YES} (including its stream/codebase
 *     aspect) is satisfied by {@code BasicInvocationHandler}/{@code
 *     BasicInvocationDispatcher} + {@link net.jini.io.context.IntegrityEnforcement},
 *     realised by the atomic/DER codec ({@code AtomicILFactory}) which refuses
 *     the malleable, decode-executing JOSS format.  That layer already achieves
 *     full {@code Integrity.YES} over plaintext (exactly as for TCP), so this
 *     transport deliberately does <em>not</em> claim it: a transport
 *     FULL_SUPPORT claim would tell the object layer that decode-integrity is
 *     already handled and could <em>bypass</em> the DER/JOSS gate.  UDS locality
 *     is defence-in-depth for the transmission aspect, not a constraint claim;
 *     the integrity entries here are byte-identical to {@code net.jini.jeri.tcp}.
 * <li>{@link Confidentiality#YES} &mdash; <b>NOT claimed for increment 1.</b>
 *     Confidentiality-by-locality is real only for a socket that genuinely is a
 *     local, owner-only, kernel-mediated pipe, but this claim would be made
 *     <em>statically</em> &mdash; by the <em>client</em> endpoint, from a
 *     deserialized path, with no verification that the far end is local and
 *     owner-only, and it would stand even when the server's owner-only gate
 *     (see {@code UdsServerEndpoint.restrictPermissions}) could not be applied
 *     (e.g. a platform without an enforceable owner-only mode).  That is the
 *     same over-claim class as the {@code Integrity.YES} claim removed above:
 *     the transport asserting a security property it has not verified.  So for
 *     increment 1 UDS claims only {@link Confidentiality#NO}, byte-identical to
 *     plaintext TCP.  Confidentiality-by-locality is <b>deferred to
 *     increment&nbsp;2</b>, where {@code SO_PEERCRED} / SPIFFE-SVID
 *     cross-validation actually verifies the peer is local and owner-authorized
 *     (mirroring how {@link ClientAuthentication#YES} is deferred to the same
 *     increment); only then can the transport honestly claim it.
 * </ul>
 *
 * <p>It does <strong>not</strong> claim
 * {@link ServerAuthentication#YES}/{@link ClientAuthentication#YES}: with
 * {@code SO_PEERCRED} deferred (SOW &sect;2) this transport surfaces no peer
 * <em>principal</em>.  Local peer trust is provided out-of-band by the socket
 * file's filesystem permissions (owner / mode {@code 0700}), not by an
 * authenticated identity inside the JERI constraint model.  Accordingly, as
 * with TCP, only {@code ClientAuthentication.NO} / {@code ServerAuthentication.NO}
 * / {@code Delegation.NO} are supported here.
 *
 * <p>The earlier worry &mdash; that a caller relying on a transport
 * {@code Integrity.YES}/{@code Confidentiality.YES} claim would be implicitly
 * trusting that the endpoint really is a local UDS rather than a spoofed
 * serialized form pointing at an off-host tunnel &mdash; is precisely why both
 * are left off the increment-1 table.  A complementary per-endpoint local-peer
 * check (e.g. {@code SO_PEERCRED} or a SPIFFE-SVID handshake) is the separate,
 * future increment (see the SOW) that lets the transport honestly claim
 * confidentiality-by-locality.
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
	/*
	 * INTEGRITY: byte-identical to net.jini.jeri.tcp.Constraints.  In JERI,
	 * Integrity.YES (including its stream/codebase-integrity aspect) is owned
	 * by the OBJECT layer -- BasicInvocation{Handler,Dispatcher} +
	 * net.jini.io.context.IntegrityEnforcement, realised by the atomic/DER
	 * codec (AtomicILFactory) which refuses the malleable, decode-executing
	 * JOSS format.  That layer already achieves full Integrity.YES over
	 * plaintext (exactly as for TCP), so this transport deliberately does NOT
	 * claim Integrity.YES: a transport FULL_SUPPORT claim would signal the
	 * object layer that decode-integrity is already handled and could BYPASS
	 * the DER/JOSS gate.  UDS locality is defence-in-depth for the transmission
	 * aspect, not a constraint claim.
	 */
	supportedValues.put(Integrity.NO,		Boolean.TRUE);
	/*
	 * CONFIDENTIALITY: byte-identical to net.jini.jeri.tcp.Constraints for
	 * increment 1 -- only Confidentiality.NO is claimed.  A previous UDS-only
	 * entry claimed Confidentiality.YES as FULL_SUPPORT ("confidentiality by
	 * locality"), but that claim is made STATICALLY by the client endpoint from
	 * a deserialized path, with NO verification that the far end is a local,
	 * owner-only socket, and it stands even when the server-side owner-only gate
	 * (UdsServerEndpoint.restrictPermissions) could not be applied.  That is the
	 * same over-claim class as the removed Integrity.YES claim, so it is dropped:
	 * confidentiality-by-locality is deferred to increment 2, where SO_PEERCRED /
	 * SPIFFE-SVID cross-validation actually verifies the peer is local and
	 * owner-authorized (mirroring how ClientAuthentication.YES is deferred).
	 */
	supportedValues.put(Confidentiality.NO,		Boolean.FALSE);
	/*
	 * SO_PEERCRED deferred: no authenticated peer principal, so only the
	 * ".NO" variants are supported, exactly as TCP.
	 */
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
	/*
	 * MarshallingFormat is an invocation-layer constraint (JGDMS-STD-008 sec.18.3): the
	 * transport does not implement it, it DEFERS it to the invocation layer, satisfied in
	 * BasicInvocationHandler.requireMarshallingFormat() / BasicInvocationDispatcher.
	 * PARTIAL_SUPPORT (Boolean.TRUE) puts it in the unfulfilled-requirements set for the
	 * higher layer rather than throwing UnsupportedConstraintException at distill time.
	 * It MUST be deferred, never claimed as full support: the invocation-layer check is
	 * what rejects a mismatched format (e.g. an Atomic-JOSS codec against a required
	 * ATOMIC_DER), so claiming support here would let Atomic JOSS bypass the constraint.
	 * See docs/FINDING-JERI-MarshallingFormat-Plaintext-Transport-Gap.md (all 4 transports).
	 */
	supportedClasses.put(MarshallingFormat.class,		Boolean.TRUE);
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
