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
package com.sun.jini.test.share.reggie;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.entry.Entry;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.admin.JoinAdmin;
//import com.sun.jini.admin.StorageLocationAdmin;
import com.sun.jini.admin.DestroyAdmin;

/**
 * An administrative interface for the com.sun.jini.test.share.reggie
 * implementation of the lookup service.
 *
 * The algorithm for granting leases is adaptive, and is configured
 * using the following parameters:
 * <ul>
 * <li> lower bound on the maximum service lease (minMaxServiceLease)
 * <li> lower bound on the maximum event lease (minMaxEventLease)
 * <li> minimum average interval between lease renewals (minRenewalInterval)
 * </ul>
 * The idea is that you don't want renewals coming in too frequently (you
 * don't want them coming faster than the lookup service can process them,
 * and you want to leave time for other requests), but you still want
 * renewals to be relatively timely (so that the information in the
 * lookup service is fresh).  The lower bounds are the ideal, but the
 * actual will vary if the ideal would keep the lookup service too busy.
 * <p>
 * The maximum service lease granted is never less than minMaxServiceLease,
 * and the maximum event lease granted is never less than minMaxEventLease.
 * However, longer leases will be granted if, based on the current number of
 * service and event registrations, the expected average time between lease
 * renewals would be less than minRenewalInterval.  When longer leases must
 * be granted to maintain the required renewal interval, the ratio of the
 * maximum service lease to the maximum event lease is kept constant.  The
 * computation of the expected average renewal interval makes the simplifying
 * assumptions that all lease requests are for the maximum amount of time
 * (for their respective lease type), all leases will be renewed, and the
 * renewals will be uniformly distributed in time.  At least the first two
 * should be reasonable assumptions in the steady state.
 * <p>
 * Lease requests for Lease.ANYLENGTH are treated the same as Lease.FOREVER.
 * <p>
 * The remaining comments here describe the Registrar with respect to
 * the following set of method pairs:
 * <ul>
 *  <li> setLogFileWeight()          and getLogFileWeight()
 *  <li> setSnapshotWeight()         and getSnapshotWeight()
 *  <li> setLogToSnapshotThreshold() and getLogToSnapshotThreshold()
 * </ul>
 * The intent of the information contained in this note is to 
 * clarify the use of these methods and the effect that use will have
 * on a lookup service's configuration.
 * <p>
 * While a lookup service is running, services are added and removed
 * from the lookup service; attributes are added, modified and deleted
 * from the registered services; service leases and/or event leases
 * are requested, renewed and expired. In order to make the lookup
 * service's state persistent across system crashes or network outages,
 * each of the state changes just described are written to a file
 * referred to as the "log file". Thus, the log file records, over
 * time, the incremental changes -- or "deltas" -- made to the lookup
 * service's state.
 * <p>
 * To prevent the log file from growing indefinitely, the lookup
 * service's complete state is intermittently written to another
 * file referred to as a "snapshot file". When the current state is
 * written to the snapshot file, the log file is cleared and the
 * incremental logging of deltas begins again. The period prior to
 * the creation of the first snapshot is referred to as the "system
 * ramp-up"; this is the only period where a log file exists without
 * a corresponding snapshot file. 
 * <p>
 * When recovering the system's state after a crash or network outage
 * (or after the lookup service or its ActivationGroup has been 
 * un-registered and then re-registered through the Activation daemon),
 * a "base state" is first recovered by retrieving and applying the
 * contents of the snapshot file (if it exists). Then, if the log
 * file has length greater than zero, its contents are retrieved
 * and applied in order to incrementally recover the state 
 * changes that occurred from the point of the base state.
 * <p>
 * The criteria used by the lookup service to determine exactly when
 * to "take a snapshot" depends on the current size of the log file
 * relative to the size that the snapshot file will be. Note that 
 * whereas, the size of the log file depends on the total number 
 * of changes to the lookup service's state, the size of the snapshot 
 * file depends on the number of services currently registered; that
 * is, the more services registered, the larger the snapshot.
 * For example, if only 10 services are registered, the snapshot 
 * file will be relatively small; but lease renewals may be regularly
 * requested on some of those services. The regular lease renewals
 * will result in a very large log file.
 * <p>
 * A balance must be maintained between how large the log file is
 * allowed to get and how often a snapshot is taken; taking snapshots
 * too often can slow down processing significantly. The lookup 
 * service is initially configured with a threshold value and a
 * weighting factor that are employed in a computation that 
 * determines when to take a snapshot. The methods referenced above
 * (and declared below) provide ways to retrieve and change these
 * values. Thus, based on a particular lookup service's makeup,
 * these methods can be used by a lookup service's administrative
 * client to "tune" performance with respect to logging persistent
 * state.
 * <pre>
 * The following comparison is made to determine when to take a snapshot:
 * 
 *   if ( logSize >= (W*snapshotSize) && (snapshotSize >= T) {
 *       take a snapshot;
 *   }
 *       where W = snapshotWeight
 *             T = logToSnapshotThreshold
 * </pre>
 * The first comparison is used to ensure that the log file does not
 * grow too large. The second comparison ensures that snapshots are
 * not taken too often.
 * <p>
 * The Administrative client of a lookup service should make note of
 * these relationships when using the methods below to tune the
 * persistence mechanism.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface RegistrarAdmin
    //    extends DiscoveryAdmin, StorageLocationAdmin, DestroyAdmin, JoinAdmin
    extends DiscoveryAdmin, DestroyAdmin, JoinAdmin
{
    /**
     * Change the lower bound for the maximum value allowed by the
     * lookup service for any service lease, in milliseconds.
     *
     * @param leaseDuration lower bound for maximum service lease,
     * in milliseconds
     */
    void setMinMaxServiceLease(long leaseDuration) throws RemoteException;

    /**
     * Retrieve the lower bound for the maximum value allowed by the
     * lookup service for any service lease, in milliseconds.
     */
    long getMinMaxServiceLease() throws RemoteException;

    /**
     * Change the lower bound for the maximum value allowed by the
     * lookup service for any event lease, in milliseconds.
     *
     * @param leaseDuration lower bound for maximum event lease,
     * in milliseconds
     */
    void setMinMaxEventLease(long leaseDuration) throws RemoteException;

    /**
     * Retrieve the lower bound for the maximum value allowed by the
     * lookup service for any event lease, in milliseconds.
     */
    long getMinMaxEventLease() throws RemoteException;

    /**
     * Change the minimum average interval between lease renewals, in
     * milliseconds.
     *
     * @param interval minimum average interval between lease renewals,
     * in milliseconds
     */
    void setMinRenewalInterval(long interval) throws RemoteException;

    /**
     * Retrieve the minimum average interval between lease renewals, in
     * milliseconds.
     */
    long getMinRenewalInterval() throws RemoteException;

    /**
     * Change the weight factor applied by the lookup service to the 
     * snapshot size during the test to determine whether or not to
     * take a "snapshot" of the system state.
     *
     * @param weight weight factor for snapshot size
     */
    void setSnapshotWeight(float weight) throws RemoteException;

    /**
     * Retrieve the weight factor applied by the lookup service to the 
     * snapshot size during the test to determine whether or not to
     * take a "snapshot" of the system state.
     */
    float getSnapshotWeight() throws RemoteException;

    /**
     * Change the value of the size threshold of the snapshot; which is
     * employed by the lookup service in the test to determine whether
     * or not to take a "snapshot" of the system state.
     *
     * @param threshold size threshold for taking a snapshot
     */
    void setLogToSnapshotThreshold(int threshold) throws RemoteException;

    /**
     * Retrieve the value of the size threshold of the snapshot; which is
     * employed by the lookup service in the test to determine whether
     * or not to take a "snapshot" of the system state.
     */
    int getLogToSnapshotThreshold() throws RemoteException;
}
