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
package com.sun.jini.fiddler;

import net.jini.admin.JoinAdmin;
import com.sun.jini.admin.DestroyAdmin;

import java.rmi.RemoteException;

/**
 * An administrative interface for the Fiddler implementation of the
 * lookup discovery service (see {@linkplain com.sun.jini.fiddler Fiddler}).
 * The comments that follow describe Fiddler with respect to the following
 * methods:
 * <ul><li> setLeaseBound
 *     <li> getLeaseBound
 *     <li> setPersistenceSnapshotWeight
 *     <li> getPersistenceSnapshotWeight
 *     <li> setPersistenceSnapshotThreshold
 *     <li> getPersistenceSnapshotThreshold
 * </ul>
 * The intent of the information contained in this note is to clarify the
 * use of these methods and the effect that use will have on the configuration
 * of Fiddler. Note that the phrase <i>lookup discovery service</i> as used
 * throughout this note refers to the Fiddler implementation of that
 * service. While such a lookup discovery service is running, the following
 * sort of state changes can occur:
 * <ul><li> Clients register and un-register (cancel their lease) with the
 *          lookup discovery service
 *     <li> Leases on the registrations created for a client by the lookup
 *          discovery service are granted, renewed, cancelled and expired
 *     <li> Managed sets of groups, locators and lookup services associated
 *          with the client registrations are added, modified, and removed
 *          from the lookup discovery service
 * </ul>
 * In order to make the lookup discovery service's state persistent across
 * system crashes or network outages, each of the state changes described
 * above are written to a file referred to as the service's <i>log file</i>.
 * The service's log file records, over time, the incremental changes -- or
 * <i>deltas</i> -- made to the lookup discovery service's state.
 * <p>
 * To prevent the log file from growing indefinitely, the lookup
 * discovery service's complete state is intermittently written to another
 * file referred to as the service's <i>snapshot file</i>. When a 
 * <i>snapshot</i> of the service's state is logged to the service's 
 * snapshot file, the service's log file is cleared, and the incremental
 * logging of deltas begins anew. When the service is started for the 
 * first time, the initial period prior to the creation of the first
 * snapshot is referred to as the system <i>ramp-up</i> period. This ramp-up
 * period is the only time where a log file exists without a corresponding
 * snapshot file. 
 * <p>
 * When recovering the system's state after a crash or network outage
 * (or after the lookup discovery service or its ActivationGroup has been 
 * un-registered and then re-registered through the Activation daemon),
 * a "base state" is first recovered by retrieving and applying the
 * contents of the snapshot file (if it exists). Then, if the log
 * file has length greater than zero, its contents are retrieved
 * and applied in order to incrementally recover the state 
 * changes that occurred from the point of the base state.
 * <p>
 * The criteria used by the lookup discovery service to determine
 * exactly when to "take a snapshot" depends on the current size of
 * the log file relative to the size that the snapshot file will be.
 * Note that whereas, the size of the log file depends on the total number 
 * of changes to the lookup discovery service's state, the size of the
 * snapshot file depends on the number of registrations that are 
 * currently active; that is, the more registrations that have been 
 * created and which have valid leases, the larger the snapshot.
 * For example, if only 10 registrations are active, the snapshot 
 * file will be relatively small; but lease renewals may be regularly
 * requested on some of those registrations. The regular lease renewals
 * will result in a very large log file.
 * <p>
 * A balance must be maintained between how large the log file is
 * allowed to get and how often a snapshot is taken; taking snapshots
 * too often can slow down processing significantly. The lookup 
 * discovery service is initially configured with a threshold value
 * and a weighting factor that are employed in a computation that 
 * determines when to take a snapshot. The methods specified by this
 * interface provide ways to access and modify those values. Thus, based
 * on a particular lookup discovery service's makeup, these methods can
 * be used by that service's administrative client to "tune" performance
 * with respect to logging the service's persistent state.
 * <pre>
 * The following comparison is made to determine when to take a snapshot:
 * 
 *   if (logSize >= W*snapshotSize) && (snapshotSize >= T) {
 *       take a snapshot;
 *   }
 *       where W = persistenceSnapshotWeight
 *             T = persistenceSnapshotThreshold
 * </pre>
 * The first comparison is used to ensure that the log file does not
 * grow too large. The second comparison ensures that snapshots are
 * not taken too often.
 * <p>
 * Administrative clients of Fiddler should consider these
 * relationships when using the methods specified by this interface
 * to tune that service's persistence mechanism.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface FiddlerAdmin extends JoinAdmin, DestroyAdmin {
    /**
     * Changes the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     * <p>
     * When a client registers with the lookup discovery service, it
     * requests a desired duration for the lease that the lookup discovery
     * service grants on the registration. As stated in the lookup discovery
     * service specification, the actual duration granted is guaranteed
     * to never be greater than the requested duration. But the Fiddler
     * implementation of the lookup discovery service applies an additional
     * restriction on the duration of the lease that is ultimately granted
     * to the client: when determining the actual duration to grant,
     * Fiddler applies a bound to the duration request. That is, whenever
     * a client requests a lease duration that is greater than the value of
     * this bound, the value of the <em>actual</em> duration assigned by
     * Fiddler will not only be less than the <em>requested</em> value,
     * it will also be less than or equal to the value of the bound.
     * <p>
     * The bound satisfies the definition of a <em>least upper bound</em>
     * on the set of all possible <em>granted</em> durations because the
     * durations granted by Fiddler can be arbitrarily close to the bound,
     * but will never be greater than the bound.
     * <p>
     * Thus, this method is a mechanism for an entity with the appropriate
     * privileges to administratively change the value of the least upper
     * bound that will be applied by the Fiddler implementation of the lookup
     * discovery service when determining the duration to assign to the lease
     * on a requested registration.
     *
     * @param newBound <code>long</code> value representing the new least
     *        upper bound (in milliseconds) on the set of all possible
     *        lease durations that may be granted
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         bound value may or may not have been changed successfully.
     */
    void setLeaseBound(long newBound) throws RemoteException;

    /**
     * Retrieves the least upper bound applied to all lease durations granted
     * by the lookup discovery service.
     *
     * @return <code>long</code> value representing the current least
     *         upper bound (in milliseconds) on the set of all possible
     *         lease durations that may be granted
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     */
    long getLeaseBound() throws RemoteException;

    /**
     * Change the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not
     * to take a "snapshot" of the system state.
     *
     * @param weight weight factor for snapshot size
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         weight factor may or may not have been changed successfully.
     */
    void setPersistenceSnapshotWeight(float weight) throws RemoteException;

    /**
     * Retrieve the weight factor applied by the lookup discovery service
     * to the snapshot size during the test to determine whether or not to
     * take a "snapshot" of the system state.
     * 
     * @return float value corresponding to the weight factor for snapshot
     *         size
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     */
    float getPersistenceSnapshotWeight() throws RemoteException;

    /**
     * Change the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     *
     * @param threshold size threshold for taking a snapshot
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service. When this exception does occur, the
     *         threshold may or may not have been changed successfully.
     */
    void setPersistenceSnapshotThreshold(int threshold) throws RemoteException;

    /**
     * Retrieve the value of the size threshold of the snapshot; which is
     * employed by the lookup discovery service in the test to determine
     * whether or not to take a "snapshot" of the system state.
     * 
     * @return int value corresponding to the size threshold of the snapshot
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         lookup discovery service.
     */
    int getPersistenceSnapshotThreshold() throws RemoteException;
}
