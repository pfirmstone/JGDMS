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
package net.jini.lease;

import java.rmi.MarshalledObject;
import net.jini.core.lease.Lease;

/**
 * Exception thrown when a lease renewal set can't unmarshal one or more
 * leases being returned by a <code>getLeases</code> call.
 * <p>
 * When unmarshalling an instance of <code>MarshalledObject</code>, one
 * of the following checked exceptions is possible: an
 * <code>IOException</code> can occur while deserializing the object
 * from its internal representation; and a
 * <code>ClassNotFoundException</code> can occur if, while deserializing
 * the object from its internal representation, either the class file of
 * the object cannot be found, or the class file of either an interface
 * or a class referenced by the object being deserialized cannot be
 * found. Typically, a <code>ClassNotFoundException</code> occurs when
 * the codebase from which to retrieve the needed class file is not
 * currently available.
 * <p>
 * This class provides a mechanism that clients of the lease renewal
 * service may use for efficient handling of the exceptions that may
 * occur when unmarshalling elements of a set of marshalled
 * <code>Lease</code> objects. When elements in such a set are
 * unmarshalled, the <code>LeaseUnmarshalException</code> class may be
 * used to collect and report pertinent information generated when
 * failure occurs while unmarshalling the elements of the set.
 * <p>
 * The information that may be of interest to entities that receive this
 * exception class is contained in the following fields: a set of
 * <code>Lease</code> objects in which each element is the result of a
 * successful unmarshalling attempt, a set of marshalled
 * <code>Lease</code> objects in which each element could not be
 * successfully unmarshalled, and a set of exceptions
 * (<code>IOException</code>, <code>ClassNotFoundException</code>, or
 * some unchecked exception) in which each element corresponds to one of
 * the unmarshalling failures.
 * <p>
 * Thus, when exceptional conditions occur while unmarshalling a set of
 * marshalled <code>Lease</code> objects, this class can be used not
 * only to indicate that an exceptional condition has occurred, but also
 * to provide information that can be used to perform error handling
 * activities such as: determining if it is feasible to continue with
 * processing, reporting errors, attempting recovery, and debugging.
 * <p>
 * Note that this exception class should be used only to report
 * exceptional conditions occurring when unmarshalling a set of
 * marshalled <code>Lease</code> objects.
 * 
 * @author Sun Microsystems, Inc.
 * @see Lease 
 */
public class LeaseUnmarshalException extends Exception {
    private static final long serialVersionUID = -6736107321698417489L;

    /**
     * Array containing the set of instances of <code>Lease</code> that
     * were successfully unmarshalled during the process in which at
     * least one failure occurred.
     *
     * @serial
     */
    final private Lease[] unmarshalledLeases;

    /**
     * Array containing the set of <code>Lease</code> instances that
     * could not be unmarshalled. This set should contain at least one
     * element.
     *
     * @serial
     */
    final private MarshalledObject[] stillMarshalledLeases;

    /**
     * Array containing the set of exceptions that occurred during the
     * unmarshalling process. Each element in this set should be an
     * instance of <code>IOException</code>,
     * <code>ClassNotFoundException</code>, or some unchecked
     * exception. Furthermore, there should be a one-to-one
     * correspondence between each element in this set and each element
     * in the set of still-to-be-unmarshalled <code>Lease</code>
     * instances. That is, the element of this set corresponding to
     * index i should be an instance of the exception that occurred
     * while attempting to unmarshal the element at index i of
     * <code>stillMarshalledLeases</code>.
     *
     * @serial 
     */
    final private Throwable[] exceptions;

    /**
     * Constructs a new instance of <code>LeaseUnmarshalException</code>
     * with a specified message.
     *
     * @param leases array containing the set of instances of
     *	      <code>Lease</code> that were successfully unmarshalled
     * @param marshalledLeases array containing the set of marshalled
     *	      <code>Lease</code> instances that could not be
     *	      unmarshalled
     * @param exceptions array containing the set of exceptions that
     *	      occurred during the unmarshalling process. Each element in
     *	      this set should be an instance of
     *	      <code>IOException</code>,
     *	      <code>ClassNotFoundException</code>, or some unchecked
     *	      exception. Furthermore, there should be a one-to-one
     *	      correspondence between each element in this set and each
     *	      element in the <code>marshalledLeases</code> argument.
     *	      <p>
     *	      That is, the element of this set corresponding to index i
     *	      should be an instance of the exception that occurred while
     *	      attempting to unmarshal the element at index i of the
     *	      <code>marshalledLeases</code> argument.
     * @param message the detail message
     * @throws IllegalArgumentException when the number of elements in
     *	       the <code>exceptions</code> argument is not equal to the
     *	       number of elements in the <code>marshalledLeases</code>
     *	       argument
     */
    public LeaseUnmarshalException(String  message,
				   Lease[] leases,
				   MarshalledObject[] marshalledLeases,
				   Throwable[] exceptions)
    {
	super(message);
	validate(marshalledLeases, exceptions);

	this.unmarshalledLeases = leases;
	this.stillMarshalledLeases = marshalledLeases;
	this.exceptions = exceptions;
    }

    /**
     * Constructs a new instance of <code>LeaseUnmarshalException</code>.
     *
     * @param leases array containing the set of instances of
     *	      <code>Lease</code> that were successfully unmarshalled
     * @param marshalledLeases array containing the set of marshalled
     *	      <code>Lease</code> instances that could not be
     *	      unmarshalled
     * @param exceptions array containing the set of exceptions that
     *	      occurred during the unmarshalling process. Each element in
     *	      this set should be an instance of
     *	      <code>IOException</code>,
     *	      <code>ClassNotFoundException</code>, or some unchecked
     *	      exception. Furthermore, there should be a one-to-one
     *	      correspondence between each element in this set and each
     *	      element in the <code>marshalledLeases</code> argument.
     *	      <p>
     *	      That is, the element of this set corresponding to index i
     *	      should be an instance of the exception that occurred while
     *	      attempting to unmarshal the element at index i of the
     *	      <code>marshalledLeases</code> argument.
     * @throws IllegalArgumentException when the number of elements in
     *	       the <code>exceptions</code> argument is not equal to the
     *	       number of elements in the <code>marshalledLeases</code>
     *	       argument
     */
    public LeaseUnmarshalException(Lease[] leases,
				   MarshalledObject[] marshalledLeases,
				   Throwable[] exceptions)
    {
	super();
	validate(marshalledLeases, exceptions);

	this.unmarshalledLeases = leases;
	this.stillMarshalledLeases = marshalledLeases;
	this.exceptions = exceptions;
    }

    /**
     * Helper method for constructors. Throws an
     * <code>IllegalArgumentException</code> if the length of
     * <code>marshalledLeases</code> does not match the length of
     * <code>exceptions</code>.
     */
    private void validate(MarshalledObject[] marshalledLeases,
			  Throwable[] exceptions)
    {
	/*
	 * If the number of exceptions does not equal number of leases
	 * that could not be unmarshalled, throw exception.
	 */
	if(exceptions.length != marshalledLeases.length) {
	    throw new IllegalArgumentException
			   ("exceptions.length ("+exceptions.length
			    +") is not equal to marshalledLeases.length ("
			    +marshalledLeases.length +")");
	}
    }

    /**
     * Accessor method that returns an array consisting of instances of
     * <code>Lease</code>, where each element of the array corresponds
     * to a successfully unmarshalled object. Note that the same array
     * is returned on each invocation of this method; that is, a copy is
     * not made.
     *
     * @return array of instances of <code>Lease</code>, where each
     *	       element corresponds to a successfully unmarshalled object
     */
    public Lease[] getLeases() {
	return unmarshalledLeases;
    }

    /**
     * Accessor method that returns an array consisting of instances of
     * <code>MarshalledObject</code>, where each element of the array is
     * a marshalled instance of the <code>Lease</code> interface, and
     * corresponds to an object that could not be successfully
     * unmarshalled. Note that the same array is returned on each
     * invocation of this method; that is, a copy is not made.
     *
     * @return array of marshalled instances of <code>Lease</code>,
     *	       where each element corresponds to an object in which
     *	       failure occurred while attempting to unmarshal the object
     */
    public MarshalledObject[] getMarshalledLeases() {
	return stillMarshalledLeases;
    }

    /**
     * Accessor method that returns an array consisting of instances of
     * <code>Throwable</code>, where each element of the array
     * corresponds to one of the exceptions that occurred during the
     * unmarshalling process. Note that the same array is returned on
     * each invocation of this method; that is, a copy is not made.
     * <p> 
     * Each element in the return set should be an instance of
     * <code>IOException</code>, <code>ClassNotFoundException</code>, or
     * some unchecked exception. Additionally, there should be a
     * one-to-one correspondence between each element in the array
     * returned by this method and the array returned by the
     * <code>getMarshalledLeases</code> method. That is, the i-th
     * element of the set returned by this method should be an instance
     * of the exception that occurred while attempting to unmarshal the
     * i-th element of the set returned by the
     * <code>getMarshalledLeases</code> method.
     *
     * @return array of instances of <code>Throwable</code>, where each
     *	       element corresponds to one of the exceptions that
     *	       occurred during the unmarshalling process
     */
    public Throwable[] getExceptions() {
	return exceptions;
    }
}
