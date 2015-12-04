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
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/**
 * Combines two or more constraint alternatives into a single overall
 * constraint. The semantics of this aggregate constraint are that at least
 * one of the individual constraint alternatives must be satisfied. The
 * alternatives do not have to be instances of the same type, but they
 * cannot themselves be <code>ConstraintAlternatives</code> instances.
 * <p>
 * Note that this class implements {@link RelativeTimeConstraint} even though
 * the constraint elements might not implement
 * <code>RelativeTimeConstraint</code>.
 * <p>
 * An instance containing an exhaustive list of alternatives (for example,
 * an instance containing both <code>ClientAuthentication.YES</code> and
 * <code>ClientAuthentication.NO</code>) serves no useful purpose, as a
 * requirement or as a preference. A <i>don't care</i> condition should
 * be expressed by the <i>absence</i> of constraints.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class ConstraintAlternatives
				implements RelativeTimeConstraint, Serializable
{
    private static final long serialVersionUID = 7214615235302870613L;

    /**
     * @serialField constraints InvocationConstraint[]
     * The alternative constraints.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("constraints", InvocationConstraint[].class,
			      true)
    };

    /**
     * The alternative constraints.
     */
    private final InvocationConstraint[] constraints;

    /**
     * Indicates whether any of the constraints are based on relative time.
     */
    private transient boolean rel = false;

    /**
     * Creates an instance containing the specified alternative constraints,
     * with duplicate constraints removed. The argument passed to this
     * constructor is neither modified nor retained; subsequent changes to
     * that argument have no effect on the instance created.
     *
     * @param constraints the alternative constraints
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if any of the elements are instances
     * of <code>ConstraintAlternatives</code>, or if fewer than two elements
     * remain after duplicate constraints are removed
     */
    public ConstraintAlternatives(InvocationConstraint[] constraints) {
	this.constraints =
	    reduce((InvocationConstraint[]) constraints.clone());
	setRelative();
    }

    /**
     * Creates an instance containing the specified alternative constraints,
     * with duplicate constraints removed. The argument passed to this
     * constructor is neither modified nor retained; subsequent changes to
     * that argument have no effect on the instance created.
     *
     * @param c the alternative constraints
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if any of the elements are instances
     * of <code>ConstraintAlternatives</code>, or if the elements are not all
     * instances of <code>InvocationConstraint</code>, or if fewer than two
     * elements remain after duplicate constraints are removed
     */
    public ConstraintAlternatives(Collection<InvocationConstraint> c) {
	try {
	    constraints = reduce( c.toArray(
					  new InvocationConstraint[c.size()]));
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
		       "element of collection is not an InvocationConstraint");
	}
	setRelative();
    }

    /**
     * Returns a constraint representing the specified alternative constraints,
     * with duplicate constraints removed. If a single constraint remains after
     * duplicates are removed, then that constraint is returned, otherwise an
     * instance of <code>ConstraintAlternatives</code> containing the remaining
     * constraints is returned. The argument passed to this method is neither
     * modified nor retained; subsequent changes to that argument have no
     * effect on the instance created.
     *
     * @param constraints the alternative constraints
     * @return a constraint representing the specified alternative constraints,
     * with duplicate constraints removed
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if the argument is empty, or if any
     * of the elements are instances of <code>ConstraintAlternatives</code>
     */
    public static InvocationConstraint create(
					   InvocationConstraint[] constraints)
    {
	return reduce((InvocationConstraint[]) constraints.clone(), false);
    }

    /**
     * Returns a constraint representing the specified alternative constraints,
     * with duplicate constraints removed. If a single constraint remains after
     * duplicates are removed, then that constraint is returned, otherwise an
     * instance of <code>ConstraintAlternatives</code> containing the remaining
     * constraints is returned. The argument passed to this method is neither
     * modified nor retained; subsequent changes to that argument have no
     * effect on the instance created.
     *
     * @param c the alternative constraints
     * @return a constraint representing the specified alternative constraints,
     * with duplicate constraints removed
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if the argument is empty, or if any
     * of the elements are instances of <code>ConstraintAlternatives</code>,
     * or if the elements are not all instances of
     * <code>InvocationConstraint</code>
     */
    public static InvocationConstraint create(Collection<InvocationConstraint> c) {
	try {
	    return reduce( c.toArray( new InvocationConstraint[c.size()]), false);
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
		       "element of collection is not an InvocationConstraint");
	}
    }

    /**
     * Creates a constraint containing the specified alternative constraints,
     * and computes the rel field if allAbs is false.
     */
    private ConstraintAlternatives(InvocationConstraint[] constraints,
				   boolean allAbs)
    {
	this.constraints = constraints;
	if (!allAbs) {
	    setRelative();
	}
    }

    /**
     * Sets the rel field to true if any of the constraints are relative.
     */
    private void setRelative() {
	for (int i = constraints.length; --i >= 0; ) {
	    if (constraints[i] instanceof RelativeTimeConstraint) {
		rel = true;
		return;
	    }
	}
    }

    /**
     * Returns true if any of the constraints are relative, false otherwise.
     */
    boolean relative() {
	return rel;
    }

    /**
     * Verifies that the array is non-empty, and that the elements are all
     * non-null and not ConstraintAlternatives instances. Removes duplicates
     * and returns a single constraint if there's only one left, otherwise
     * returns an ConstraintAlternatives containing the remaining constraints.
     * The argument is modified in place.
     */
    private static InvocationConstraint reduce(
					    InvocationConstraint[] constraints,
					    boolean allAbs)
    {
	verify(constraints, 1);
	int n = reduce0(constraints);
	if (n == 1) {
	    return constraints[0];
	}
	return new ConstraintAlternatives(
		      (InvocationConstraint[]) Constraint.trim(constraints, n),
		      allAbs);
    }

    /**
     * Verifies that the array has at least min elements, and that the
     * elements are all non-null and not ConstraintAlternatives instances.
     */
    private static void verify(InvocationConstraint[] constraints, int min) {
	if (constraints.length < min) {
	    throw new IllegalArgumentException(
				 "cannot create constraint with " +
				 (min == 1 ? "no" : ("less than " + min)) +
				 " elements");
	}
	for (int i = constraints.length; --i >= 0; ) {
	    InvocationConstraint c = constraints[i];
	    if (c == null) {
		throw new NullPointerException("elements cannot be null");
	    } else if (c instanceof ConstraintAlternatives) {
		throw new IllegalArgumentException(
			"elements cannot be ConstraintAlternatives instances");
	    }
	}
    }

    /**
     * Verifies that the array has at least 2 elements, and that the elements
     * are all non-null and not ConstraintAlternatives instances, removes
     * duplicates, modifying the array in place, verifies that there are still
     * at least 2 elements, and returns an array containing the remaining
     * elements.
     */
    private static InvocationConstraint[] reduce(
					    InvocationConstraint[] constraints)
    {
	verify(constraints, 2);
	int n = reduce0(constraints);
	if (n == 1) {
	    throw new IllegalArgumentException(
					   "reduced to less than 2 elements");
	}
	return (InvocationConstraint[]) Constraint.trim(constraints, n);
    }

    /**
     * Eliminates duplicates, modifying the array in place, and returns
     * the resulting number of elements.
     */
    private static int reduce0(InvocationConstraint[] constraints) {
	int i = 0;
	for (int j = 0; j < constraints.length; j++) {
	    InvocationConstraint c = constraints[j];
	    if (!Constraint.contains(constraints, i, c)) {
		constraints[i++] = c;
	    }
	}
	return i;
    }

    /**
     * Returns an immutable set of all of the constraints. Any attempt to
     * modify this set results in an {@link UnsupportedOperationException}
     * being thrown.
     *
     * @return an immutable set of all of the constraints
     */
    public Set elements() {
	return new ArraySet(constraints);
    }

    /**
     * Returns the elements, without copying.
     */
    InvocationConstraint[] getConstraints() {
	return constraints;
    }

    /**
     * Returns a constraint equal to the result of taking the constraints in
     * this instance, replacing each constraint that is an instance of
     * {@link RelativeTimeConstraint} with the result of invoking that
     * constraint's <code>makeAbsolute</code> method with the specified base
     * time, and invoking the <code>create</code> method of this class with
     * the revised collection of constraints.
     */
    public InvocationConstraint makeAbsolute(long baseTime) {
	if (!rel) {
	    return this;
	}
	InvocationConstraint[] vals =
	    new InvocationConstraint[constraints.length];
	for (int i = vals.length; --i >= 0; ) {
	    InvocationConstraint c = constraints[i];
	    if (c instanceof RelativeTimeConstraint) {
		c = ((RelativeTimeConstraint) c).makeAbsolute(baseTime);
	    }
	    vals[i] = c;
	}
	return reduce(vals, true);
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return Constraint.hash(constraints);
    }

    /**
     * Two instances of this class are equal if they have the same constraints
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ConstraintAlternatives &&
		Constraint.equal(constraints,
				 ((ConstraintAlternatives) obj).constraints));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ConstraintAlternatives" + Constraint.toString(constraints);
    }

    /**
     * Verifies that there are at least two constraints, that none are
     * <code>null</code> and none are instances of this class, and that
     * there are no duplicates.
     *
     * @throws InvalidObjectException if there are less than two constraints,
     * or any constraint is <code>null</code> or an instance of this class,
     * or if there are duplicates
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (constraints == null) {
	    throw new InvalidObjectException(
				  "cannot create constraint with no elements");
	}
	try {
	    verify(constraints, 2);
	} catch (RuntimeException e) {
	    if (e instanceof NullPointerException ||
		e instanceof IllegalArgumentException)
	    {
		InvalidObjectException ee =
		    new InvalidObjectException(e.getMessage());
		ee.initCause(e);
		throw ee;
	    }
	    throw e;
	}
	for (int i = constraints.length; --i >= 0; ) {
	    if (Constraint.contains(constraints, i, constraints[i])) {
		throw new InvalidObjectException(
			  "cannot create constraint with duplicate elements");
	    }
	}
	setRelative();
    }
}
