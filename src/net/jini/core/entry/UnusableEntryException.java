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
package net.jini.core.entry;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.ObjectInputStream;

/**
 * Thrown when one tries to get an <code>Entry</code> from a service,
 * but the entry is unusable (due to serialization or other errors).
 * Normally <code>partialEntry</code> points to an entry with as many
 * fields as possible filled in, with the array <code>unusableFields</code>
 * naming the fields that could not be deserialized and the array
 * <code>nestedExceptions</code> having the corresponding exception.
 * <p>
 * If the serialized <code>Entry</code> was corrupt enough that no
 * attempt could even be made to deserialize its fields,
 * <code>partialEntry</code> and <code>unusableFields</code> will be
 * <code>null</code>, and <code>nestedExceptions</code> will be an
 * array with one element that is the offending exception.  This will
 * typically be because one or more of the classes of the <code>Entry</code>
 * type itself cannot be loaded.
 * <p>
 * The names in <code>unusableFields</code> can be used together with
 * the reflection mechanisms of <code>java.lang.reflect</code> to
 * examine the full state of the object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class UnusableEntryException extends Exception {
    static final long serialVersionUID = -2199083666668626172L;

    /**
     * The partial entry.  Fields that could not be deserialized
     * will be <code>null</code>.
     *
     * @serial
     */
    public Entry partialEntry;

    /**
      *	The names of the unusable fields.  If the entry was entirely
      * unusable, <code>unusableFields</code> will be <code>null</code>.
      *
      * @serial
      */
    public String[] unusableFields;

    /**
     * The exception that caused the failure for the corresponding
     * field named in unusableFields.  If the entry was entirely
     * unusable, <code>nestedExceptions</code> will be an array with
     * the one exception that prevented its use.
     *
     * @serial
     */
    public Throwable[] nestedExceptions;

    /**
     * Create an exception for the given partial entry and vectors of
     * bad field names/nested exception pairs.
     *
     * @param partial     the Entry object on which the exception occurred
     * @param badFields   a String array containing the bad field names
     * @param exceptions  an array of Throwable objects associated with the
     *                    bad field names
     *
     * @throws IllegalArgumentException if <code>partial</code> is
     *     <code>null</code> and <code>badFields</code> is not
     *     <code>null</code> or <code>exceptions</code> does not have
     *     exactly one element or if <code>partial</code> is
     *     non-<code>null</code> and <code>badFields</code> and
     *     <code>exceptions</code> are not the same length
     *
     * @throws NullPointerException if <code>partial</code> is
     *     non-<code>null</code> and <code>badFields</code> or any
     *     element of <code>badFields</code> is <code>null</code>, or
     *     if <code>exceptions</code> or any element of
     *     <code>exceptions</code> is <code>null</code>
     */
    public UnusableEntryException(Entry partial, String[] badFields,
	Throwable[] exceptions)
    {
	super();

	if (partial == null) {
	    if (exceptions.length != 1) {
		throw new IllegalArgumentException("If partial is null " +
		    "exceptions must have one element");
		}

	    if (badFields != null) {
		throw new IllegalArgumentException("If partial is null " +
						   "badFields must be null");
	    }
	} else {
	    if (badFields.length != exceptions.length) {
		throw new IllegalArgumentException("If partial is non-null " +
                    "badFields and exceptions must have same length");
	    }
	}

	if (badFields != null) {
	    for (int i=0; i<badFields.length; i++) {
		if (badFields[i] == null)
		    throw new NullPointerException("badFields has a null element");
	    }
	}

	for (int i=0; i<exceptions.length; i++) {
	    if (exceptions[i] == null) 
		throw new NullPointerException("exceptions has a null element");
	}

	partialEntry = partial;
	unusableFields = badFields;
	nestedExceptions = exceptions;
    }

    /**
     * Create an exception for a nested exception that prevented even an
     * attempt to build an entry.
     *
     * @param e a Throwable representing the nested exception
     * @throws NullPointerException if <code>e</code> is <code>null</code>
     */
    public UnusableEntryException(Throwable e) {
	if (e == null)
	    throw new NullPointerException("e must be non-null");
	
	partialEntry = null;
	unusableFields = null;
	nestedExceptions = new Throwable[] { e };
    }

    /**
     * @throws InvalidObjectException if:
     * <ul>
     * <li> <code>partialEntry</code> is <code>null</code> and
     *      <code>unusableFields</code> is not <code>null</code> or
     *      <code>nestedExceptions</code> does not have exactly one
     *      element,
     * <li> if <code>partialEntry</code> is non-<code>null</code> and
     *      <code>unusableFields</code> and
     *      <code>nestedExceptions</code> are not the same length,
     * <li> if <code>partialEntry</code> is non-<code>null</code> and
     *      <code>unusableFields</code> is <code>null</code> or 
     *      any element of <code>unusableFields</code> is
     *      <code>null</code>, or
     * <li> if <code>nestedExceptions</code> or any element of 
     *     <code>nestedExceptions</code> is <code>null</code>
     * </ul>
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if a problem occurs during de-serialization.
     */
    private void readObject(ObjectInputStream in) 
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	if (partialEntry == null) {
	    if (nestedExceptions.length != 1) {
		throw new InvalidObjectException("If partialEntry is null " +
		    "nestedExceptions must have one element");
		}

	    if (unusableFields != null) {
		throw new InvalidObjectException("If partialEntry is null " +
						 "unusableFields must be null");
	    }
	} else {
	    if (unusableFields == null)
		throw new InvalidObjectException("unusableFields is null");

	    if (nestedExceptions == null)
		throw new InvalidObjectException("nestedExceptions is null");

	    if (unusableFields.length != nestedExceptions.length) {
		throw new InvalidObjectException("If partialEntry is non-null " +
                    "unusableFields and nestedExceptions must have same length");
	    }
	}

	if (unusableFields != null) {
	    for (int i=0; i<unusableFields.length; i++) {
		if (unusableFields[i] == null)
		    throw new InvalidObjectException("unusableFields has a " +
						     "null element");
	    }
	}

	for (int i=0; i<nestedExceptions.length; i++) {
	    if (nestedExceptions[i] == null) 
		throw new InvalidObjectException("nestedExceptions has a null " +
						 "element");
	}
    }

    /** 
     * @throws InvalidObjectException if called
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException(
	    "UnusableEntryExceptions should always have data");
    }

    /**
     * Calls {@link #printStackTrace(PrintStream) printStackTrace(System.err)}.
     */
    public void printStackTrace() { 
        printStackTrace(System.err);
    }

    /**
     * Calls {@link Exception#printStackTrace(PrintStream)
     * super.printStackTrace(s)} and then calls {@link
     * Throwable#printStackTrace(PrintStream) printStackTrace(s)} on
     * each exception in <code>nestedExceptions</code>.
     */
    public void printStackTrace(PrintStream s) {
        synchronized (s) {
	    super.printStackTrace(s);

	    if (unusableFields == null) {
		s.println("Total unmarshalling failure, cause was:");
		nestedExceptions[0].printStackTrace(s);
	    } else {
		s.println("Partial unmarshalling failure");
		for (int i=0; i<nestedExceptions.length; i++) {
		    s.println(unusableFields[i] + 
			" field could not be unmarshalled because of:");
		    nestedExceptions[i].printStackTrace(s);
		}
	    }
        }
    }

    /**
     * Calls {@link Exception#printStackTrace(PrintWriter)
     * super.printStackTrace(s)} and then calls {@link
     * Throwable#printStackTrace(PrintWriter) printStackTrace(s)} on
     * each exception in <code>nestedExceptions</code>.
     */
    public void printStackTrace(PrintWriter s) { 
        synchronized (s) {
	    super.printStackTrace(s);

	    if (unusableFields == null) {
		s.println("Total unmarshalling failure, cause was:");
		nestedExceptions[0].printStackTrace(s);
	    } else {
		s.println("Partial unmarshalling failure");
		for (int i=0; i<nestedExceptions.length; i++) {
		    s.println(unusableFields[i] + 
			" field could not be unmarshalled because of:");
		    nestedExceptions[i].printStackTrace(s);
		}
	    }
        }
    }
}
