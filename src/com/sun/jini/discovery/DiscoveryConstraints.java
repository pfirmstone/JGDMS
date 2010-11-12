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

package com.sun.jini.discovery;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.io.UnsupportedConstraintException;

/**
 * Class for processing constraints which apply to the discovery protocol:
 * {@link DiscoveryProtocolVersion}, {@link MulticastMaxPacketSize},
 * {@link MulticastTimeToLive}, {@link UnicastSocketTimeout},
 * {@link net.jini.core.constraint.ConnectionRelativeTime},
 * {@link net.jini.core.constraint.ConnectionAbsoluteTime}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class DiscoveryConstraints {

    /** Method object for the multicastRequest method of this class. */
    public static final Method multicastRequestMethod;
    /** Method object for the multicastAnnouncement method of this class. */
    public static final Method multicastAnnouncementMethod;
    /** Method object for the unicastDiscovery method of this class. */
    public static final Method unicastDiscoveryMethod;
    static {
	try {
	    multicastRequestMethod = DiscoveryConstraints.class.getMethod(
		"multicastRequest", null);
	    multicastAnnouncementMethod = DiscoveryConstraints.class.getMethod(
		"multicastAnnouncement", null);
	    unicastDiscoveryMethod = DiscoveryConstraints.class.getMethod(
		"unicastDiscovery", null);
	} catch (NoSuchMethodException e) {
	    throw new AssertionError(e);
	}
    }

    private static final Set supportedProtocols = new HashSet(2);
    static {
	supportedProtocols.add(DiscoveryProtocolVersion.ONE);
	supportedProtocols.add(DiscoveryProtocolVersion.TWO);
    }

    private final InvocationConstraints unfulfilled;
    private final Set protocolVersions;
    private final int preferredProtocolVersion;
    private final ConnectionAbsoluteTime connectionAbsoluteTime;
    private final MulticastMaxPacketSize maxPacketSize;
    private final MulticastTimeToLive timeToLive;
    private final UnicastSocketTimeout socketTimeout;
    private final int hashcode;

    @Override
    public int hashCode() {
	return hashcode;
    }
    
    @Override
    public boolean equals(Object o){
	if ( o == this ) return true;
	if ( o.hashCode() != hashcode) return false;
	if ( o instanceof DiscoveryConstraints) {
	    DiscoveryConstraints that = (DiscoveryConstraints) o;
	    if ( unfulfilled != null ) {
		if ( !unfulfilled.equals(that.unfulfilled) ) return false;
	    } else if ( unfulfilled != that.unfulfilled) return false;
	    if (  protocolVersions != null ) {
		if ( !protocolVersions.equals(that.protocolVersions) ) return false;
	    } else if ( protocolVersions!= that.protocolVersions) return false;
	    if (  connectionAbsoluteTime != null ) {
		if ( !connectionAbsoluteTime.equals(that.connectionAbsoluteTime) ) return false;
	    } else if ( connectionAbsoluteTime != that.connectionAbsoluteTime) return false;
	    if (preferredProtocolVersion != that.preferredProtocolVersion) return false;
	    if ( maxPacketSize  != null ) {
		if ( !maxPacketSize.equals(that.maxPacketSize) ) return false;
	    } else if ( maxPacketSize != that.maxPacketSize) return false;    
	    if (  timeToLive != null ) {
		if ( !timeToLive.equals(that.timeToLive) ) return false;
	    } else if ( timeToLive != that.timeToLive) return false;
	    if (  socketTimeout != null ) {
		if ( !socketTimeout.equals(that.socketTimeout) ) return false;
	    } else if ( socketTimeout != that.socketTimeout) return false;    		
	    return true;	    
	}
	return false;
    }

    /**
     * Empty method which serves as a {@link MethodConstraints} key for looking
     * up {@link InvocationConstraints} that apply to multicast requests.
     */
    public static void multicastRequest() {
    }

    /**
     * Empty method which serves as a {@link MethodConstraints} key for looking
     * up {@link InvocationConstraints} that apply to multicast announcements.
     */
    public static void multicastAnnouncement() {
    }

    /**
     * Empty method which serves as a {@link MethodConstraints} key for looking
     * up {@link InvocationConstraints} that apply to unicast discovery.
     */
    public static void unicastDiscovery() {
    }

    /**
     * Processes the discovery-related constraints in the given set of
     * constraints, returning a <code>DiscoveryConstraints</code> instance from
     * which the constraint results can be queried.  Processing of timeout
     * constraints is time dependent, and subsequent invocations of this
     * method with the same <code>InvocationConstraints</code> may result in
     * <code>DiscoveryConstraint</code> instances with different constraint
     * results.
     *
     * @param constraints the constraints to process
     * @return an instance representing processed constraints
     * @throws UnsupportedConstraintException if the discovery-related
     * constraints contain conflicts, or otherwise cannot be processed
     * @throws NullPointerException if <code>constraints</code> is
     * <code>null</code>
     *
     */
    public static DiscoveryConstraints process(
					   InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	return new DiscoveryConstraints(constraints);
    }

    private DiscoveryConstraints(InvocationConstraints constraints)
	throws UnsupportedConstraintException
    {
	unfulfilled = new InvocationConstraints(
	    getUnfulfilled(constraints.requirements()),
	    getUnfulfilled(constraints.preferences()));

	ConstraintReducer cr = 
	    new ConstraintReducer(DiscoveryProtocolVersion.class);
	protocolVersions = cr.reduce(
	    new InvocationConstraints(constraints.requirements(), null));
	if (!protocolVersions.isEmpty() &&
	    intersect(protocolVersions, supportedProtocols).isEmpty())
	{
	    throw new UnsupportedConstraintException(
		"no supported protocols: " + protocolVersions);
	}
	preferredProtocolVersion = chooseProtocolVersion(
	    protocolVersions, cr.reduce(constraints), unfulfilled);

	Set s = new MulticastMaxPacketSizeReducer().reduce(constraints);
	maxPacketSize = s.isEmpty() ? 
	    null : (MulticastMaxPacketSize) getElement(s);

	s = new ConstraintReducer(
	    MulticastTimeToLive.class).reduce(constraints);
	timeToLive = s.isEmpty() ? null : (MulticastTimeToLive) getElement(s);

	s = new ConstraintReducer(
	    UnicastSocketTimeout.class).reduce(constraints);
	socketTimeout = s.isEmpty() ? 
	    null : (UnicastSocketTimeout) getElement(s);
	
	InvocationConstraints absConstraints =
	    new InvocationConstraints(
		constraints.requirements(),
		constraints.preferences()).makeAbsolute();
	s = new ConnectionAbsoluteTimeReducer().reduce(absConstraints);
	
	connectionAbsoluteTime = s.isEmpty() ?
				    null :
				    (ConnectionAbsoluteTime) getElement(s);
	int hash = 7;
	hash = 41 * hash + (this.unfulfilled != null ? this.unfulfilled.hashCode() : 0);
	hash = 41 * hash + (this.protocolVersions != null ? this.protocolVersions.hashCode() : 0);
	hash = 41 * hash + this.preferredProtocolVersion;
	hash = 41 * hash + (this.connectionAbsoluteTime != null ? this.connectionAbsoluteTime.hashCode() : 0);
	hash = 41 * hash + (this.maxPacketSize != null ? this.maxPacketSize.hashCode() : 0);
	hash = 41 * hash + (this.timeToLive != null ? this.timeToLive.hashCode() : 0);
	hash = 41 * hash + (this.socketTimeout != null ? this.socketTimeout.hashCode() : 0);
	hashcode = hash;
    }

    /**
     * Returns the protocol version to use for sending multicast requests or
     * announcements, or initiating unicast discovery.
     *
     * @return the protocol version to use
     */
    public int chooseProtocolVersion() {
	return preferredProtocolVersion;
    }

    /**
     * Checks the protocol version of an incoming multicast request,
     * announcement, or unicast discovery attempt, throwing an {@link
     * UnsupportedConstraintException} if handling of the given protocol does
     * not satisfy the constraints of this instance.
     *
     * @param version protocol version to check
     * @throws UnsupportedConstraintException if handling of the given protocol
     * does not satisfy the constraints of this instance
     */
    public void checkProtocolVersion(int version)
	throws UnsupportedConstraintException
    {
	if (!(protocolVersions.isEmpty() ||
	      protocolVersions.contains(
		  DiscoveryProtocolVersion.getInstance(version))))
	{
	    throw new UnsupportedConstraintException(
		"disallowed protocol: " + version);
	}
    }
    
    /**
     * Returns the deadline by which a network connection must be established
     * during unicast discovery, or <code>defaultValue</code> if not
     * constrained.
     * 
     * @param defaultValue default timeout to return
     * @return the deadline for the network connection
     * @since 2.1
     */
    public long getConnectionDeadline(long defaultValue) {
	return connectionAbsoluteTime != null ? 
	    connectionAbsoluteTime.getTime() : defaultValue;
    }

    /**
     * Returns the maximum multicast packet size to allow, or the specified
     * default value if not constrained.
     *
     * @param defaultValue the value to return if the multicast packet size is
     * unconstrained
     * @return the maximum multicast packet size to allow
     */
    public int getMulticastMaxPacketSize(int defaultValue) {
	return (maxPacketSize != null) ? 
	    maxPacketSize.getSize() : defaultValue;
    }

    /**
     * Returns the multicast time to live value to use, or the specified
     * default value if not constrained.
     *
     * @param defaultValue the value to return if the multicast time to live
     * value is unconstrained
     * @return the multicast time to live value to use
     */
    public int getMulticastTimeToLive(int defaultValue) {
	return (timeToLive != null) ?
	    timeToLive.getTimeToLive() : defaultValue;
    }

    /**
     * Returns socket read timeout to use for unicast discovery, or the 
     * specified default value if not constrained.
     *
     * @param defaultValue the value to return if the socket timeout is
     * unconstrained
     * @return the socket timeout to use
     */
    public int getUnicastSocketTimeout(int defaultValue) {
	return (socketTimeout != null) ? 
	    socketTimeout.getTimeout() : defaultValue;
    }

    /**
     * Returns the constraints for this instance which are not, or do not
     * contain as alternatives, instances of the "fulfillable" (by this layer)
     * constraint types <code>DiscoveryProtocolVersion</code>, 
     * <code>ConnectionRelativeTime</code>, <code>MulticastMaxPacketSize</code>, 
     * <code>MulticastTimeToLive</code>, and <code>UnicastSocketTimeout</code>.  
     * Constraint alternatives containing both fulfillable and unfulfillable 
     * constraints are treated optimistically--it is assumed that the one of the 
     * fulfillable alternatives will be satisfied, so the unfulfillable 
     * alternatives are not included in the returned constraints.
     *
     * @return the unfulfilled constraints for this instance
     */
    public InvocationConstraints getUnfulfilledConstraints() {
	return unfulfilled;
    }

    /**
     * Utility for reducing constraints of a given type into a base set of
     * alternatives.  For each type of constraints that it reduces, this class
     * makes the simplifying assumption that failure to reduce constraints of
     * that type into a single (perhaps one element) set of alternatives
     * represents a constraint conflict--i.e., that two disjoint instances of
     * the constraint type cannot be applied to the same operation.  While this
     * restriction does not hold across all possible constraints, it is
     * satisfied by the particular constraint types that DiscoveryConstraints
     * handles.
     *
     * If this utility encounters a set of alternatives containing instances of
     * both the type to reduce as well as other constraint types, then it
     * ignores the instances of the other types, treating the set as if it can
     * only be satisfied by the alternatives of the targeted type.  This may in
     * some cases cause false positives when detecting conflicts in constraints
     * containing alternatives of mixed type; however, it should never result
     * in false negatives.
     */
    private static class ConstraintReducer {

	private final Class targetClass;

	/**
	 * Creates reducer that operates on instances of the given constraint
	 * class.
	 */
	ConstraintReducer(Class targetClass) {
	    this.targetClass = targetClass;
	}
	
	/**
	 * Returns the reduction of the given constraints into a single set of
	 * alternatives for the target class.  Returns an empty set if no
	 * constraints of the target class are specified.  Throws
	 * UnsupportedConstraintException if the constraints conflict.
	 */
	Set reduce(InvocationConstraints constraints)
	    throws UnsupportedConstraintException
	{
	    Set reduced = reduce(null, constraints.requirements(), true);
	    reduced = reduce(reduced, constraints.preferences(), false);
	    return (reduced != null) ? reduced : Collections.EMPTY_SET;
	}

	/**
	 * Returns the reduction (intersection and compaction) of a new set of
	 * alternative constraints, all instances of the target class, with a
	 * previously reduced set (null if no other constraints have been
	 * reduced yet).  Returns an empty set if elements in the sets
	 * conflict.  This method can be overridden by subclasses for
	 * constraints with particular reduction semantics; the default
	 * implementation of this method returns the intersection of the two
	 * sets.
	 */
	Set reduce0(Set reduced, Set toReduce) {
	    return (reduced != null) ? intersect(reduced, toReduce) : toReduce;
	}

	private Set reduce(Set reduced, Set constraints, boolean required)
	    throws UnsupportedConstraintException
	{
	    for (Iterator i = constraints.iterator(); i.hasNext(); ) {
		InvocationConstraint c = (InvocationConstraint) i.next();

		Set toReduce = Collections.EMPTY_SET;
		if (targetClass.isInstance(c)) {
		    toReduce = Collections.singleton(c);
		} else if (c instanceof ConstraintAlternatives) {
		    toReduce = getTargetInstances(
			((ConstraintAlternatives) c).elements());
		}
		if (!toReduce.isEmpty()) {
		    Set s = reduce0(reduced, toReduce);
		    if (!s.isEmpty()) {
			reduced = s;
		    } else if (required) {
			throw new UnsupportedConstraintException(
			    "constraints conflict: " + constraints);
		    }
		}
	    }
	    return reduced;
	}

	private Set getTargetInstances(Set set) {
	    Set instances = Collections.EMPTY_SET;
	    for (Iterator i = set.iterator(); i.hasNext(); ) {
		Object obj = i.next();
		if (targetClass.isInstance(obj)) {
		    if (instances.isEmpty()) {
			instances = new HashSet();
		    }
		    instances.add(obj);
		}
	    }
	    return instances;
	}
    }

    /*
     * Abstract class which reduces constraints that are specified to have
     * a max value. Subclasses only have to handle returning the value of the
     * constraint and creating a constraint given an input value.
     */
    private abstract static class MaxValueReducer 
	extends ConstraintReducer
    {
	MaxValueReducer(Class targetClass) {
	    super(targetClass);
	}

	abstract long getValue(InvocationConstraint ic);
	abstract InvocationConstraint getConstraintInstance(long value);
	
	/*
	 * Reduces the alternatives in toReduce to the maximum value and then
	 * chooses the minimum between this value and the one from reduced.
	 * Invokes abstract method getValue to allow subclasses to return the
	 * value for specific constraint classes and invokes
	 * getConstraintInstance to finally return the actual instance given
	 * the reduced value that has been determined as above.
	 */
	Set reduce0(Set reduced, Set toReduce) {
	    long value = 0;
	    for (Iterator i = toReduce.iterator(); i.hasNext(); ) {
		value = Math.max(
		    value, getValue((InvocationConstraint) i.next()));
	    }
	    if (reduced != null) {
		value = Math.min(
		    value,
		    getValue((InvocationConstraint) getElement(reduced)));
	    }
	    return Collections.singleton(getConstraintInstance(value));
	}
    }
    
    private static class MulticastMaxPacketSizeReducer 
	extends MaxValueReducer
    {
	MulticastMaxPacketSizeReducer() {
	    super(MulticastMaxPacketSize.class);
	}
	
	long getValue(InvocationConstraint maxPacketSize) {
	    return ((MulticastMaxPacketSize) maxPacketSize).getSize();
	}
	
	InvocationConstraint getConstraintInstance(long value) {
	    if (value > Integer.MAX_VALUE) {
		// Shouldnt really happen as we are only dealing with
		// MulticasatMaxPacketSize constraints which are int
		throw new AssertionError("Value too large " + value);
	    }
	    return new MulticastMaxPacketSize((int)value);
	}
    }
        
    private static class ConnectionAbsoluteTimeReducer extends MaxValueReducer {
	ConnectionAbsoluteTimeReducer() {
	    super(ConnectionAbsoluteTime.class);
	}
	
	long getValue(InvocationConstraint absTime) {
	    return ((ConnectionAbsoluteTime) absTime).getTime();
	}
	
	InvocationConstraint getConstraintInstance(long value) {
	    return new ConnectionAbsoluteTime(value);
	}
    }

    private static Set getUnfulfilled(Set constraints) {
	Set unfulfilled = new HashSet(constraints.size());
	for (Iterator i = constraints.iterator(); i.hasNext(); ) {
	    InvocationConstraint c = (InvocationConstraint) i.next();
	    if (c instanceof ConstraintAlternatives) {
		Set s = ((ConstraintAlternatives) c).elements();
		Set u = getUnfulfilled(s);
		if (u.size() == s.size()) {
		    unfulfilled.add(c);
		}
	    } else if (!(c instanceof DiscoveryProtocolVersion ||
			 c instanceof MulticastMaxPacketSize ||
			 c instanceof MulticastTimeToLive ||
			 c instanceof UnicastSocketTimeout ||
			 c instanceof ConnectionAbsoluteTime ||
			 c instanceof ConnectionRelativeTime))
	    {
		unfulfilled.add(c);
	    }
	}
	return unfulfilled;
    }

    private static int chooseProtocolVersion(Set protocolVersions,
					     Set protocolVersionPrefs,
					     InvocationConstraints unfulfilled)
    {
	DiscoveryProtocolVersion bias = unfulfilled.isEmpty() ? 
	    DiscoveryProtocolVersion.ONE : DiscoveryProtocolVersion.TWO;
	Set[] sets = { protocolVersionPrefs, protocolVersions };
	for (int i = 0; i < sets.length; i++) {
	    Set s = sets[i];
	    if (s.contains(bias)) {
		return bias.getVersion();
	    }
	    if (!(s = intersect(s, supportedProtocols)).isEmpty()) {
		return ((DiscoveryProtocolVersion) getElement(s)).getVersion();
	    }
	}
	return bias.getVersion();
    }

    private static Set intersect(Set s1, Set s2) {
	Set intersection = Collections.EMPTY_SET;
	for (Iterator i = s1.iterator(); i.hasNext(); ) {
	    Object obj = i.next();
	    if (s2.contains(obj)) {
		if (intersection.isEmpty()) {
		    intersection = new HashSet();
		}
		intersection.add(obj);
	    }
	}
	return intersection;
    }

    private static Object getElement(Set s) {
	return s.iterator().next();
    }
}
