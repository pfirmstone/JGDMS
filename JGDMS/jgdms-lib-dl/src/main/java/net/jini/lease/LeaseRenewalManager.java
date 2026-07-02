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

import java.rmi.RemoteException;
import java.util.Iterator;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import org.apache.river.resource.Service;

/**
 * Provides for the systematic renewal and overall management of a set
 * of leases associated with one or more remote entities on behalf of a
 * local entity.
 * <p>
 * This class removes much of the administrative burden associated with
 * lease renewal. Clients of the renewal manager simply give their
 * leases to the manager and the manager renews each lease as necessary
 * to achieve a <em>desired expiration</em> time (which may be later
 * than the lease's current <em>actual expiration</em> time). Failures
 * encountered while renewing a lease can optionally be reflected to the
 * client via <code>LeaseRenewalEvent</code> instances.
 * <p>
 * Note that this class is not remote. Entities wishing to use this
 * class must create an instance of this class in their own virtual
 * machine to locally manage the leases granted to them. If the virtual
 * machine that the manager was created in exits or crashes, the renewal
 * manager will be destroyed.
 * <p>
 * The <code>LeaseRenewalManager</code> distinguishes between two time
 * values associated with lease expiration: the <em>desired
 * expiration</em> time for the lease, and the <em>actual
 * expiration</em> time granted when the lease is created or last
 * renewed. The desired expiration represents when the client would like
 * the lease to expire. The actual expiration represents when the lease
 * is going to expire if it is not renewed. Both time values are
 * absolute times, not relative time durations. The desired expiration
 * time can be retrieved using the renewal manager's
 * <code>getExpiration</code> method. The actual expiration time of a
 * lease object can be retrieved by invoking the lease's
 * <code>getExpiration</code> method.
 * <p>
 * Each lease in the managed set also has two other associated
 * attributes: a desired <em>renewal duration</em>, and a <em>remaining
 * desired duration</em>. The desired renewal duration is specified
 * (directly or indirectly) when the lease is added to the set. This
 * duration must normally be a positive number; however, it may be
 * <code>Lease.ANY</code> if the lease's desired expiration is
 * <code>Lease.FOREVER</code>. The remaining desired duration is always
 * the desired expiration less the current time.
 * <p>
 * Each time a lease is renewed, the renewal manager will ask for an
 * extension equal to the lease's renewal duration if the renewal
 * duration is:
 * <ul>
 * <li> <code>Lease.ANY</code>, or
 * <li> less than the remaining desired duration,
 * </ul>
 * otherwise it will ask for an extension equal to the lease's remaining
 * desired duration.
 * <p>
 * Once a lease is given to a lease renewal manager, the manager will
 * continue to renew the lease until one of the following occurs:
 * <ul>
 * <li> The lease's desired or actual expiration time is reached.
 * <li> An explicit removal of the lease from the set is requested via a
 *	<code>cancel</code>, <code>clear</code>, or <code>remove</code>
 *	call on the renewal manager.
 * <li> The renewal manager tries to renew the lease and gets a bad
 *	object exception, bad invocation exception, or
 *	<code>LeaseException</code>.
 * </ul>
 * <p>
 * The methods of this class are appropriately synchronized for
 * concurrent operation. Additionally, this class makes certain
 * guarantees with respect to concurrency. When this class makes a
 * remote call (for example, when requesting the renewal of a lease),
 * any invocations made on the methods of this class will not be
 * blocked. Similarly, this class makes a reentrancy guarantee with
 * respect to the listener objects registered with this class. Should
 * this class invoke a method on a registered listener (a local call),
 * calls from that method to any other method of this class are
 * guaranteed not to result in a deadlock condition.
 *
 * <p>
 * <b>Implementation.</b> Since release 3.1.1 this class is a thin, delegating
 * <i>facade</i>. Its public constructors and methods are unchanged; each
 * instance holds a {@link LeaseRenewalManagerSpi} delegate obtained from a
 * {@link LeaseRenewalManagerFactory} that is resolved once, via
 * {@link org.apache.river.resource.Service}, from the classpath. The
 * implementation (which uses virtual threads) lives in a separate,
 * non-downloadable jar so that this download jar can be compiled for the
 * widest range of client JVMs. If no {@code LeaseRenewalManagerFactory} is
 * found, construction throws {@link IllegalStateException}.
 *
 * @author Sun Microsystems, Inc.
 * @see Lease
 * @see net.jini.core.lease.LeaseException
 * @see LeaseRenewalEvent
 *
 *  <!-- Implementation Specifics -->
 *
 * The following implementation-specific items are discussed below:
 * <ul>
 * <li><a href="#configEntries">Configuring LeaseRenewalManager</a>
 * <li><a href="#logging">Logging</a>
 * <li><a href="#algorithm">The renewal algorithm</a>
 * </ul>
 *
 * <a name="configEntries"><b>Configuring LeaseRenewalManager</b></a>
 *
 * This implementation of <code>LeaseRenewalManager</code> supports the
 * following configuration entries, with component
 * <code>net.jini.lease.LeaseRenewalManager</code>:
 *
 * <table summary="Describes the renewBatchTimeWindow configuration entry"
 *	  border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2"><code>
 *	 renewBatchTimeWindow</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>5 * 60 * 1000 // 5 minutes</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description: <td> The maximum number of milliseconds earlier than
 *     a lease would typically be renewed to allow it to be renewed in
 *     order to permit batching its renewal with that of other
 *     leases. The value must not be negative. This entry is obtained
 *     in the constructor.
 * </table>
 * <table summary="Describes the roundTripTime configuration entry"
 *	  border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2"><code>
 *	 roundTripTime</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> <code>long</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>10 * 1000 // 10 seconds</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description: <td> The worst-case latency, expressed in milliseconds,
 *     to assume for a remote call to renew a lease. The value must be greater
 *     than zero. Unrealistically low values for this entry may
 *     result in failure to renew a lease. Leases managed by this manager
 *     should have durations exceeding the <code>roundTripTime</code>.
 *     This entry is obtained in the constructor.
 * </table>
 * <table summary="Describes the executorService configuration entry"
 *	  border="0" cellpadding="2">
 *   <tr valign="top">
 *     <th scope="col">&#X2022;
 *     <th scope="col" align="left" colspan="2"><code>
 *	 executorService</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Type: <td> {@link java.util.concurrent.ExecutorService}
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Default: <td> <code>new ThreadPoolExecutor(1,11,15,TimeUnit.SECONDS,
 *     new LinkedBlockingQueue())</code>
 *   <tr valign="top"> <td> &nbsp; <th scope="row" align="right">
 *     Description: <td> The object used to manage queuing tasks
 *     involved with renewing leases and sending notifications. The
 *     value must not be <code>null</code>. The default value creates
 *     a maximum of 11 threads for performing operations, waits 15
 *     seconds before removing idle threads.
 * </table>
 * <p>
 * <a name="logging"><b>Logging</b></a>
 * </p>
 * <p>
 * This implementation uses the {@link java.util.logging.Logger} named
 * <code>net.jini.lease.LeaseRenewalManager</code> to log information at
 * the following logging levels: </p>
 *
 * <table border="1" cellpadding="5"
 *	  summary="Describes logging performed by the
 *		   LeaseRenewalManager at different logging levels">
 *
 * <caption><b><code>net.jini.lease.LeaseRenewalManager</code></b></caption>
 *
 * <tr> <th scope="col"> Level <th scope="col"> Description
 *
 * <tr> <td> {@link org.apache.river.logging.Levels#FAILED FAILED}
 *	<td> Lease renewal failure events, or leases that expire before
 *           reaching the desired expiration time
 *
 * <tr> <td> {@link org.apache.river.logging.Levels#HANDLED HANDLED}
 *	<td> Lease renewal attempts that produce indefinite exceptions
 *
 * <tr> <td> {@link java.util.logging.Level#FINE FINE}
 *	<td> Adding and removing leases, lease renewal attempts, and desired
 *	     lease expiration events
 *
 * </table>
 * <p>
 *
 * For a way of using the <code>FAILED</code> and <code>HANDLED</code> logging
 * levels in standard logging configuration files, see the
 * {@link org.apache.river.logging.LogManager} class.</p>
 * <p>
 * <a name="algorithm"><b>The renewal algorithm</b></a></p>
 * <p>
 * The time at which a lease is scheduled for renewal is based on the
 * expiration time of the lease, possibly adjusted to account for the
 * latency of the remote renewal call. The configuration entry
 * <code>roundTripTime</code>, which defaults to ten seconds, represents
 * the total time to make the remote call.</p>
 * <p>
 * The following pseudocode was derived from the code which computes
 * the renewal time. In this code, <code>rtt</code> represents the
 * value of the <code>roundTripTime</code>:</p>
 *
 * <pre>
 *          endTime = lease.getExpiration();
 *          delta = endTime - now;
 *          if (delta &lt;= rtt * 2) {
 *	        delta = rtt;
 *          } else if (delta &lt;= rtt * 8) {
 *	        delta /= 2;
 *          } else if (delta &lt;= 1000 * 60 * 60 * 24 * 7) {
 *	        delta /= 8;
 *          } else if (delta &lt;= 1000 * 60 * 60 * 24 * 14) {
 *	        delta = 1000 * 60 * 60 * 24;
 *          } else {
 *	        delta = 1000 * 60 * 60 * 24 * 3;
 *          }
 *          renew = endTime - delta;
 *</pre>
 * <p>
 * It is important to note that <code>delta</code> is never less than
 * <code>rtt</code> when the renewal time is computed. A lease which
 * would expire within this time range will be scheduled for immediate
 * renewal. The use of very short lease durations (at or below <code>rtt</code>)
 * can cause the renewal manager to effectively ignore the lease duration
 * and repeatedly schedule the lease for immediate renewal.
 * </p><p>
 * If an attempt to renew a lease fails with an indefinite exception, a
 * renewal is rescheduled with an updated renewal time as computed by the
 * following pseudocode:</p>
 *
 * <pre>
 *          delta = endTime - renew;
 *          if (delta &gt; rtt) {
 *              if (delta &lt;= rtt * 3) {
 *	            delta = rtt;
 *              } else if (delta &lt;= 1000 * 60 * 60) {
 *	            delta /= 3;
 *              } else if (delta &lt;= 1000 * 60 * 60 * 24) {
 *	            delta = 1000 * 60 * 30;
 *              } else if (delta &lt;= 1000 * 60 * 60 * 24 * 7) {
 *	            delta = 1000 * 60 * 60 * 3;
 *              } else {
 *	            delta = 1000 * 60 * 60 * 8;
 *              }
 *              renew += delta;
 *          }
 * </pre>
 *
 * Client leases are maintained in a collection sorted by descending renewal
 * time. A renewal thread is spawned whenever the renewal time of the last lease
 * in the collection is reached. This renewal thread examines all of the leases
 * in the collection whose renewal time falls within
 * <code>renewBatchTimeWindow</code> milliseconds of the renewal time of the
 * last lease. If any of these leases can be batch renewed with the last lease (as
 * determined by calling the {@link Lease#canBatch canBatch} method of
 * the last lease) then a {@link net.jini.core.lease.LeaseMap} is created, all
 * eligible leases are added to it and the
 * {@link net.jini.core.lease.LeaseMap#renewAll} method is called. Otherwise, the
 * last lease is renewed directly.
 * <p>
 * The <code>ExecutorService</code> that manages the renewal threads has a bound on
 * the number of simultaneous threads it will support. The renewal time of
 * leases may be adjusted earlier in time to reduce the likelihood that the
 * renewal of a lease will be delayed due to exhaustion of the thread pool.
 * Actual renewal times are determined by starting with the lease with the
 * latest (farthest off) desired renewal time and working backwards.  When
 * computing the actual renewal time for a lease, the renewals of all leases
 * with later renewal times, which will be initiated during the round trip time
 * of the current lease's renewal, are considered.  If using the desired
 * renewal time for the current lease would result in more in-progress renewals
 * than the number of threads allowed, the renewal time of the current lease is
 * shifted earlier in time, such that the maximum number of threads is not
 * exceeded.
 *
 */

public class LeaseRenewalManager {

    /**
     * Holder that resolves the single {@link LeaseRenewalManagerFactory} once,
     * lazily and thread-safely (class-init happens under the JVM's
     * initialization lock).
     */
    private static final class FactoryHolder {

        static final LeaseRenewalManagerFactory FACTORY = resolve();

        private static LeaseRenewalManagerFactory resolve() {
            Iterator<LeaseRenewalManagerFactory> it = Service.providers(
                    LeaseRenewalManagerFactory.class,
                    LeaseRenewalManager.class.getClassLoader());
            if (it.hasNext()) {
                return it.next();
            }
            return null;
        }
    }

    /**
     * Returns the resolved factory, or throws a clear diagnostic if none is on
     * the classpath.
     */
    static LeaseRenewalManagerFactory factory() {
        LeaseRenewalManagerFactory f = FactoryHolder.FACTORY;
        if (f == null) {
            throw new IllegalStateException("no net.jini.lease.LeaseRenewalManagerFactory on the classpath — add the jgdms-lib dependency");
        }
        return f;
    }

    /** The lease-renewal implementation this facade delegates to. */
    private final LeaseRenewalManagerSpi impl;

    /**
     * No-argument constructor that creates an instance of this class
     * that initially manages no leases.
     */
    public LeaseRenewalManager() {
        this.impl = factory().newLeaseRenewalManager(this);
    }

    /**
     * Constructs an instance of this class that initially manages no leases
     * and that uses <code>config</code> to control implementation-specific
     * details of the behavior of the instance created.
     *
     * @param config supplies entries that control the configuration of this
     *	      instance
     * @throws ConfigurationException if a problem occurs when obtaining
     *	       entries from the configuration
     * @throws NullPointerException if the configuration is <code>null</code>
     */
    public LeaseRenewalManager(Configuration config)
	throws ConfigurationException
    {
        this.impl = factory().newLeaseRenewalManager(this, config);
    }

    /**
     * Constructs an instance of this class that will initially manage a
     * single lease. Employing this form of the constructor is
     * equivalent to invoking the no-argument form of the constructor
     * followed by an invocation of the three-argument form of the
     * <code>renewUntil</code> method. See <code>renewUntil</code> for
     * details on the arguments and what exceptions may be thrown by
     * this constructor.
     *
     * @param lease reference to the initial lease to manage
     * @param desiredExpiration the desired expiration for
     *	      <code>lease</code>
     * @param listener reference to the <code>LeaseListener</code>
     *	      object that will receive notifications of any exceptional
     *	      conditions that occur during renewal attempts. If
     *	      <code>null</code> no notifications will be sent.
     * @throws NullPointerException if <code>lease</code> is
     *	       <code>null</code>
     * @see LeaseListener
     * @see #renewUntil
     */
    public LeaseRenewalManager(Lease lease,
			       long desiredExpiration,
			       LeaseListener listener)
    {
        this.impl = factory().newLeaseRenewalManager(this, lease,
                desiredExpiration, listener);
    }

    /**
     * Include a lease in the managed set until a specified time.
     * <p>
     * If <code>desiredExpiration</code> is <code>Lease.ANY</code>
     * calling this method is equivalent the following call:
     * <pre>
     *     renewUntil(lease, Lease.FOREVER, Lease.ANY, listener)
     * </pre>
     * otherwise it is equivalent to this call:
     * <pre>
     *     renewUntil(lease, desiredExpiration, Lease.FOREVER, listener)
     * </pre>
     * <p>
     * @param lease the <code>Lease</code> to be managed
     * @param desiredExpiration when the client wants the lease to
     *	      expire, in milliseconds since the beginning of the epoch
     * @param listener reference to the <code>LeaseListener</code>
     *	      object that will receive notifications of any exceptional
     *	      conditions that occur during renewal attempts. If
     *	      <code>null</code> no notifications will be sent.
     * @throws NullPointerException if <code>lease</code> is
     *	       <code>null</code>
     * @see #renewUntil
     */
    public final void renewUntil(Lease lease,
			   long desiredExpiration,
			   LeaseListener listener)
    {
	impl.renewUntil(lease, desiredExpiration, listener);
    }

    /**
     * Include a lease in the managed set until a specified time and
     * with a specified renewal duration.
     * <p>
     * This method takes as arguments: a reference to the lease to
     * manage, the desired expiration time of the lease, the renewal
     * duration time for the lease, and a reference to the
     * <code>LeaseListener</code> object that will receive notification
     * of exceptional conditions when attempting to renew this
     * lease. The <code>LeaseListener</code> argument may be
     * <code>null</code>.
     * <p>
     * If the <code>lease</code> argument is <code>null</code>, a
     * <code>NullPointerException</code> will be thrown. If the
     * <code>desiredExpiration</code> argument is
     * <code>Lease.FOREVER</code>, the <code>renewDuration</code>
     * argument may be <code>Lease.ANY</code> or any positive value;
     * otherwise, the <code>renewDuration</code> argument must be a
     * positive value. If the <code>renewDuration</code> argument does
     * not meet these requirements, an
     * <code>IllegalArgumentException</code> will be thrown.
     * <p>
     * If the lease passed to this method is already in the set of
     * managed leases, the listener object, the desired expiration, and
     * the renewal duration associated with that lease will be replaced
     * with the new listener, desired expiration, and renewal duration.
     * <p>
     * The lease will remain in the set until one of the following
     * occurs:
     * <ul>
     * <li> The lease's desired or actual expiration time is reached.
     * <li> An explicit removal of the lease from the set is requested
     *	    via a <code>cancel</code>, <code>clear</code>, or
     *	    <code>remove</code> call on the renewal manager.
     * <li> The renewal manager tries to renew the lease and gets a bad
     *	    object exception, bad invocation exception, or
     *	    <code>LeaseException</code>.
     * </ul>
     * <p>
     * This method will interpret the value of the
     * <code>desiredExpiration</code> argument as the desired absolute
     * system time after which the lease is no longer valid. This
     * argument provides the ability to indicate an expiration time that
     * extends beyond the actual expiration of the lease. If the value
     * passed for this argument does indeed extend beyond the lease's
     * actual expiration time, then the lease will be systematically
     * renewed at appropriate times until one of the conditions listed
     * above occurs. If the value is less than or equal to the actual
     * expiration time, nothing will be done to modify the time when the
     * lease actually expires. That is, the lease will not be renewed
     * with an expiration time that is less than the actual expiration
     * time of the lease at the time of the call.
     * <p>
     * If the <code>LeaseListener</code> argument is a
     * non-<code>null</code> object reference, it will receive
     * notification of exceptional conditions occurring upon a renewal
     * attempt of the lease. In particular, exceptional conditions
     * include the reception of a <code>LeaseException</code>, bad
     * object exception, or bad invocation exception (collectively these
     * are referred to as <em>definite exceptions</em>) during a renewal
     * attempt or the lease's actual expiration being reached before its
     * desired expiration.
     * <p>
     * If a definite exception occurs during a lease renewal request,
     * the exception will be wrapped in an instance of the
     * <code>LeaseRenewalEvent</code> class and sent to the listener.
     * <p>
     * If an indefinite exception occurs during a renewal request for
     * the lease, renewal requests will continue to be made for that
     * lease until: the lease is renewed successfully, a renewal attempt
     * results in a definite exception, or the lease's actual expiration
     * time has been exceeded. If the lease cannot be successfully
     * renewed before its actual expiration is reached, the exception
     * associated with the most recent renewal attempt will be wrapped
     * in an instance of the <code>LeaseRenewalEvent</code> class and
     * sent to the listener.
     * <p>
     * If the lease's actual expiration is reached before the lease's
     * desired expiration time, and either 1) the last renewal attempt
     * succeeded or 2) there have been no renewal attempts, a
     * <code>LeaseRenewalEvent</code> containing a <code>null</code>
     * exception will be sent to the listener.
     *
     * @param lease the <code>Lease</code> to be managed
     * @param desiredExpiration when the client wants the lease to
     *	      expire, in milliseconds since the beginning of the epoch
     * @param renewDuration the renewal duration to associate with the
     *	      lease, in milliseconds
     * @param listener reference to the <code>LeaseListener</code>
     *	      object that will receive notifications of any exceptional
     *	      conditions that occur during renewal attempts. If
     *	      <code>null</code>, no notifications will be sent.
     * @throws NullPointerException if <code>lease</code> is
     *	       <code>null</code>
     * @throws IllegalArgumentException if <code>renewDuration</code> is
     *	       invalid
     * @see LeaseRenewalEvent
     * @see net.jini.core.lease.LeaseException
     */
    public void renewUntil(Lease lease,
			   long desiredExpiration,
			   long renewDuration,
			   LeaseListener listener)
    {
	impl.renewUntil(lease, desiredExpiration, renewDuration, listener);
    }

    /**
     * Include a lease in the managed set for a specified duration.
     * <p>
     * Calling this method is equivalent the following call:
     * <pre>
     *     renewFor(lease, desiredDuration, Lease.FOREVER, listener)
     * </pre>
     *
     * @param lease reference to the new lease to manage
     * @param desiredDuration the desired duration (relative time) that
     *	      the caller wants <code>lease</code> to be valid for, in
     *	      milliseconds
     * @param listener reference to the <code>LeaseListener</code>
     *	      object that will receive notifications of any exceptional
     *	      conditions that occur during renewal attempts. If
     *	      <code>null</code>, no notifications will be sent.
     * @throws NullPointerException if <code>lease</code> is
     *	       <code>null</code>
     * @see #renewFor
     */
    public void renewFor(Lease lease, long desiredDuration,
			 LeaseListener listener)
    {
	impl.renewFor(lease, desiredDuration, listener);
    }

    /**
     * Include a lease in the managed set for a specified duration and
     * with specified renewal duration.
     * <p>
     * The semantics of this method are similar to those of the
     * four-argument form of <code>renewUntil</code>, with
     * <code>desiredDuration</code> + current time being used for the
     * value of the <code>desiredExpiration</code> argument of
     * <code>renewUntil</code>. The only exception to this is that, in
     * the context of <code>renewFor</code>, the value of the
     * <code>renewDuration</code> argument may only be
     * <code>Lease.ANY</code> if the value of the
     * <code>desiredDuration</code> argument is <em>exactly</em>
     * <code>Lease.FOREVER.</code>
     * <p>
     * This method tests for arithmetic overflow in the desired
     * expiration time computed from the value of
     * <code>desiredDuration</code> argument
     * (<code>desiredDuration</code> + current time). Should such
     * overflow be present, a value of <code>Lease.FOREVER</code> is
     * used to represent the lease's desired expiration time.
     *
     * @param lease reference to the new lease to manage
     * @param desiredDuration the desired duration (relative time) that
     *	      the caller wants <code>lease</code> to be valid for, in
     *	      milliseconds
     * @param renewDuration the renewal duration to associate with the
     *	      lease, in milliseconds
     * @param listener reference to the <code>LeaseListener</code>
     *	      object that will receive notifications of any exceptional
     *	      conditions that occur during renewal attempts. If
     *	      <code>null</code>, no notifications will be sent.
     * @throws NullPointerException if <code>lease</code> is
     *	       <code>null</code>
     * @throws IllegalArgumentException if <code>renewDuration</code> is
     *	       invalid
     * @see #renewUntil
     */
    public void renewFor(Lease lease,
			 long desiredDuration,
			 long renewDuration,
			 LeaseListener listener)
    {
	impl.renewFor(lease, desiredDuration, renewDuration, listener);
    }

    /**
     * Returns the current desired expiration time associated with a
     * particular lease, (not the actual expiration that was granted
     * when the lease was created or last renewed).
     *
     * @param lease the lease the caller wants the current desired
     *	      expiration for
     * @return a <code>long</code> value corresponding to the current
     *	       desired expiration time associated with <code>lease</code>
     * @throws UnknownLeaseException if the lease passed to this method
     *	       is not in the set of managed leases
     * @see UnknownLeaseException
     * @see #setExpiration
     */
    public long getExpiration(Lease lease)
	throws UnknownLeaseException
    {
	return impl.getExpiration(lease);
    }

    /**
     * Replaces the current desired expiration of a given lease from the
     * managed set with a new desired expiration time.
     * <p>
     * Note that an invocation of this method with a lease that is
     * currently a member of the managed set is equivalent to an
     * invocation of the <code>renewUntil</code> method with the lease's
     * current listener as that method's <code>listener</code>
     * argument. Specifically, if the value of the
     * <code>expiration</code> argument is less than or equal to the
     * lease's current desired expiration, this method takes no action.
     *
     * @param lease the lease whose desired expiration time should be
     *	      replaced
     * @param expiration <code>long</code> value representing the new
     *	      desired expiration time for the <code>lease</code>
     *	      argument
     * @throws UnknownLeaseException if the lease passed to this method
     *	       is not in the set of managed leases
     * @see #renewUntil
     * @see UnknownLeaseException
     * @see #getExpiration
     */
    public void setExpiration(Lease lease, long expiration)
	throws UnknownLeaseException
    {
	impl.setExpiration(lease, expiration);
    }

    /**
     * Removes a given lease from the managed set, and cancels it.
     * <p>
     * Note that even if an exception is thrown as a result of the
     * cancel operation, the lease will still have been removed from the
     * set of leases managed by this class. Additionally, any exception
     * thrown by the <code>cancel</code> method of the lease object
     * itself may also be thrown by this method.
     *
     * @param lease the lease to remove and cancel
     * @throws UnknownLeaseException if the lease passed to this method
     *	       is not in the set of managed leases
     * @throws RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and
     *         the server. When this exception does occur, the lease may
     *         or may not have been successfully cancelled, (but the
     *         lease is guaranteed to have been removed from the managed
     *         set).
     * @see Lease#cancel
     * @see UnknownLeaseException
     */
    public void cancel(Lease lease)
	throws UnknownLeaseException, RemoteException
    {
	impl.cancel(lease);
    }

    public void close(){
        impl.close();
    }

    /**
     * Removes a given lease from the managed set of leases; but does
     * not cancel the given lease.
     *
     * @param lease the lease to remove from the managed set
     * @throws UnknownLeaseException if the lease passed to this method
     *         is not in the set of managed leases
     * @see UnknownLeaseException
     */
    public void remove(Lease lease) throws UnknownLeaseException {
	impl.remove(lease);
    }

    /**
     * Removes all leases from the managed set of leases. This method
     * does not request the cancellation of the removed leases.
     */
    public void clear() {
	impl.clear();
    }
}
