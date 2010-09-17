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
import java.rmi.RemoteException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;

/**
 * A collection of leases being managed by a lease renewal service.
 * <p>
 * Clients of the renewal service organize the leases they wish to
 * have renewed into <em>lease renewal sets</em> (or <em>sets</em>,
 * for short). These sets are represented by objects implementing this
 * interface. The
 * <code>LeaseRenewalService.createLeaseRenewalSet</code> method is
 * provided to create sets. Sets are populated by methods on the sets
 * themselves. Two leases in the same set need not be granted by the
 * same service or have the same expiration time; in addition, they
 * can be added or removed from the set independently.
 * <p>
 * This interface is not a remote interface; each implementation of the
 * renewal service exports proxy objects that implement the
 * <code>LeaseRenewalSet</code> interface that use an
 * implementation-specific protocol to communicate with the actual
 * remote server. All of the proxy methods obey normal RMI remote
 * interface semantics except where explicitly noted. Two proxy objects
 * are equal if they are proxies for the same set created by the same
 * renewal service. Every method invocation (on both a
 * <code>LeaseRenewalService</code> and all the
 * <code>LeaseRenewalSet</code> instances created by that server) is
 * atomic with respect to other invocations.
 * <p>
 * A number of the methods in this class throw
 * <code>RemoteException</code>, each of these may throw the
 * <code>java.rmi.NoSuchObjectException</code> subclass. If a client
 * receives a <code>NoSuchObjectException</code> when calling a method
 * on a renewal set, the client can infer that the set has been
 * destroyed; however, it should not infer that the renewal service
 * has been destroyed.
 * <p>
 * The term <em>client lease</em> is used to refer to a lease that has
 * been placed into a renewal set. Client leases are distinct from the
 * leases that the renewal service grants on renewal sets it has
 * created.
 * <p>
 * Each client lease has two expiration related times associated with
 * it: the <em>desired expiration</em> time for the lease, and the
 * <em>actual expiration</em> time granted when the lease was created
 * or last renewed. The desired expiration represents when the client
 * would like the lease to expire. The actual expiration represents
 * when the lease is going to expire if it is not renewed. Both time
 * values are absolute times, not relative time durations. When a
 * client lease's desired expiration arrives, the lease will be
 * removed from the set without further client intervention.
 * <p>
 * Each client lease also has two other associated attributes: a
 * desired <em>renewal duration</em> and a <em>remaining desired
 * duration</em>. The desired renewal duration is specified by the
 * client (directly or indirectly) when the lease is added to the
 * set. This duration must normally be a positive number, however, it
 * may be <code>Lease.ANY</code> if the lease's desired expiration is
 * <code>Lease.FOREVER</code>. The remaining desired duration is
 * always the desired expiration less the current time.
 * <p>
 * Each time a client lease is renewed, the renewal service will ask
 * for an extension equal to the lease's renewal duration if the
 * renewal duration is:
 * <ul>
 *     <li> Lease.ANY, or 
 *     <li> less than the remaining desired duration, 
 * </ul>
 * 
 * otherwise, it will ask for an extension equal to the lease's
 * remaining desired duration.
 * <p>
 * If a client lease's actual expiration is later than its desired
 * expiration, the renewal service will not renew the lease; the lease
 * will remain in the set until its desired expiration is reached, the
 * set is destroyed, or it is removed by the client.
 * <p>
 * Each set is leased from the renewal service. If the lease on a set
 * expires or is cancelled, the renewal service will destroy the set
 * and take no further action with regard to the client leases in the
 * set. Each lease renewal set has associated with it an expiration
 * warning event that occurs at a client-specified time before the
 * lease on the set expires. Clients can register for warning events
 * using methods provided by the set. A registration for warning
 * events does not have its own lease, but instead is covered by the
 * same lease under which the set was granted.
 * <p>
 * The term <em>definite exception</em> is used to refer to an
 * exception that could be thrown by an operation on a client lease
 * (such as a remote method call) that would be indicative of a
 * permanent failure of the client lease. For purposes of this
 * document, all bad object exceptions, bad invocation exceptions, and
 * <code>LeaseException</code>s are considered to be definite
 * exceptions.
 * <p>
 * Each lease renewal set has associated with it a renewal failure
 * event that will occur in either of two cases: if any client lease
 * in the set reaches its actual expiration before its desired
 * expiration is reached, or if the renewal service attempts to renew
 * a client lease and gets a definite exception. Clients can register
 * for failure events using methods provided by the set. A
 * registration for failure event does not have its own lease, but
 * instead is covered by the same lease under which the set was
 * granted.
 * <p>
 * Once placed in a set, a client lease will stay there until one or
 * more of the following occurs:
 * <ul>
 *     <li> The lease on the set itself expires or is cancelled,
 *          causing destruction of the set
 *     <li> The client lease is removed by the client
 *     <li> The client lease's actual or desired expiration is reached
 *     <li> A renewal attempt on the client lease results in a
 *          definite exception
 * </ul>
 * <p>
 * Each client lease in a set will be renewed as long as it is in the
 * set. If a renewal call throws an indefinite exception, the renewal
 * service should retry the lease renewal until the lease would
 * otherwise be removed from the set. The renewal service will never
 * cancel a client lease. The preferred method of cancelling a client
 * lease is for the client to first remove the lease from the set and
 * then call cancel on it. It is also permissible for the client to
 * cancel the lease without first removing the lease from the set,
 * although this is likely to result in additional network traffic.
 * <p>
 * Client leases get returned to clients in a number of ways (via
 * <code>remove</code> and <code>getLeases</code> calls, as components
 * of events, etc.). In general, they should have their serial format
 * set to <code>Lease.DURATION</code> before being transferred to the
 * client's virtual machine. In some exceptional circumstances, this
 * may not be possible (for example, the client lease was recovered
 * from persistent storage and could not be deserialized in the
 * server's virtual machine). In these cases, it is acceptable to
 * transfer the lease to the client using the
 * <code>Lease.ABSOLUTE</code> serial format.
 * <p>
 * Whenever a client lease gets returned to a client, its actual
 * expiration should reflect either:
 * <ul>
 *     <li> The result of the last successful renewal call that the
 *          renewal service made or
 *     <li> The expiration time the lease originally had when it was
 *          added to the set, if the renewal service has not yet
 *          successfully renewed the lease
 * </ul>
 * 
 * @author Sun Microsystems, Inc.
 * @see LeaseRenewalService
 */
public interface LeaseRenewalSet {
    /**
     * The event id for all <code>RenewalFailureEvent</code> objects.
     *
     * @see RenewalFailureEvent
     */
    final static public long RENEWAL_FAILURE_EVENT_ID = 0;

    /**
     * The event id for all <code>ExpirationWarningEvent</code> objects.
     *
     * @see ExpirationWarningEvent
     */
    final static public long EXPIRATION_WARNING_EVENT_ID = 1;

    /**
     * Include a client lease in the set for a specified duration and
     * with a specified renewal duration.
     * <p>
     * The <code>leaseToRenew</code> argument specifies the lease to be
     * added to the set. An <code>IllegalArgumentException</code> must
     * be thrown if the lease was granted by the renewal service
     * itself. If <code>leaseToRenew</code> is <code>null</code>, a
     * <code>NullPointerException</code> must be thrown.
     * <p>
     * The <code>desiredDuration</code> argument is the number of
     * milliseconds the client would like the lease to remain in the
     * set. It is used to calculate the lease's initial desired
     * expiration by adding <code>desiredDuration</code> to the current
     * time (as viewed by the service). If this causes an overflow, a
     * desired expiration of <code>Long.MAX_VALUE</code> will be
     * used. Unlike a lease duration, the desired duration is
     * unilaterally specified by the client, not negotiated between the
     * client and the service. Note, a negative value for
     * <code>desiredDuration</code> (including <code>Lease.ANY</code>)
     * will result in a desired expiration that is in the past, causing
     * <code>leaseToRenew</code> to be dropped from the set; this action
     * will neither result in an exception or an event.
     * <p>
     * If the actual expiration time of <code>leaseToRenew</code> is
     * less than the current time (as viewed by the renewal service) and
     * the current time is less than the desired expiration time for
     * <code>leaseToRenew</code>, the method will return
     * normally. However, <code>leaseToRenew</code> will be dropped from
     * the set and a renewal failure event will be generated.
     * <p>
     * The <code>renewDuration</code> is the initial renewal duration to
     * associate with <code>leaseToRenew</code> (in milliseconds). If
     * <code>desiredDuration</code> is exactly
     * <code>Long.MAX_VALUE</code>, the <code>renewDuration</code> may
     * be any positive number or <code>Lease.ANY</code>; otherwise it
     * must be a positive number. If these requirements are not met, the
     * renewal service must throw an
     * <code>IllegalArgumentException</code>.
     * <p>
     * Calling this method with a lease that is equivalent to a client
     * lease already in the set will associate the existing client lease
     * in the set with the new desired duration and renew duration. The
     * client lease is not replaced because it is more likely that the
     * renewal service, rather than the client, has an up-to-date lease
     * expiration. The service is more likely to have an up-to-date
     * expiration because the client should not be renewing a lease that
     * it has passed to a lease renewal service unless the lease is
     * removed first. These semantics also allow <code>renewFor</code>
     * to be used in an idempotent fashion.
     *
     * @param leaseToRenew the lease to be added to the renewal set
     * @param desiredDuration the maximum length of time in milliseconds
     *	      the <code>leaseToRenew</code> should remain in the set, or
     *	      <code>Lease.FOREVER</code> which implies there is no
     *	      pre-specified time when the lease should be removed from
     *	      the set
     * @param renewDuration the lease duration to request when renewing
     *	      the lease, unless <code>renewDuration</code> is greater
     *	      than the remainder of the <code>desiredDuration</code>
     * @throws IllegalArgumentException if <code>desiredDuration</code>
     *	       is <code>Lease.FOREVER</code>, <code>renewDuration</code>
     *	       is not a positive value, <code>Lease.FOREVER</code>, or
     *	       <code>Lease.ANY</code>. If <code>desiredDuration</code>
     *	       is not <code>Lease.FOREVER</code>,
     *	       <code>IllegalArgumentException</code> will be thrown if
     *	       <code>renewDuration</code> is not a positive value or
     *	       <code>Lease.ANY</code>. <code>IllegalArgumentException</code>
     *	       will also be thrown if <code>leaseToRenew</code> 
     *	       was granted by this renewal service.
     * @throws NullPointerException if <code>leaseToRenew</code> is
     *	       <code>null</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public void renewFor(Lease leaseToRenew, long desiredDuration,
			 long  renewDuration)
	throws RemoteException;

    /**
     * Include a client lease in the set for a specified duration.
     * <p>
     * Calling this method is equivalent to making the following call 
     * on this set:
     *
     * <pre>
     *     renewFor(leaseToRenew, desiredDuration, Lease.FOREVER)
     * </pre>
     *
     * @param leaseToRenew the lease to be added to the renewal set
     * @param desiredDuration the maximum length of time in milliseconds
     *	      the <code>leaseToRenew</code> should remain in the set, or
     *	      <code>Lease.FOREVER</code> which implies there is no
     *	      pre-specified time when the lease should be removed from
     *	      the set
     * @throws IllegalArgumentException if <code>leaseToRenew</code> 
     *	       was granted by this renewal service
     * @throws NullPointerException if <code>leaseToRenew</code> is
     *	       <code>null</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public void renewFor(Lease leaseToRenew, long desiredDuration)
	throws RemoteException;

    /**
     * Removes the specified lease from set. If the lease is currently
     * in the set it will be returned, otherwise <code>null</code> will
     * be returned. <code>leaseToRemove</code> will not be 
     * canceled by this call.
     *
     * @param leaseToRemove lease to be removed from the set
     * @return the removed lease if it was in the set or
     *	       <code>null</code> if it was not
     * @throws NullPointerException if <code>leaseToRemove</code> is
     *	       <code>null</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public Lease remove(Lease leaseToRemove) 
	throws RemoteException;
    
    /**
     * Returns all the leases currently in the set. 
     *
     * @return the leases in the set. Return a zero length array if
     *	       there are not leases currently in the set.
     * @throws LeaseUnmarshalException if one or more of the leases can
     *	       not be unmarshalled. The throwing of a
     *	       <code>LeaseUnmarshalException</code> represents a,
     *	       possibly transient, failure in the ability to unmarshal
     *	       one or more client leases in the set, it does not
     *	       necessarily imply anything about the state of the renewal
     *	       service or the set themselves.
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public Lease[] getLeases() throws LeaseUnmarshalException, RemoteException;

    /**
     * Register for the expiration warning event associated with this
     * set.
     * <p>
     * This method allows the client to register for notification of
     * the approaching expiration of the set's lease. The
     * <code>listener</code> argument specifies what listener should
     * be notified when the lease is about to expire. The
     * <code>minWarning</code> argument specifies a minimum number of
     * milliseconds before lease expiration that the first event
     * delivery attempt should be made by the service. The service may
     * also make subsequent delivery attempts if the first and any
     * subsequent attempts have been indeterminate. The
     * <code>minWarning</code> argument must be zero or a positive
     * number; if it is not, an <code>IllegalArgumentException</code>
     * must be thrown. If the current expiration of the set's lease is
     * less than <code>minWarning</code> milliseconds away, the event
     * will occur immediately (though it will take time to propagate
     * to the listener).
     * <p>
     * The <code>handback</code> argument to this method specifies an
     * object that will be part of the expiration warning event
     * notification. This mechanism is detailed in the Jini
     * Distributed Event Specification.
     * <p>
     * This method returns the event registration for this event. The
     * <code>Lease</code> object associated with the registration will
     * be equivalent (in the sense of <code>equals</code>) to the
     * <code>Lease</code> on the renewal set. Because the event
     * registration shares a lease with the set, clients that want to
     * just remove their expiration warning registration without
     * destroying the set should use the
     * <code>clearExpirationWarningListener</code> method instead of
     * cancelling the registration's lease. The registration's event
     * ID will be
     * <code>LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID</code>. The
     * source of the registration will be the set. The method must
     * throw a <code>NullPointerException</code> if the
     * <code>listener</code> argument is <code>null</code>. If an
     * event handler has already been specified for this event the
     * current registration is replaced with the new one. Because both
     * registrations are for the same kind of event, the events sent
     * to the new registration must be in the same sequence as the
     * events sent to the old registration.
     *
     * @param listener the listener to be notified when this event
     *	      occurs
     * @param minWarning how long before the lease on the set expires
     *	      should the event be sent
     * @param handback an object to be handed back to the listener when
     *	      the warning event occurs
     * @return an <code>EventRegistration</code> describing the event
     *	       registration
     * @throws IllegalArgumentException if <code>minWarning</code> is
     *	       negative
     * @throws NullPointerException if <code>listener</code> is
     *	       <code>null</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public EventRegistration setExpirationWarningListener(
	                         RemoteEventListener listener, 
				 long                minWarning, 
				 MarshalledObject    handback)
	throws RemoteException;

    /**
     * Remove the listener currently registered for expiration warning
     * events. It is safe to call this method even if no listener is
     * currently registered.
     *
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public void clearExpirationWarningListener()
	throws RemoteException;

    /**
     * Register for the renewal failure event associated with this set.
     * <p>    
     * This method allows the client to register for the event
     * associated with the failure to renew a client lease in the
     * set. These events are generated when a client lease expires
     * while it is still in the set, or when the service attempts to
     * renew a client lease and gets a definite exception. The
     * listener argument specifies the listener to be notified if a
     * client lease could not be renewed.
     * <p>
     * The <code>handback</code> argument specifies an object that
     * will be part of the renewal failure event notification. This
     * mechanism is detailed in the Jini Distributed Event
     * Specification.
     * <p>
     * This method returns the event registration for this event. The
     * <code>Lease</code> object associated with the registration will
     * be equivalent (in the sense of <code>equals</code>) to the
     * <code>Lease</code> on the renewal set. Because the event
     * registration shares a lease with the set, clients that want to
     * just remove their expiration warning registration without
     * destroying the set should use the
     * <code>clearRenewalFailureListener</code> method, instead of
     * cancelling the registration's lease. The registration's event
     * ID will be
     * <code>LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID</code>. The
     * source of the registration will be the set. The method must
     * throw <code>NullPointerException</code> if the
     * <code>listener</code> argument is <code>null</code>. If an
     * event handler has already been specified for this event the
     * current registration is replaced with the new one. The returned
     * event registration must have the same event ID as the replaced
     * registration. Because both registrations are for the same kind
     * of event, the events sent to the new registration must be in
     * the same sequence as the events sent to the old registration.
     *
     * @param listener the listener to be notified when this event
     *	      occurs
     * @param handback an object to be handed back to the listener when
     *	      the warning event occurs
     * @return an <code>EventRegistration</code> describing the event
     *	      registration
     * @throws NullPointerException if <code>listener</code> is
     *	       <code>null</code>
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public EventRegistration setRenewalFailureListener(
				 RemoteEventListener listener, 
				 MarshalledObject    handback)
	throws RemoteException;

    /**
     * Remove the listener currently registered for renewal failure
     * events. It is safe to call this method even if no listener is
     * currently registered.
     *
     * @throws RemoteException if a communication-related exception
     *	       occurs
     */
    public void clearRenewalFailureListener()
	throws RemoteException;

    /**
     * Returns the lease that controls the lifetime of this set. Can
     * be used to extend or end the sets lifetime. Note that this method
     * does not make a remote call.
     *
     * @return the lease that controls the lifetime of this set
     */
    public Lease getRenewalSetLease();
}
