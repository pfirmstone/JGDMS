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

package net.jini.discovery;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.MarshalledObject;
import net.jini.core.lookup.ServiceRegistrar;
import org.apache.river.api.io.AtomicException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;

/**
 * When unmarshalling an instance of <code>MarshalledObject</code>, different
 * exceptions can occur. An <code>IOException</code> can occur while
 * deserializing the object from its internal representation. A
 * <code>ClassNotFoundException</code> can occur if, while deserializing
 * the object from its internal representation, either the class file of
 * the object cannot be found, or the class file of either an interface
 * or a class referenced by the object being deserialized cannot be found. 
 * Typically, a <code>ClassNotFoundException</code> occurs when the codebase
 * from which to retrieve the needed class file is not currently available.
 * <p>
 * This class provides a mechanism that clients of the lookup discovery 
 * service may use for efficient handling of the exceptions that may 
 * occur when unmarshalling elements of a set of marshalled instances 
 * of the <code>ServiceRegistrar</code> interface. When elements in such
 * a set are unmarshalled, the <code>LookupUnmarshalException</code> class
 * may be used to collect and report pertinent information generated when
 * failure occurs while unmarshalling the elements of the set.
 * <p>
 * The information that may be of interest to entities that receive this
 * exception class is contained in the following fields: a set of
 * <code>ServiceRegistrar</code> instances in which each element is the
 * result of a successful unmarshalling attempt, a set of marshalled instances
 * of <code>ServiceRegistrar</code> in which each element could not be
 * successfully unmarshalled, and a set of exceptions (<code>IOException</code>,
 * <code>ClassNotFoundException</code>, or some unchecked exception) in which
 * each element corresponds to one of the unmarshalling failures.
 * <p>
 * Thus, when exceptional conditions occur while unmarshalling a set of 
 * marshalled instances of <code>ServiceRegistrar</code>, this class can
 * be used not only to indicate that an exceptional condition has occurred,
 * but also to provide information that can be used to perform error handling 
 * activities such as: determining if it is feasible to continue with 
 * processing, reporting exceptions, attempting recovery, and debugging.
 * <p>
 * Note that this exception class should be used only to report exceptional
 * conditions occurring when unmarshalling a set of marshalled instances 
 * of the <code>ServiceRegistrar</code> interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.lookup.ServiceRegistrar
 */
@AtomicSerial
public class LookupUnmarshalException extends AtomicException {

    private static final long serialVersionUID = 2956893184719950537L;

    /**
     * Array containing the set of instances of <code>ServiceRegistrar</code>
     * that were successfully unmarshalled during the process in which at
     * least one failure occurred. This set may be <code>null</code> or
     * have zero length.
     *
     * @serial
     */
    private final ServiceRegistrar[] registrars;

    /**
     * Array containing the set of <code>ServiceRegistrar</code> instances
     * that could not be unmarshalled. This set should not be <code>null</code>
     * and should contain at least one element.
     *
     * @serial
     */
    private final MarshalledObject[] marshalledRegistrars;

    /**
     * Array containing the set of exceptions that occurred during the
     * unmarshalling process. Each element in this set should be an instance
     * of <code>IOException</code>, <code>ClassNotFoundException</code>, or
     * some unchecked exception. Furthermore, there should be a one-to-one
     * correspondence between each element in this set and each element in
     * the set of still-to-be-unmarshalled <code>ServiceRegistrar</code>
     * instances. That is, the element of this set corresponding to index i
     * should be an instance of the exception that occurred while attempting
     * to unmarshal the element at index i of <code>marshalledRegistrars</code>.
     * This set should not be <code>null</code> and should contain at least
     * one element.
     *
     * @serial
     */
    private final Throwable[] exceptions;

    /**
     * Constructs a new instance of <code>LookupUnmarshalException</code>.
     *
     * @param registrars           Array containing the set of instances of
     *                             <code>ServiceRegistrar</code> that were
     *                             successfully unmarshalled.
     *                            
     * @param marshalledRegistrars Array containing the set of marshalled
     *                             <code>ServiceRegistrar</code> instances
     *                             that could not be unmarshalled.
     *                   
     * @param exceptions           Array containing the set of exceptions that
     *                             occurred during the unmarshalling process.
     *                             Each element in this set should be an
     *                             instance of <code>IOException</code>,
     *                             <code>ClassNotFoundException</code>, or
     *                             some unchecked exception. Furthermore,
     *                             there should be a one-to-one correspondence
     *                             between each element in this set and
     *                             each element in the
     *                             <code>marshalledRegistrars</code> parameter.
     * <p>
     *                             That is, the element of this set
     *                             corresponding to index i should be an
     *                             instance of the exception that occurred
     *                             while attempting to unmarshal the element
     *                             at index i of the
     *                             <code>marshalledRegistrars</code> parameter.
     *
     * @throws java.lang.NullPointerException this exception occurs
     *         when <code>null</code> is input for either the
     *         <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter.
     * @throws java.lang.IllegalArgumentException this exception occurs when
     *         either the <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter has zero length; or when the
     *         lengths of those two parameters are not equal.
     */
    public LookupUnmarshalException(ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions) 
    {
        this(registrars, marshalledRegistrars, exceptions,
	     check(registrars, marshalledRegistrars, exceptions));
    }//end constructor
    
    private LookupUnmarshalException(ServiceRegistrar[] registrars,
                                    MarshalledObject[]  marshalledRegistrars,
                                    Throwable[]         exceptions,
				    boolean		check)
    {
	super();
	this.registrars = registrars != null ? registrars.clone(): null;
	this.marshalledRegistrars = marshalledRegistrars.clone();
	this.exceptions = exceptions.clone();
    }

    /**
     * Constructs a new instance of <code>LookupUnmarshalException</code>.
     *
     * @param registrars           Array containing the set of instances of
     *                             <code>ServiceRegistrar</code> that were
     *                             successfully unmarshalled.
     *                            
     * @param marshalledRegistrars Array containing the set of marshalled
     *                             <code>ServiceRegistrar</code> instances
     *                             that could not be unmarshalled.
     *                   
     * @param exceptions           Array containing the set of exceptions that
     *                             occurred during the unmarshalling process.
     *                             Each element in this set should be an
     *                             instance of <code>IOException</code>,
     *                             <code>ClassNotFoundException</code>, or
     *                             some unchecked exception. Furthermore,
     *                             there should be a one-to-one correspondence
     *                             between each element in this set and
     *                             each element in the
     *                             <code>marshalledRegistrars</code> parameter.
     * <p>
     *                             That is, the element of this set
     *                             corresponding to index i should be an
     *                             instance of the exception that occurred
     *                             while attempting to unmarshal the element
     *                             at index i of the
     *                             <code>marshalledRegistrars</code> parameter.
     *
     * @param message              <code>String</code> describing the nature
     *                             of the exception
     *
     * @throws java.lang.NullPointerException this exception occurs
     *         when <code>null</code> is input for either the
     *         <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter.
     * @throws java.lang.IllegalArgumentException this exception occurs when
     *         either the <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter has zero length; or when the
     *         lengths of those two parameters are not equal.
     */
    public LookupUnmarshalException(ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions, 
                                    String             message) 
    {
	this(registrars, marshalledRegistrars, exceptions, message,
	    check(registrars,marshalledRegistrars,exceptions));
    }//end constructor
    
    private LookupUnmarshalException(ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions, 
                                    String             message,
				    boolean		check)
    {
	super(message);
	this.registrars = registrars != null ? registrars.clone() : null;
	this.marshalledRegistrars = marshalledRegistrars.clone();
	this.exceptions = exceptions.clone();
	
    }

    /**
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException 
     */
    public LookupUnmarshalException(GetArg arg) throws IOException{
	this(arg, 
	     Valid.copy(arg.get("registrars", null, ServiceRegistrar[].class)),
	     Valid.copy(arg.get("marshalledRegistrars", null, MarshalledObject[].class)),
	     Valid.copy(arg.get("exceptions", null, Throwable[].class))
	);
    }
    
    /**
     * Validate state.
     * @param arg
     * @param registrars
     * @param marshalledRegistrars
     * @param exceptions
     * @throws IOException 
     */
    private LookupUnmarshalException(GetArg arg,
				    ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions) throws IOException
    {
	this(arg, registrars, marshalledRegistrars, exceptions,
		validate(registrars, marshalledRegistrars, exceptions));
    }
    
    /**
     * Invariants have been checked, let superclass check, then assign fields.
     * @param arg
     * @param registrars
     * @param marshalledRegistrars
     * @param exceptions
     * @param check
     * @throws IOException 
     */
    private LookupUnmarshalException(GetArg arg,
				    ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions,
				    boolean	       check) throws IOException
    {
	super(arg); // Super has to check it's invariants.
	this.registrars = registrars;
	this.marshalledRegistrars = marshalledRegistrars;
	this.exceptions = exceptions;
    }
    
    /**
     * validation of serial form.
     * @param registrars
     * @param marshalledRegistrars
     * @param exceptions
     * @return
     * @throws InvalidObjectException 
     */
    private static boolean validate(ServiceRegistrar[] registrars,
                                    MarshalledObject[] marshalledRegistrars,
                                    Throwable[]        exceptions) throws InvalidObjectException
    {
    
	if(marshalledRegistrars == null) {
            throw new InvalidObjectException
                            ("LookupUnmarshalException.readObject "
                             +"failure - marshalledRegistrars field is null");
        }//endif
        if(exceptions == null) {
            throw new InvalidObjectException
                                      ("LookupUnmarshalException.readObject "
                                       +"failure - exceptions field is null");
        }//endif
        if(marshalledRegistrars.length == 0) {
            throw new InvalidObjectException
                             ("LookupUnmarshalException.readObject "
                              +"failure - marshalledRegistrars.length == 0");
        }//endif
        if(exceptions.length != marshalledRegistrars.length) {
            throw new InvalidObjectException
                           ("LookupUnmarshalException.readObject failure - "
                            +"exceptions.length ("+exceptions.length
                            +") is not equal to marshalledRegistrars.length "
                            +"("+marshalledRegistrars.length+")");
	}
	return true;
    }
    
    /**
     * Accessor method that returns an array consisting of instances of 
     * <code>ServiceRegistrar</code>, where each element of the array
     * corresponds to a successfully unmarshalled object. Note that a copy
     * is returned on each invocation of this method.
     *
     * @return array of instances of <code>ServiceRegistrar</code>, where
     *         each element corresponds to a successfully unmarshalled object.
     */
    public ServiceRegistrar[] getRegistrars() {
        return registrars != null ? registrars.clone(): null;
    }//end getRegistrars

    /**
     * Accessor method that returns an array consisting of instances of 
     * <code>MarshalledObject</code>, where each element of the array is a
     * marshalled instance of the <code>ServiceRegistrar</code> interface,
     * and corresponds to an object that could not be successfully
     * unmarshalled. Note that a copy is returned on each invocation
     * of this method.
     *
     * @return array of marshalled instances of <code>ServiceRegistrar</code>,
     *         where each element corresponds to an object in which failure
     *         occurred while attempting to unmarshal the object.
     */
    public MarshalledObject[] getMarshalledRegistrars() {
        return marshalledRegistrars.clone();
    }//end getMarshalledRegistrars

    /**
     * Accessor method that returns an array consisting of instances of 
     * <code>Throwable</code>, where each element of the array corresponds
     * to one of the exceptions that occurred during the unmarshalling
     * process. Note that a copy is returned on each invocation
     * of this method.
     * <p>
     * Each element in the return set should be an instance of
     * <code>IOException</code>, <code>ClassNotFoundException</code>, or
     * some unchecked exception. Additionally, there should be a one-to-one
     * correspondence between each element in the array returned by this method
     * and the array returned by the <code>getMarshalledRegistrars</code>
     * method. That is, the i-th element of the set returned by this method
     * should be an instance of the exception that occurred while attempting
     * to unmarshal the i-th element of the set returned by the method
     * <code>getMarshalledRegistrars</code>.
     * </p>
     * @return array of instances of <code>Throwable</code>, where each element
     *         corresponds to one of the exceptions that occurred during
     *         the unmarshalling process.
     */
    public Throwable[] getExceptions() {
        return exceptions.clone();
    }//end getExceptions

    /**
     * Checks the state of this class prior to constructing
     *
     * @param registrars           Array containing the set of instances of
     *                             <code>ServiceRegistrar</code> that were
     *                             successfully unmarshalled.
     *                            
     * @param marshalledRegistrars Array containing the set of marshalled
     *                             <code>ServiceRegistrar</code> instances
     *                             that could not be unmarshalled.
     *                   
     * @param exceptions           Array containing the set of exceptions that
     *                             occurred during the unmarshalling process.
     *
     * @throws java.lang.NullPointerException this exception occurs
     *         when <code>null</code> is input for either the
     *         <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter.
     * @throws java.lang.IllegalArgumentException this exception occurs when
     *         either the <code>marshalledRegistrars</code> parameter or the
     *         <code>exceptions</code> parameter has zero length; or when the
     *         lengths of those two parameters are not equal.
     */
    private static boolean check(ServiceRegistrar[] registrars,
                      MarshalledObject[] marshalledRegistrars,
                      Throwable[]        exceptions) {
        /* Verify the input arguments */
        if(marshalledRegistrars == null) {
            throw new NullPointerException
                                      ("marshalledRegistrars cannot be null");
        }//endif
        if(exceptions == null) {
            throw new NullPointerException("exceptions cannot be null");
        }//endif
        if(marshalledRegistrars.length == 0) {
            throw new IllegalArgumentException
                                        ("marshalledRegistrars has 0 length");
        }//endif
        if(exceptions.length != marshalledRegistrars.length) {
            throw new IllegalArgumentException
                           ("exceptions.length ("+exceptions.length
                            +") is not equal to marshalledRegistrars.length "
                            +"("+marshalledRegistrars.length+")");
        }//endif
        return true;
    }//end check
    
    private void writeObject(ObjectOutputStream out) throws IOException 
    {
	out.defaultWriteObject();
    }

    /** 
     * When an instance of this class is deserialized, this method is
     * automatically invoked. This implementation of this method validates
     * the state of the deserialized instance.
     *
     * @throws InvalidObjectException if the state of the
     *         deserialized instance of this class is found to be invalid.
     */
    private void readObject(ObjectInputStream s)  
                               throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        /* Verify marshalledRegistrars and exceptions fields */
        validate(registrars, marshalledRegistrars, exceptions);

    }//end readObject

}//end class LookupUnmarshalException
