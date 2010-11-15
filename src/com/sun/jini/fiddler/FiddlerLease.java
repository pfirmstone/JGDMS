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

import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.lease.AbstractLease;

import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.UnknownLeaseException;

import java.lang.reflect.Method;
import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.RemoteException;

/**
 * When the Fiddler implementation of the lookup discovery service grants
 * a lease on a registration requested by a client, a proxy to that lease
 * is provided to allow the client to interact with the granted lease.
 * This class is the implementation of that proxy.
 * <p>
 * Clients only see instances of this class via the <code>Lease</code>
 * interface.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class FiddlerLease extends AbstractLease 
                   implements ReferentUuid
{

    private static final long serialVersionUID = 2L;

    /**
     * The reference to the back-end server of the lookup discovery service
     * that granted this lease (the granting entity).
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
     * The unique identifier assigned by the lookup discovery service to
     * the registration to which this lease corresponds.
     *
     * @serial
     */
    final Uuid registrationID;
    /**
     * The internal identifier assigned to this lease by the granting entity.
     *
     * @serial
     */
    final Uuid leaseID;

    /**
     * Public static factory method that creates and returns an instance of 
     * <code>FiddlerLease</code>. If the server associated with this proxy
     * implements <code>RemoteMethodControl</code>, then the object returned by
     * this method will also implement <code>RemoteMethodControl</code>.
     * 
     * @param server         reference to the server object through which 
     *                       communication occurs between the client-side and
     *                       server-side of the associated service.
     * @param serverID       the service ID of the service referenced by the
     *                       server parameter 
     * @param registrationID the unique identifier assigned by the service to
     *                       each instance of this proxy
     * @param leaseID        identifier assigned to this lease by the
     *                       granting entity
     * @param expiration     the time of expiration of the lease being granted
     * 
     * @return an instance of <code>FiddlerLease</code> that implements
     *         <code>RemoteMethodControl</code> if the given
     *         <code>server</code> does.
     */
    public static FiddlerLease createLease(Fiddler server,
                                           Uuid serverID,
                                           Uuid registrationID,
                                           Uuid leaseID,
                                           long expiration)
    {
        if(server instanceof RemoteMethodControl) {
            return new ConstrainableFiddlerLease(server,
                                                 serverID,
                                                 registrationID,
                                                 leaseID,
                                                 expiration,
                                                 null);//method constraints
        } else {
            return new FiddlerLease(server,
                                    serverID,
                                    registrationID,
                                    leaseID,
                                    expiration);
        }//endif
    }//end createLease

    /**
     * Constructs a proxy to the lease the Fiddler implementation of the
     * lookup discovery service places on a client's requested registration.
     * The lease corresponding to the constructed proxy will have as its
     * expiration time, the time input to the <code>expiration</code>
     * parameter.
     *
     * @param server         reference to the back-end server of the lookup
     *                       discovery service that granted this lease (the
     *                       granting entity)
     * @param serverID       the service ID of the service referenced by the
     *                       server parameter 
     * @param registrationID unique identifier assigned to the registration
     *                       to which this lease corresponds
     * @param leaseID        identifier assigned to this lease by the
     *                       granting entity
     * @param expiration     the time of expiration of the lease being granted
     */
    private FiddlerLease(Fiddler server,
                         Uuid serverID,
                         Uuid registrationID,
                         Uuid leaseID,
                         long expiration)
    {
        super(expiration);
        this.server         = server;
        this.serverID       = serverID;
        this.registrationID = registrationID;
        this.leaseID        = leaseID;
    }//end constructor

    /**
     * Creates a <code>LeaseMap</code> object that can contain leases whose
     * renewal or cancellation can be batched. Additionally, upon creating
     * the map the current lease is placed in the map as a key, and
     * the value of the <code>duration</code> parameter is placed in
     * the map as the lease's corresponding mapped value of type
     * <code>Long</code>.
     *
     * @param duration the amount of time (in milliseconds) during which
     *                 the lease being placed in the map will remain in
     *                 effect. This value will be converted to an instance
     *                 of <code>Long</code> prior to being placed in the
     *                 map with its associated lease.
     *                 the lease in the map.
     *                      
     * @return an instance of <code>LeaseMap</code> that contains as its
     *         first mapping, the ordered pair consisting of this lease
     *         and the value of the <code>duration</code> parameter.
     *
     * @see net.jini.core.lease.Lease#createLeaseMap
     */
    public LeaseMap createLeaseMap(long duration) {
        return FiddlerLeaseMap.createLeaseMap(this, duration);
    }

    /**
     * Examines the input parameter to determine if that parameter, along
     * with the current lease (the current instance of this class), can
     * be batched in a <code>LeaseMap</code>.
     * <p>
     * For this implementation of the service, two leases can be batched
     * (placed in the same service-specific instance of <code>LeaseMap</code>)
     * if those leases satisfy the following conditions:
     * <p><ul>
     *    <li> the leases are the same type; that is, the leases are both
     *         instances of the same, service-specific <code>Lease</code>
     *         implementation
     *    <li> the leases were granted by the same backend server
     *                      
     * @param lease reference to the <code>Lease</code> object that this
     *              method examines for batching compatibility with the
     *              the current current instance of this class
     *                      
     * @return <code>true</code> if the input parameter is compatible for
     *         batching with the current current instance of this class,
     *         <code>false</code> otherwise
     *
     * @see net.jini.core.lease.Lease#canBatch
     */
    public boolean canBatch(Lease lease) {
        return (    (lease instanceof FiddlerLease)
                 && (serverID.equals(((FiddlerLease)lease).serverID))  );
    }//end FiddlerLease.canBatch

    /**
     * Returns a reference to the back-end server of the lookup discovery
     * service that granted this lease.
     *                      
     * @return an instance of the Fiddler implementation of the lookup
     *         discovery service.
     */
    Fiddler getServer() {
        return server;
    }

    /**
     * Returns the unique ID associated with the server referenced in this
     * class.
     *                      
     * @return an instance of the <code>Uuid</code> that contains the
     *         universally unique ID associated with the server referenced
     *         in this class.
     */
    Uuid getServerID() {
        return serverID;
    }

    /**
     * Returns the unique identifier assigned by the lookup discovery 
     * service to the registration to which the current lease corresponds.
     *                      
     * @return a <code>net.jini.id.Uuid</code> value
     *         corresponding to the unique ID assigned to the registration
     *         associated with the current lease.
     */
    Uuid getRegistrationID() {
        return registrationID;
    }

    /**
     * Returns the identifier assigned to the current lease by the entity
     * that granted it.
     *                      
     * @return a <code>long</code> value corresponding to the ID assigned
     *         to the current lease.
     */
    Uuid getLeaseID() {
        return leaseID;
    }

    /**
     * Replaces the current value of the <code>expiration</code> field 
     * (defined in the <code>AbstractLease</code> super class) with the
     * value contained in the input parameter. The value contained in the
     * <code>expiration</code> field represents the absolute (non-relative)
     * time (in milliseconds) at which the current lease will expire.
     *
     * @param expiration the new value of the <code>expiration</code> field
     */
    void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    /**
     * This method allows the entity to which the current lease is granted
     * (the lease holder) to indicate that it is no longer interested
     * in the resources provided to the entity by the lookup discovery
     * service. When an entity invokes this method, the overall effect 
     * is the same as if the lease expired, except that expiration occurs
     * immediately instead of at the end of a pre-agreed duration.
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being cancelled is unknown to the lease grantor.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been cancelled successfully.
     *
     * @see net.jini.core.lease.Lease#cancel
     */
    public void cancel() throws UnknownLeaseException, RemoteException {
        server.cancelLease(registrationID, leaseID);
    }

    /** 
     * This method allows the entity to which the current lease is granted
     * (the lease holder) to indicate that it is still interested in the
     * resources of the lookup discovery service, and to request continued
     * access to those resources for an amount of time (in milliseconds)
     * relative to the current time. That is, the duration is not added
     * added to the current expiration, but is added to the current time.
     * <p>
     * Note that the duration of the renewed lease will be no greater than
     * the requested duration, and may be less than that duration.
     *
     * @param duration the requested duration for the lease being renewed
     *
     * @return <code>long</code> value representing the actual duration that
     *         was granted for the renewed lease. Note that the actual
     *         duration granted and returned by this method may be less than
     *         the duration requested.
     *
     * @throws net.jini.core.lease.UnknownLeaseException this exception occurs
     *         when the lease being renewed does not exist, or is unknown
     *         to the lease grantor; typically because the lease has expired.
     *         
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the lease may or may
     *         not have been renewed successfully.
     *
     * @see net.jini.core.lease.Lease#renew
     * @see com.sun.jini.lease.AbstractLease#renew
     * @see com.sun.jini.lease.AbstractLease#doRenew
     */
    protected long doRenew(long duration)
                                 throws UnknownLeaseException, RemoteException
    {
        return server.renewLease(registrationID, leaseID, duration);
    }

    /** 
     * Returns the universally unique identifier that has been assigned to the
     * resource this proxy represents.
     *
     * @return the instance of <code>Uuid</code> that is associated with the
     *         resource this proxy represents. This method will not return
     *         <code>null</code>.
     *
     * @see net.jini.id.ReferentUuid
     */
    public Uuid getReferentUuid() {
        return leaseID;
    }

    /** 
     * For any instance of this class, returns the hashcode value generated
     * by the hashCode method of the lease ID associated with the current
     * instance of this lease.
     *
     * @return <code>int</code> value representing the hashcode for an
     *         instance of this class.
     */
    public int hashCode() {
        return leaseID.hashCode();
    }

    /** 
     * For any instance of this class, indicates whether the object input
     * to this method is equal to the current instance of this class; where
     * equality of leases granted by a lookup discovery service is defined by
     * reference equality. That is, two leases are equal if they reference
     * (are proxies to) the same backend lease server.
     *
     * @param obj reference to the object that is to be compared to the
     *            object on which this method is invoked.
     *
     * @return <code>true</code> if the object input is referentially
     *         equal to the object on which this method is invoked;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        return ReferentUuids.compare(this,obj);
    }

    /** When an instance of this class is deserialized, this method is
     *  automatically invoked. This implementation of this method validates
     *  the state of the deserialized instance.
     *
     * @throws <code>InvalidObjectException</code> if the state of the
     *         deserialized instance of this class is found to be invalid.
     */
    private void readObject(ObjectInputStream s)  
                               throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        /* Verify server */
        if(server == null) {
            throw new InvalidObjectException("FiddlerLease.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif
        /* Verify serverID */
        if(serverID == null) {
            throw new InvalidObjectException("FiddlerLease.readObject "
                                             +"failure - serverID "
                                             +"field is null");
        }//endif
        /* Verify registrationID */
        if(registrationID == null) {
            throw new InvalidObjectException("FiddlerLease.readObject "
                                             +"failure - registrationID "
                                             +"field is null");
        }//endif
    }//end readObject

    /** During deserialization of an instance of this class, if it is found
     *  that the stream contains no data, this method is automatically
     *  invoked. Because it is expected that the stream should always 
     *  contain data, this implementation of this method simply declares
     *  that something must be wrong.
     *
     * @throws <code>InvalidObjectException</code> to indicate that there
     *         was no data in the stream during deserialization of an
     *         instance of this class; declaring that something is wrong.
     */
    private void readObjectNoData() throws InvalidObjectException {
        throw new InvalidObjectException("no data found when attempting to "
                                         +"deserialize FiddlerLease instance");
    }//end readObjectNoData

    /** The constrainable version of the class <code>FiddlerLease</code>. 
     *  <p>
     *  When a client obtains an instance of this proxy class, the client
     *  should not attempt to use the proxy until the client is assured
     *  that the proxy can be trusted. In addition to implementing the
     *  methods and mechanisms required by <code>RemoteMethodControl</code>, 
     *  this class - in conjunction with the service's
     *  <code>ProxyVerifier</code> class, helps provide a mechanism
     *  for verifying trust in the proxy on behalf of a client.
     *  <p>
     *  In order to verify that an instance of this class is trusted, 
     *  trust must be verified in all subsidiary objects (contained in that
     *  instance) through which the client ultimately makes calls (local or
     *  remote).  With respect to this class, the <code>server</code> field
     *  is a proxy object through which the client makes remote calls to the 
     *  service's backend. Therefore, trust in that object must be
     *  verified. Additionally, this class also contains a field of type
     *  <code>Uuid</code> (<code>registrationID</code> which should be
     *  tested for trust. Consider the following diagram:
     *  <p>
     *  <pre>
     *    FiddlerLease {
     *        Fiddler server
     *        Uuid registrationID
     *    }//end FiddlerLease
     *  </pre>
     *  <p>
     *  Thus, in order to verify that an instance of this class is trusted,
     *  trust must be verified in the following objects from the diagram
     *  above:
     *  <ul><li> server
     *      <li> registrationID
     *  </ul>
     *
     *  When a client obtains an instance of this proxy class, the
     *  deserialization process which delivers the proxy to the client
     *  invokes the <code>readObject</code> method of this class. Part of
     *  trust verification is performed in the <code>readObject</code> method,
     *  and part is performed when the client prepares the proxy. Thus, this
     *  class' participation in the trust verification process can be
     *  summarized as follows:
     *  <p>
     *  <ul>
     *    <li> server
     *      <ul>
     *        <li> readObject
     *          <ul>
     *            <li> verify server != null
     *            <li> verify server implements RemoteMethodControl
     *            <li> verify server's method constraints are the same
     *                 as those placed on the corresponding public Remote
     *                 methods of its outer proxy class
     *          </ul>
     *        <li> proxy preparation
     *          <ul>
     *            <li> Security.verifyObjectTrust() which calls
     *            <li> ProxyVerifier.isTrustedObject() which calls
     *            <li> canonicalServerObject.checkTrustEquivalence(server)
     *                 (whose implementation is supplied by the particular 
     *                 RMI implementation that was used to export the server)
     *          </ul>
     *      </ul>
     *    <li> registrationID
     *      <ul><li> readObject
     *          <ul><li> verify registrationID != null</ul>
     *      </ul>
     *  </ul>
     *
     * @since 2.0
     */
    static final class ConstrainableFiddlerLease extends FiddlerLease
                                                 implements RemoteMethodControl
    {
        static final long serialVersionUID = 2L;

        /* Convenience fields containing, respectively, the renew and cancel
         * methods defined in the Lease interface. These fields are used in
         * the method mapping array, and when retrieving method constraints
         * for comparison in canBatch().
         */
        private static final Method renewMethod =
                             ProxyUtil.getMethod(Lease.class,
                                                 "renew",
                                                 new Class[] {long.class} );
        private static final Method cancelMethod =
                             ProxyUtil.getMethod(Lease.class,
                                                 "cancel",
                                                 new Class[] {} );

        /** Array containing element pairs in which each pair of elements
         *  represents a correspondence 'mapping' between two methods having
         *  the following characteristics:
         *  - the first element in the pair is one of the public, remote
         *    method(s) that may be invoked by the client through the proxy
         *    class that this class extends
         *  - the second element in the pair is the method, implemented
         *    in the backend server class, that is ultimately executed in
         *    the server's backend when the client invokes the corresponding
         *    method in this proxy
         */
        private static final Method[] methodMapArray = 
        {
            renewMethod, ProxyUtil.getMethod(Fiddler.class,
                                             "renewLease", 
                                             new Class[] {Uuid.class,
                                                          Uuid.class,
                                                          long.class} ),

            cancelMethod, ProxyUtil.getMethod(Fiddler.class,
                                              "cancelLease", 
                                              new Class[] {Uuid.class,
                                                           Uuid.class} )
        };//end methodMapArray

        /** In order to determine if this lease can be batched with another
         *  given lease, the method <code>canBatch</code> must verify that
         *  the corresponding methods of each lease have equivalent
         *  constraints. The array defined here contains the set of methods
         *  whose constraints will be compared in <code>canBatch</code>.
         */
        private static final Method[] canBatchMethodMapArray =
                                            { renewMethod,  renewMethod,
                                              cancelMethod, cancelMethod
                                            };//end canBatchMethodMapArray

        /** Client constraints placed on this proxy (may be <code>null</code>).
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Constructs a new <code>ConstrainableFiddlerLease</code> instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableFiddlerLease(Fiddler server, 
                                          Uuid serverID,
                                          Uuid registrationID,
                                          Uuid leaseID,
                                          long expiration,
                                          MethodConstraints methodConstraints)
        {
            super( constrainServer(server, methodConstraints),
                   serverID, registrationID, leaseID, expiration );
	    this.methodConstraints = methodConstraints;
        }//end constructor

        /**
         * Examines the input parameter to determine if that parameter, along
         * with the current lease (the current instance of this class), can
         * be batched in a <code>LeaseMap</code>.
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
         * @param lease reference to the <code>Lease</code> object that this
         *              method examines for batching compatibility with the
         *              the current instance of this class
         *                      
         * @return <code>true</code> if the input parameter is compatible for
         *         batching with the current instance of this class,
         *         <code>false</code> otherwise
         *
         * @see net.jini.core.lease.Lease#canBatch
         */
        public boolean canBatch(Lease lease) {
            if( !(super.canBatch(lease)) )  return false;
            /* Non-constrainable batch criteria satisfied, now handle 
             * constrainable case.
             */
            if( !(lease instanceof ConstrainableFiddlerLease) ) return false;
            /* Compare constraints */
            return ConstrainableProxyUtil.equivalentConstraints
                        ( methodConstraints,
                          ((ConstrainableFiddlerLease)lease).methodConstraints,
                          canBatchMethodMapArray );
        }//end ConstrainableFiddlerLease.canBatch

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

        /** Returns a new copy of this proxy class 
         *  (<code>ConstrainableFiddlerLease</code>) with its client
         *  constraints set to the specified constraints. A <code>null</code>
         *  value is interpreted as mapping all methods to empty constraints.
         */
        public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
        {
            return (new ConstrainableFiddlerLease(server,
                                                  serverID,
                                                  registrationID,
                                                  leaseID,
                                                  expiration,
                                                  constraints));
        }//end setConstraints

        /** Returns the client constraints placed on the current instance
         *  of this proxy class (<code>ConstrainableFiddlerLease</code>).
         *  The value returned by this method can be <code>null</code>,
         *  which is interpreted as mapping all methods to empty constraints.
         */
        public MethodConstraints getConstraints() {
            return methodConstraints;
        }//end getConstraints

        /** Returns a proxy trust iterator that is used in 
         *  <code>ProxyTrustVerifier</code> to retrieve this object's
         *  trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
	    return new SingletonProxyTrustIterator(server);
        }//end getProxyTrustIterator

        /** Performs various functions related to the trust verification
         *  process for the current instance of this proxy class, as
         *  detailed in the description for this class.
         *
         * @throws <code>InvalidObjectException</code> if any of the
         *         requirements for trust verification (as detailed in the 
         *         class description) are not satisfied.
         */
        private void readObject(ObjectInputStream s)  
                                   throws IOException, ClassNotFoundException
        {
            /* Note that basic validation of the fields of this class was
             * already performed in the readObject() method of this class'
             * super class.
             */
            s.defaultReadObject();
            /* Verify the server and its constraints */
            ConstrainableProxyUtil.verifyConsistentConstraints
                                                       (methodConstraints,
                                                        server,
                                                        methodMapArray);
        }//end readObject

    }//end class ConstrainableFiddlerLease

}//end class FiddlerLease
