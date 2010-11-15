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

import com.sun.jini.lease.AbstractLeaseMap;
import com.sun.jini.proxy.ConstrainableProxyUtil;

import net.jini.id.Uuid;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * When clients request a registration with the Fiddler implementation of
 * the lookup discovery service, leases of type FiddlerLease are granted
 * on those registrations. Under certain circumstances it may be desirable
 * to collect multiple granted leases in a set which implements the
 * <code>net.jini.core.lease.LeaseMap</code> interface. 
 * <p>
 * This class is the implementation class of the <code>LeaseMap</code>
 * interface that is employed by the Fiddler implementation of the lookup
 * discovery service. When a client wishes to "batch" leases granted by
 * that service, the are placed in an instance of this class.
 * <p>
 * Clients only see instances of this class via the  <code>LeaseMap</code>
 * interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class FiddlerLeaseMap extends AbstractLeaseMap {

    /**
     * The reference to the back-end server of the lookup discovery service
     *
     * @serial
     */
    final Fiddler server;
    /**
     * The unique ID associated with the server referenced in this class
     * (used to compare server references).
     *
     * @serial
     */
    final Uuid serverID;

    /**
     * Static factory method that creates and returns an instance of 
     * <code>FiddlerLeaseMap</code>. If the lease input to this method
     * is and instance of <code>ConstrainableFiddlerLease</code>, then
     * the object returned by this method will be an instance of
     * <code>ConstrainableFiddlerLeaseMap</code>.
     *
     * Note that because of the way the <code>Lease</code> class for the
     * associated service is implemented, together with the way the
     * <code>canBatch</code> method of that class is implemented, if the
     * <code>lease</code> object input to this method implements
     * <code>ConstrainableFiddlerLease</code>, then the <code>server</code>
     * object referenced in this class will implement 
     * <code>RemoteMethodControl</code> as well.
     * 
     * @param lease    reference to a lease to add to the map as a key value
     * @param duration the duration of the corresponding lease. This value
     *                 is the "mapped" value corresponding to the lease key.
     * 
     * @return an instance of <code>FiddlerLeaseMap</code>, or an instance
     *         of <code>ConstrainableFiddlerLeaseMap</code> if the given 
     *         <code>lease</code> is an instance of
     *         <code>ConstrainableFiddlerLease</code>.
     */
    static FiddlerLeaseMap createLeaseMap(FiddlerLease lease, long duration) {
        if(lease instanceof FiddlerLease.ConstrainableFiddlerLease) {
            MethodConstraints leaseConstraints =
        ((FiddlerLease.ConstrainableFiddlerLease)lease).getConstraints();

            return new ConstrainableFiddlerLeaseMap(lease.getServer(),
                                                    lease,
                                                    duration,
                                                    leaseConstraints);
        } else {
            return new FiddlerLeaseMap(lease.getServer(), lease, duration);
        }//endif
    }//end createLeaseMap

    /**
     * Constructs a new instance of FiddlerLeaseMap.
     *
     * @param server   reference to the server object through which 
     *                 communication occurs between the client-side and
     *                 server-side of the associated service.
     * @param lease    reference to a lease to add to the map as a key value
     * @param duration the duration of the corresponding lease. This value
     *                 is the "mapped" value corresponding to the lease key.
     */
    private FiddlerLeaseMap(Fiddler server, FiddlerLease lease, long duration){
        super(lease, duration);
        this.server = server;
        this.serverID = lease.getServerID();
    }//end constructor

    /**
     * Examines the input parameter to determine if that parameter will be
     * accepted or rejected by this map as a "legal" key value.
     * <p>
     * This method will return true if the <code>key</code> parameter is 
     * the type of lease which can be "batch-wise" renewed and cancelled 
     * along with all of the other leases in the map.
     * <p>
     * For the Fiddler implementation of the lookup discovery service, two
     * leases can be batched (placed in the same </code>FiddlerLeaseMap</code>)
     * if they are both instances of <code>FiddlerLease</code> and they
     * were both granted by the same Fiddler implementation of the lookup
     * discovery service. That is, they are the same type, and they were
     * granted by the same server.
     *                      
     * @param key reference to the object that this method examines to
     *        determine if this map will accept or reject it as a key
     *                      
     * @return true if this map will accept the <code>key</code> parameter,
     *         false otherwise
     *
     * @see net.jini.core.lease.Lease#canBatch
     */
    public boolean canContainKey(Object key) {
        return (    (key instanceof FiddlerLease)
                 && (serverID.equals(((FiddlerLease)key).getServerID()))  );
    }

    /**
     * Renews all leases in this map. For each lease (key) in the map, the
     * duration used to renew the lease is the lease's corresponding map
     * value. If all renewal attempts are successful, this method returns
     * normally; otherwise, this method removes from this map all leases
     * that could not be renewed, and throws a <code>LeaseMapException</code>.
     *
     * @throws net.jini.core.lease.LeaseMapException this exception is thrown
     *         when one or more leases in the map could not be renewed.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the leases in the map
     *         may or may not have been renewed successfully.
     */
    public void renewAll() throws LeaseMapException, RemoteException {
        int size = map.size();
        if (size == 0) {
            return;
        }
        Uuid[] registrationIDs = new Uuid[size];
        Uuid[] leaseIDs = new Uuid[size];
        long[] durations = new long[size];
        int i = 0;
        for (Iterator iter = map.entrySet().iterator(); iter.hasNext(); i++) {
            Map.Entry e = (Map.Entry)iter.next();
            FiddlerLease ls = (FiddlerLease)e.getKey();
            registrationIDs[i] = ls.getRegistrationID();
            leaseIDs[i] = ls.getLeaseID();
            durations[i] = ((Long)e.getValue()).longValue();
        }
        FiddlerRenewResults results = server.renewLeases(registrationIDs,
                                                         leaseIDs,
                                                         durations);
        long now = System.currentTimeMillis();
        HashMap emap = (results.exceptions != null) ?
                         new HashMap(2 * results.exceptions.length + 1) : null;
        i = 0;
        int j = 0;
        for (Iterator iter = map.entrySet().iterator(); iter.hasNext(); i++) {
            Map.Entry e = (Map.Entry)iter.next();
            long duration = results.durations[i];
            if (duration >= 0) {
                ((FiddlerLease)e.getKey()).setExpiration(duration + now);
            } else {
                emap.put(e.getKey(), results.exceptions[j++]);
                iter.remove();
            }
        }
        if (emap != null) {
            throw new LeaseMapException("lease renewal failures", emap);
        }
    }

    /**
     * Cancels all leases in this map. If all cancellation attempts are
     * successful, this method returns normally; otherwise, this method
     * removes from this map all leases that could not be cancelled, and
     * throws a <code>LeaseMapException</code>.
     * <p>
     * Note that a lease is removed from this map only when an attempt to
     * cancel it fails; that is, every lease that is successfully cancelled
     * is left in the map.
     *
     * @throws net.jini.core.lease.LeaseMapException this exception is thrown
     *         when one or more leases in the map could not be cancelled.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the leases in the map
     *         may or may not have been cancelled successfully.
     */
    public void cancelAll() throws LeaseMapException, RemoteException {
        int size = map.size();
        if (size == 0) {
            return;
        }
        Uuid[] registrationIDs = new Uuid[size];
        Uuid[] leaseIDs = new Uuid[size];
        int i = 0;
        for (Iterator iter = map.keySet().iterator(); iter.hasNext(); i++) {
            FiddlerLease ls = (FiddlerLease)iter.next();
            registrationIDs[i] = ls.getRegistrationID();
            leaseIDs[i] = ls.getLeaseID();
        }
        Exception[] exceptions = server.cancelLeases(registrationIDs,leaseIDs);
        if (exceptions == null) {
            return;
        }
        i = 0;
        HashMap emap = new HashMap(13);
        for (Iterator iter = map.keySet().iterator(); iter.hasNext(); i++) {
            FiddlerLease ls = (FiddlerLease)iter.next();
            Exception ex = exceptions[i];
            if (ex != null) {
                emap.put(ls, ex);
                iter.remove();
            }
        }
        throw new LeaseMapException("lease cancellation failures", emap);
    }

    /** The constrainable version of the class <code>FiddlerLeaseMap</code>. 
     *
     * @since 2.0
     */
    static final class ConstrainableFiddlerLeaseMap extends FiddlerLeaseMap {

        /* Convenience fields containing, respectively, the renew and cancel
         * methods defined in the Lease interface. These fields are used in
         * the method mapping array, and when retrieving method constraints
         * for comparison in canContainKey().
         */
        private static final Method renewMethod =
                             ProxyUtil.getMethod(Lease.class,
                                                 "renew",
                                                 new Class[] {long.class} );
        private static final Method cancelMethod =
                             ProxyUtil.getMethod(Lease.class,
                                                 "cancel",
                                                 new Class[] {} );

        /* When a <code>LeaseMap</code> is created, an implicit set of 
         * constraints is generated and associated with that lease map.
         * The constraints that are generated are based on the constraints
         * on the first lease placed in that lease map. As such, the array
         * defined here contains element pairs in which each pair of elements
         * represents a correspondence 'mapping' between two methods having
         * the following characteristics:
         *  - the first element in the pair is one of the public, remote
         *    method(s) that may be invoked by the client through a lease
         *    proxy, and which has method constraints that are intended 
         *    to apply to the corresponding method that is invoked on
         *    server's backend
         *  - the second element in the pair is the method, implemented
         *    in the backend server class, that is intended to be 
         *    executed with the same method constraints as its corresponding
         *    method, specified in the first element of the pair
         */
        private static final Method[] methodMapArray = 
        {
            renewMethod, ProxyUtil.getMethod(Fiddler.class,
                                             "renewLeases", 
                                             new Class[] {Uuid[].class,
                                                          Uuid[].class,
                                                          long[].class}),

            cancelMethod, ProxyUtil.getMethod(Fiddler.class,
                                              "cancelLeases", 
                                              new Class[] {Uuid[].class,
                                                           Uuid[].class})
        };//end methodMapArray

        /** In order to determine if a given lease will be accepted by this
         *  map as a "legal" key value, the method <code>canContainKey</code>
         *  must verify that the corresponding methods of the initial
         *  lease used to create this map and the lease input to
         *  <code>canContainKey</code> have equivalent constraints. The
         *  array defined here contains the set of methods whose constraints
         *  will be compared in <code>canContainKey</code>.
         */
        private static final Method[] canContainKeyMethodMapArray =
                                            { renewMethod,  renewMethod,
                                              cancelMethod, cancelMethod
                                            };//end canContainKeyMethodMapArray

        /** Client constraints placed on this proxy (may be <code>null</code>).
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Constructs a new <code>ConstrainableFiddlerLeaseMap</code>
         *  instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableFiddlerLeaseMap
                                        (Fiddler server, 
                                         FiddlerLease lease,
                                         long duration,
                                         MethodConstraints methodConstraints)
        {
            super(constrainServer(server, methodConstraints), lease, duration);
	    this.methodConstraints = methodConstraints;
        }//end constructor

        /**
         * Examines the input parameter to determine if that parameter will
         * be accepted or rejected by this map as a "legal" key value.
         * <p>
         * This method will return true if the <code>key</code> parameter is 
         * the type of lease which can be "batch-wise" renewed and cancelled 
         * along with all of the other leases in the map.
         * <p>
         * For this implementation of the service, two leases can be
         * batched (placed in the same service-specific instance of
         * <code>LeaseMap</code>) if those leases satisfy the following
         * conditions:
         * <p><ul>
         *    <li> the leases are the same type; that is, the leases are 
         *         both instances of the same, constrainable, service-specific
         *         <code>Lease</code> implementation
         *    <li> the leases were granted by the same backend server
         *    <li> the leases have the same constraints
         *                      
         * @param key reference to the object that this method examines to
         *        determine if this map will accept or reject it as a key
         *                      
         * @return <code>true</code> if this map will accept the
         *         <code>key</code> parameter, <code>false</code> otherwise
         *
         * @see net.jini.core.lease.Lease#canBatch
         * @see net.jini.core.lease.LeaseMap#canContainKey
         */
        public boolean canContainKey(Object key) {
            if( !(super.canContainKey(key)) )  return false;
            /* Non-constrainable criteria satisfied, now handle constrainable
             * case.
             */
            if( !(key instanceof FiddlerLease.ConstrainableFiddlerLease) ) {
                return false;
            }//endif
            /* Compare constraints */
            MethodConstraints keyConstraints =
          ((FiddlerLease.ConstrainableFiddlerLease)key).getConstraints();
            return ConstrainableProxyUtil.equivalentConstraints
                                              ( methodConstraints,
                                                keyConstraints,
                                                canContainKeyMethodMapArray );
        }//end ConstrainableFiddlerLeaseMap.canContainKey

        /** Returns a copy of the given server proxy having the client method
         *  constraints that result after the specified method mapping is
         *  applied to the given client method constraints.
         */
        private static Fiddler constrainServer( Fiddler server,
                                                MethodConstraints constraints )
        {
            MethodConstraints newConstraints 
               = ConstrainableProxyUtil.translateConstraints(constraints,
                                                             methodMapArray);
            RemoteMethodControl constrainedServer = 
                ((RemoteMethodControl)server).setConstraints(newConstraints);

            return ((Fiddler)constrainedServer);
        }//end constrainServer

    }//end class ConstrainableFiddlerLeaseMap

}//end class FiddlerLeaseMap
