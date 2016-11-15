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

import java.io.IOException;
import java.util.EventObject;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Event generated when a <code>LeaseRenewalManager</code> cannot renew
 * a lease.
 * <p>
 * The <code>LeaseRenewalEvent</code> instances are sent to the
 * <code>LeaseListener</code> (if any) associated with a given lease
 * when the lease was added to the managed set. These events are
 * typically generated because one of the following conditions occur:
 * <ul>
 *   <li> After successfully renewing a lease any number of times and
 *	  experiencing no failures, the <code>LeaseRenewalManager</code>
 *	  determines, prior to the next renewal attempt, that the actual
 *	  expiration time of the lease has passed, implying that any
 *	  further attempt to renew the lease would be fruitless.
 *   <li> An indefinite exception occurs during each attempt to renew a
 *	  lease, from the point that the first such exception occurs
 *	  until the point when the <code>LeaseRenewalManager</code>
 *	  determines that lease's actual expiration time has passed.
 *   <li> A definite exception occurs during a lease renewal attempt.
 * </ul>
 * <p>
 * Note that bad object exceptions, bad invocation exceptions, and
 * <code>LeaseException</code>s are all considered definite
 * exceptions.
 * <p>
 * This class encapsulates information about both the lease for which the
 * failure occurred, as well as information about the condition that
 * caused the renewal attempt to fail.
 * <p>
 * This class is a subclass of the class <code>EventObject</code>,
 * adding the following additional items of abstract state:
 * <ul>
 *   <li> The lease on which the renewal attempt failed, and to which
 *	  the event pertains
 *   <li> The desired expiration time of the affected lease
 *   <li> The <code>Throwable</code> associated with the last renewal
 *	  attempt (if any)
 * </ul>
 * <p>
 * In addition to the methods of the <code>EventObject</code> class,
 * this class defines methods through which this additional state may be
 * retrieved.
 * <p>
 * The source associated with a <code>LeaseRenewalEvent</code> will be
 * the <code>LeaseRenewalManager</code> that generated the event.
 *
 * @author Sun Microsystems, Inc.
 * @see Lease
 * @see LeaseException
 * @see LeaseRenewalManager
 * @see LeaseListener
 * @see EventObject
 */
@AtomicSerial
public class LeaseRenewalEvent extends EventObject {
    private static final long serialVersionUID = -626399341646348302L;

    /**
     * The failed lease.
     *
     * @serial
     */
    private final Lease lease;

    /**
     * The desired expiration of the failed lease.
     *
     * @serial
     */
    private final long expiration;

    /**
     * The exception that caused the failure, if any.
     *
     * @serial
     */
    private final Throwable ex;

    /** 
     * Constructs an instance of this class with the specified state.
     *
     * @param source reference to the instance of the
     *	      <code>LeaseRenewalManager</code> that generated the event
     * @param lease the lease to which the event pertains
     * @param expiration the desired expiration time for the affected
     *	      lease
     * @param ex the <code>Throwable</code> associated with the last
     *	      renewal attempt (if any)
     * @see Lease
     * @see LeaseRenewalManager
     */
    public LeaseRenewalEvent(LeaseRenewalManager source,
			     Lease lease,
			     long expiration,
			     Throwable ex) 
    {
	super(source);
	this.lease = lease;
	this.expiration = expiration;
	this.ex = ex;
    }
    
    public LeaseRenewalEvent(GetArg arg) throws IOException{
	this(null, 
	     arg.get("lease", null, Lease.class), 
	     arg.get("expiration", 0L),
	     arg.get("ex", null, Throwable.class)
	);
    }

    /** 
     * Returns a reference to the lease to which the event pertains.
     *
     * @return the <code>Lease</code> object corresponding to the lease
     *	       on which the renewal attempt failed
     * @see Lease
     */
    public Lease getLease() {
	return lease;
    }

    /** 
     * Returns the desired expiration time of the lease to which event
     * pertains.
     *
     * @return a <code>long</code> value that represents the desired
     *	       expiration time of the lease on which the renewal attempt
     *	       failed
     */
    public long getExpiration() {
	return expiration;
    }

    /** 
     * Returns the exception (if any) that caused the event to be sent.
     * The conditions under which the event may be sent, and the related
     * values returned by this method, are as follows:
     * <ul>
     *
     * <li> When any lease in the managed set has passed its actual
     *	    expiration time, and either the most recent renewal attempt
     *	    was successful or there have been no renewal attempts, the
     *	    <code>LeaseRenewalManager</code> will cease any further
     *	    attempts to renew the lease, and will send a
     *	    <code>LeaseRenewalEvent</code> with no associated exception.
     *	    In this case, invoking this method will return
     *	    <code>null</code>.
     * <li> For any lease from the managed set for which the most recent
     *	    renewal attempt was unsuccessful because of the occurrence
     *	    of a indefinite exception, the
     *	    <code>LeaseRenewalManager</code> will continue to attempt to
     *	    renew the affected lease at the appropriate times until: the
     *	    renewal succeeds, the lease's expiration time has passed, or
     *	    a renewal attempt throws a definite exception. If a definite
     *	    exception is thrown or the lease expires, the
     *	    <code>LeaseRenewalManager</code> will cease any further
     *	    attempts to renew the lease, and will send a
     *	    <code>LeaseRenewalEvent</code> containing the exception
     *	    associated with the last renewal attempt.
     * <li> If, while attempting to renew a lease from the managed set,
     *	    a definite exception is encountered, the
     *	    <code>LeaseRenewalManager</code> will cease any further
     *	    attempts to renew the lease, and will send a
     *	    <code>LeaseRenewalEvent</code> containing the particular
     *	    exception that occurred.
     * </ul>
     *
     * @return an instance of <code>Throwable</code> or
     *	       <code>null</code>, indicating the condition that caused
     *	       the <code>LeaseRenewalManager</code> to fail to renew the
     *	       affected lease
     */
    public Throwable getException() {
	return ex;
    }
}
