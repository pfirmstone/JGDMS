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
package com.sun.jini.lease;

import com.sun.jini.proxy.MarshalledWrapper;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import net.jini.core.lease.Lease;
import net.jini.io.MarshalledInstance;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

/**
 * Basic implementation of <code>RenewalFailureEvent</code> that
 * defers unmarshalling.
 * <p>
 * The <code>Lease</code> that could not be renewed and any
 * accompanying <code>Throwable</code> will not be deserialized until
 * the appropriate accessor is called.
 *
 * @author Sun Microsystems, Inc.
 * @see LeaseRenewalSet 
 */
public class BasicRenewalFailureEvent extends RenewalFailureEvent {
    private static final long serialVersionUID = 4122133697986606684L;

    /**
     * Exception, in marshalled form, returned by <code>getThrowable</code>
     * method.  May be <code>null</code>.
     *
     * @serial
     */
    private MarshalledInstance marshalledThrowable;

    /**
     * Lease, in marshalled form, returned by <code>getLease</code> method.
     *
     * @serial 
     */
    private MarshalledInstance marshalledLease;

    /**
     * Transient cache of lease returned by <code>getLease</code> method.  
     */
    private transient Lease lease = null;

    /**
     * Transient cache of exception returned by <code>getThrowable</code> 
     * method.
     */
    private transient Throwable throwable;

    /** Whether to verify codebase integrity. */
    private transient boolean verifyCodebaseIntegrity;

    /**
     * Simple constructor.  Note event id is fixed to
     * <code>LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID</code>.
     *
     * @param source the <code>LeaseRenewalSet</code> that generated the event
     * @param seqNum the sequence number of this event
     * @param handback the client handback
     * @param marshalledLease the lease which could not be renewed, in
     *        marshalled form
     * @param marshalledThrowable the first exception that was thrown in the
     *        last chain of renewal failures, in marshalled form.  May be
     *        <code>null</code> in which case <code>getThrowable</code> will
     *        return <code>null</code>.
     */
    public BasicRenewalFailureEvent(LeaseRenewalSet source, 
				    long seqNum,
				    MarshalledObject handback,
				    MarshalledInstance marshalledLease,
				    MarshalledInstance marshalledThrowable) 
    {
	super(source, seqNum, handback);
	this.marshalledThrowable = marshalledThrowable;
	this.marshalledLease = marshalledLease;
    }

    /**
     * Returns the lease that could not be renewed.  When the event is
     * deserialized, the lease is left in marshalled form.  It is only
     * unmarshalled when this call is made.  If a call to this method
     * fails, future calls will attempt to re-unmarshal the lease.
     * Once the lease is successfully unmarshalled it is cached in a
     * transient field so future calls will not result in
     * <code>IOException</code> or
     * <code>ClassNotFoundException</code>.
     *
     * @return the unmarshalled lease
     * @throws IOException if there are problems unmarshalling the lease,
     *	       usually because of some sort of class mismatch
     * @throws ClassNotFoundException if there are problems unmarshalling the
     *	       lease, usually because one of the classes associated with the
     *	       lease's implementation could not be loaded
     * @throws SecurityException if this object was unmarshalled from a stream
     *	       that required codebase integrity, and the integrity of the
     *	       lease's codebase could not be verified
     */
    public Lease getLease() throws IOException, ClassNotFoundException {
	if (lease == null) {
	    lease = (Lease) marshalledLease.get(verifyCodebaseIntegrity);
	}
	return lease;
    };

    /**
     * Returns the exception (if any) that was thrown by the last renewal
     * attempt.
     * <p>
     * When the event is deserialized, the exception is
     * left in marshalled form.  It is only unmarshalled when this call
     * is made.  If a call to this method fails, future calls will
     * attempt to re-unmarshal the exception.  Once the
     * exception is successfully unmarshalled it is
     * cached in a transient field so future calls will not result in
     * <code>IOException</code> or
     * <code>ClassNotFoundException</code>.
     *
     * @return the unmarshalled exception
     * @throws IOException if there are problems unmarshalling the exception,
     *	       usually because of some sort of class mismatch
     * @throws ClassNotFoundException if there are problems unmarshalling the
     *	       exception, usually because one of the classes associated with
     *	       the exception's implementation could not be loaded
     * @throws SecurityException if this object was unmarshalled from a stream
     *	       that required codebase integrity, and the integrity of the
     *	       exception's codebase could not be verified
     */
    public Throwable getThrowable() throws IOException, ClassNotFoundException {
	// Is there a throwable?
	if (marshalledThrowable == null)
	    return null;

	if (throwable == null) {
	    throwable =
		(Throwable) marshalledThrowable.get(verifyCodebaseIntegrity);
	}

	return throwable;
    }

    /* Set transient fields. */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	verifyCodebaseIntegrity = MarshalledWrapper.integrityEnforced(in);
    }
}
