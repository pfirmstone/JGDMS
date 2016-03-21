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

package net.jini.core.constraint;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * An immutable aggregation of constraints into a set of requirements and a
 * set of preferences. A requirement is a mandatory constraint that must be
 * satisfied for the invocation. A preference is a desired constraint, to be
 * satisfied if possible, but it will not be satisfied if it conflicts with a
 * requirement. If two preferences conflict, it is arbitrary as to which
 * one will be satisfied. If a constraint is not understood, due to lack of
 * knowledge of the type of the constraint or the contents of the constraint,
 * then the constraint cannot be satisfied.
 * <p>
 * Note that it is possible for an instance of this class to contain both
 * requirements that conflict with each other, and preferences that conflict
 * with each other and with requirements.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public final class InvocationConstraints implements Serializable {
    private static final long serialVersionUID = -3363161199079334224L;

    /**
     * @serialField reqs InvocationConstraint[] The requirements.
     * @serialField prefs InvocationConstraint[] The preferences.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("reqs", InvocationConstraint[].class, true),
        new ObjectStreamField("prefs", InvocationConstraint[].class, true)
    };

    /** An empty array */
    private static final InvocationConstraint[] empty =
						  new InvocationConstraint[0];

    /**
     * Bit set in the rel field if there are relative requirements.
     */
    private static final int REL_REQS = 1;
    /**
     * Bit set in the rel field if there are relative preferences.
     */
    private static final int REL_PREFS = 2;

    /**
     * An empty instance, one that has no requirements and no preferences.
     */
    public static final InvocationConstraints EMPTY =
		  new InvocationConstraints((InvocationConstraint) null, null);

    /**
     * The requirements.
     */
    private InvocationConstraint[] reqs;
    /**
     * The preferences.
     */
    private InvocationConstraint[] prefs;

    /**
     * Flags indicating whether any requirements and/or preferences are
     * based on relative time.
     */
    private transient int rel = 0;
    
    /**
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException 
     */
    public InvocationConstraints(GetArg arg) throws IOException{
	this(arg.get("reqs", null, InvocationConstraint[].class),
	     arg.get("prefs", null, InvocationConstraint[].class),
	     true
	);
    }
    
    /**
     * AtomicSerial private constructor.
     * @param reqs
     * @param prefs
     * @param serial
     * @throws InvalidObjectException 
     */
    private InvocationConstraints(InvocationConstraint[] reqs, 
				  InvocationConstraint[] prefs,
				  boolean serial) throws InvalidObjectException
    {
	this(check(reqs, prefs),
             verify(reqs),
             verify(prefs)
        );
    }
    
    private static boolean check(InvocationConstraint[] reqs, 
				 InvocationConstraint[] prefs) throws InvalidObjectException
    {
        for (int i = prefs.length; --i >= 0; ) {
	    if (Constraint.contains(reqs, reqs.length, prefs[i])) {
		throw new InvalidObjectException(
			  "cannot create constraint with redundant elements");
	    }
	}
        return true;
    }
    
    /**
     * AtomicSerial private constructor.
     * @param serial
     * @param reqs
     * @param prefs 
     */
    private InvocationConstraints(boolean serial,
				  InvocationConstraint[] reqs, 
				  InvocationConstraint[] prefs)
    {
	this.reqs = reqs;
	this.prefs = prefs;
	setRelative(reqs, REL_REQS);
	setRelative(prefs, REL_REQS);
    }

    /**
     * Creates an instance that has the first constraint, <code>req</code>,
     * added as a requirement if it is a non-<code>null</code> value, and has
     * the second constraint, <code>pref</code>, added as a preference if it
     * is a non-<code>null</code> value and is not a duplicate of the
     * requirement.
     *
     * @param req a requirement, or <code>null</code>
     * @param pref a preference, or <code>null</code>
     */
    public InvocationConstraints(InvocationConstraint req,
				 InvocationConstraint pref)
    {
	if (req != null) {
	    reqs = new InvocationConstraint[]{req};
	}
	if (pref != null) {
	    prefs = new InvocationConstraint[]{pref};
	}
	reduce();
    }

    /**
     * Creates an instance that has all of the constraints from the first
     * array, <code>reqs</code>, added as requirements if the array is a
     * non-<code>null</code> value, and has all of the constraints from
     * the second array, <code>prefs</code>, added as preferences if the
     * array is a non-<code>null</code> value. Duplicate requirements,
     * duplicate preferences, and preferences that are duplicates of
     * requirements are all removed. The arguments passed to the constructor
     * are neither modified nor retained; subsequent changes to those
     * arguments have no effect on the instance created.
     *
     * @param reqs requirements, or <code>null</code>
     * @param prefs preferences, or <code>null</code>
     * @throws NullPointerException if any element of an argument is
     * <code>null</code>
     */
    public InvocationConstraints(InvocationConstraint[] reqs,
				 InvocationConstraint[] prefs)
    {
	if (reqs != null) {
	    this.reqs = (InvocationConstraint[]) reqs.clone();
	}
	if (prefs != null) {
	    this.prefs = (InvocationConstraint[]) prefs.clone();
	}
	reduce();
    }

    /**
     * Creates an instance that has all of the constraints from the first
     * collection, <code>reqs</code>, added as requirements if the collection
     * is a non-<code>null</code> value, and has all of the constraints from
     * the second collection, <code>prefs</code>, added as preferences if the
     * collection is a non-<code>null</code> value. Duplicate requirements,
     * duplicate preferences, and preferences that are duplicates of
     * requirements are all removed. The arguments passed to the constructor
     * are neither modified nor retained; subsequent changes to those
     * arguments have no effect on the instance created.
     *
     * @param reqs requirements, or <code>null</code>
     * @param prefs preferences, or <code>null</code>
     * @throws NullPointerException if any element of an argument is
     * <code>null</code>
     * @throws IllegalArgumentException if any element of an argument is not
     * an instance of <code>InvocationConstraint</code>
     */
    public InvocationConstraints(Collection<InvocationConstraint> reqs, Collection<InvocationConstraint> prefs) {
	try {
	    if (reqs != null) {
		this.reqs = reqs.toArray(new InvocationConstraint[reqs.size()]);
	    }
	    if (prefs != null) {
		this.prefs = prefs.toArray(
				       new InvocationConstraint[prefs.size()]);
	    }
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
		       "element of collection is not an InvocationConstraint");
	}
	reduce();
    }

    /**
     * Creates an instance containing the specified requirements and
     * preferences. Reqidx and prefidx indicate how many of the initial
     * elements in each array are known to have been reduced (meaning they
     * came from an existing instance of this class), but prefidx must be
     * zero if reqidx is non-zero but reqidx is less than the total number
     * of requirements. Rel contains the flags for which constraints are
     * relative.
     */
    private InvocationConstraints(InvocationConstraint[] reqs,
				  int reqidx,
				  InvocationConstraint[] prefs,
				  int prefidx,
				  int rel)
    {
	this.reqs = reqs;
	this.prefs = prefs;
	reduce(reqidx, prefidx);
	this.rel = rel;
    }

    /**
     * Replaces null fields with empty arrays, eliminates duplicates, and
     * sets flags indicating which constraints are relative.
     */
    private void reduce() {
	if (reqs == null) {
	    reqs = empty;
	}
	if (prefs == null) {
	    prefs = empty;
	}
	reduce(0, 0);
	setRelative(reqs, REL_REQS);
	setRelative(prefs, REL_PREFS);
    }

    /**
     * Checks for nulls and eliminates duplicates. Reqidx and prefidx
     * indicate how many of the initial elements in each array are known to
     * have been reduced (meaning they came from an existing instance of this
     * class), but prefidx must be zero if reqidx is non-zero but reqidx is
     * less than the total number of requirements.
     */
    private void reduce(int reqidx, int prefidx) {
	for (int i = reqidx; i < reqs.length; i++) {
	    InvocationConstraint req = reqs[i];
	    if (req == null) {
		throw new NullPointerException("elements cannot be null");
	    } else if (!Constraint.contains(reqs, reqidx, req)) {
		reqs[reqidx++] = req;
	    }
	}
	reqs = (InvocationConstraint[]) Constraint.trim(reqs, reqidx);
	for (int i = prefidx; i < prefs.length; i++) {
	    InvocationConstraint pref = prefs[i];
	    if (pref == null) {
		throw new NullPointerException("elements cannot be null");
	    } else if (!Constraint.contains(prefs, prefidx, pref) &&
		       !Constraint.contains(reqs, reqs.length, pref))
	    {
		prefs[prefidx++] = pref;
	    }
	}
	prefs = (InvocationConstraint[]) Constraint.trim(prefs, prefidx);
    }

    /**
     * Returns true if the specified constraint either implements
     * RelativeTimeConstraint or is an instance of ConstraintAlternatives with
     * elements that implement RelativeTimeConstraint, and false otherwise.
     */
    private static boolean relative(InvocationConstraint c) {
	return (c instanceof RelativeTimeConstraint &&
		(!(c instanceof ConstraintAlternatives) ||
		 ((ConstraintAlternatives) c).relative()));
    }

    /**
     * Sets the given flag in the rel field if any if the specified
     * constraints are relative.
     */
    private void setRelative(InvocationConstraint[] constraints, int flag) {
	for (int i = constraints.length; --i >= 0; ) {
	    if (relative(constraints[i])) {
		rel |= flag;
		return;
	    }
	}
    }

    /**
     * Returns an instance of this class that has all of the requirements from
     * each non-<code>null</code> argument added as requirements and has all
     * of the preferences from each non-<code>null</code> argument added as
     * preferences. Duplicate requirements, duplicate preferences, and
     * preferences that are duplicates of requirements are all removed.
     *
     * @param constraints1 constraints, or <code>null</code>
     * @param constraints2 constraints, or <code>null</code>
     * @return an instance of this class that has all of the requirements from
     * each non-<code>null</code> argument added as requirements and has all
     * of the preferences from each non-<code>null</code> argument added as
     * preferences
     */
    public static InvocationConstraints combine(
					   InvocationConstraints constraints1,
					   InvocationConstraints constraints2)
    {
	if (constraints1 == null || constraints1.isEmpty()) {
	    return constraints2 == null ? EMPTY : constraints2;
	} else if (constraints2 == null || constraints2.isEmpty()) {
	    return constraints1;
	} else if (constraints2.reqs.length > constraints1.reqs.length) {
	    InvocationConstraints tmp = constraints1;
	    constraints1 = constraints2;
	    constraints2 = tmp;
	}
	int prefidx;
	InvocationConstraint[] reqs;
	if (constraints2.reqs.length > 0) {
	    reqs = concat(constraints1.reqs, constraints2.reqs);
	    prefidx = 0;
	} else {
	    reqs = constraints1.reqs;
	    prefidx = constraints1.prefs.length;
	}
	InvocationConstraint[] prefs;
	if (constraints1.prefs.length > 0 || constraints2.prefs.length > 0) {
	    prefs = concat(constraints1.prefs, constraints2.prefs);
	} else {
	    prefs = empty;
	}
	return new InvocationConstraints(reqs, constraints1.reqs.length,
					 prefs, prefidx,
					 constraints1.rel | constraints2.rel);
    }

    /**
     * Returns a new array containing the elements of both arguments.
     */
    private static InvocationConstraint[] concat(InvocationConstraint[] arr1,
						 InvocationConstraint[] arr2)
    {
	InvocationConstraint[] res =
	    new InvocationConstraint[arr1.length + arr2.length];
	System.arraycopy(arr1, 0, res, 0, arr1.length);
	System.arraycopy(arr2, 0, res, arr1.length, arr2.length);
	return res;
    }

    /**
     * Converts any relative constraints to absolute time.
     */
    private static InvocationConstraint[] makeAbsolute(
						   InvocationConstraint[] arr,
						   long baseTime)
    {
	InvocationConstraint[] narr = new InvocationConstraint[arr.length];
	for (int i = arr.length; --i >= 0; ) {
	    InvocationConstraint c = arr[i];
	    if (c instanceof RelativeTimeConstraint) {
		c = ((RelativeTimeConstraint) c).makeAbsolute(baseTime);
	    }
	    narr[i] = c;
	}
	return narr;
    }

    /**
     * Returns an instance of this class equal to the result of taking the
     * requirements and preferences in this instance, replacing each
     * constraint that is an instance of {@link RelativeTimeConstraint} with
     * the result of invoking that constraint's <code>makeAbsolute</code>
     * method with the specified base time, and creating a new instance of
     * this class with duplicate requirements, duplicate preferences, and
     * preferences that are duplicates of requirements all removed.
     *
     * @param baseTime an absolute time, specified in milliseconds from
     * midnight, January 1, 1970 UTC
     * @return an instance of this class equal to the result of taking the
     * requirements and preferences in this instance, replacing each
     * constraint that is an instance of <code>RelativeTimeConstraint</code>
     * with the result of invoking that constraint's <code>makeAbsolute</code>
     * method with the specified base time, and creating a new instance of
     * this class with duplicate requirements, duplicate preferences, and
     * preferences that are duplicates of requirements all removed
     */
    public InvocationConstraints makeAbsolute(long baseTime) {
	if (rel == 0) {
	    return this;
	}
	InvocationConstraint[] nreqs;
	int reqidx;
	if ((rel & REL_REQS) != 0) {
	    nreqs = makeAbsolute(reqs, baseTime);
	    reqidx = 0;
	} else {
	    nreqs = reqs;
	    reqidx = reqs.length;
	}
	InvocationConstraint[] nprefs;
	if ((rel & REL_PREFS) != 0) {
	    nprefs = makeAbsolute(prefs, baseTime);
	} else {
	    nprefs = (InvocationConstraint[]) prefs.clone();
	}
	return new InvocationConstraints(nreqs, reqidx, nprefs, 0, 0);
    }

    /**
     * Returns an instance of this class constructed from all of the same
     * requirements and preferences as this instance, but with every
     * constraint that is an instance of {@link RelativeTimeConstraint}
     * replaced by the result of invoking the constraint's
     * <code>makeAbsolute</code> method with the current time (as given by
     * {@link System#currentTimeMillis System.currentTimeMillis}). Duplicate
     * requirements, duplicate preferences, and preferences that are
     * duplicates of requirements are all removed.
     *
     * @return an instance of this class constructed from all of the same
     * requirements and preferences as this instance, but with every
     * constraint that is an instance of <code>RelativeTimeConstraint</code>
     * replaced by the result of invoking the constraint's
     * <code>makeAbsolute</code> method with the current time
     */
    public InvocationConstraints makeAbsolute() {
	if (rel == 0) {
	    return this;
	}
	return makeAbsolute(System.currentTimeMillis());
    }

    /**
     * Returns an immutable set of all of the requirements. Any attempt to
     * modify this set results in an {@link UnsupportedOperationException}
     * being thrown.
     *
     * @return an immutable set of all of the requirements
     */
    public Set<InvocationConstraint> requirements() {
	return new ArraySet<InvocationConstraint>(reqs);
    }

    /**
     * Returns an immutable set of all of the preferences. Any attempt to
     * modify this set results in an {@link UnsupportedOperationException}
     * being thrown.
     *
     * @return an immutable set of all of the preferences
     */
    public Set<InvocationConstraint> preferences() {
	return new ArraySet<InvocationConstraint>(prefs);
    }

    /**
     * Returns <code>true</code> if the instance has no requirements and no
     * preferences; returns <code>false</code> otherwise.
     *
     * @return <code>true</code> if the instance has no requirements and no
     * preferences; <code>false</code> otherwise
     */
    public boolean isEmpty() {
	return reqs.length == 0 && prefs.length == 0;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return Constraint.hash(reqs) + Constraint.hash(prefs);
    }

    /**
     * Two instances of this class are equal if they have the same requirements
     * and the same preferences. This method is a sufficient substitute for
     * {@link net.jini.security.proxytrust.TrustEquivalence#checkTrustEquivalence
     * TrustEquivalence.checkTrustEquivalence}.
     */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (!(obj instanceof InvocationConstraints)) {
	    return false;
	}
	InvocationConstraints sc = (InvocationConstraints)obj;
	return (Constraint.equal(reqs, sc.reqs) &&
		Constraint.equal(prefs, sc.prefs));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return ("InvocationConstraints[reqs: " + Constraint.toString(reqs) +
		", prefs: " + Constraint.toString(prefs) + "]");
    }
    
    /**
     * Verifies that there are no <code>null</code> elements and no duplicates.
     *
     * @throws InvalidObjectException if the requirements or preferences
     * arrays are <code>null</code>, or any element is <code>null</code>,
     * or if there are duplicate requirements, duplicate preferences, or
     * preferences that are duplicates of requirements
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    /* Also sets the rel field */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	verify(reqs);
	verify(prefs);
	for (int i = prefs.length; --i >= 0; ) {
	    if (Constraint.contains(reqs, reqs.length, prefs[i])) {
		throw new InvalidObjectException(
			  "cannot create constraint with redundant elements");
	    }
	}
	setRelative(reqs, REL_REQS);
	setRelative(prefs, REL_REQS);
    }

    /**
     * Verifies that the array is non-null, the elements are all non-null,
     * and there are no duplicates.
     */
    private static InvocationConstraint[] verify(InvocationConstraint[] constraints)
	throws InvalidObjectException
    {
	if (constraints == null) {
	    throw new InvalidObjectException("array cannot be null");
	}
	for (int i = constraints.length; --i >= 0; ) {
	    InvocationConstraint c = constraints[i];
	    if (c == null) {
		throw new InvalidObjectException("elements cannot be null");
	    } else if (Constraint.contains(constraints, i, c)) {
		throw new InvalidObjectException(
			  "cannot create constraint with redundant elements");
	    }
	}
        return constraints;
    }
}
