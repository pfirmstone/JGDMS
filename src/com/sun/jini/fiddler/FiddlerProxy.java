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

import net.jini.admin.Administrable;
import net.jini.discovery.LookupDiscoveryService;
import net.jini.discovery.LookupDiscoveryRegistration;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.RemoteEventListener;

import java.lang.reflect.Method;
import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

/**
 * This class is a proxy for a lookup discovery service. Clients only see
 * instances of this class via the LookupDiscoveryService interface (and
 * the FiddlerAdmin interface if needed).
 *
 * @author Sun Microsystems, Inc.
 *
 */
class FiddlerProxy implements Administrable, LookupDiscoveryService,
                              ReferentUuid, Serializable
{
    private static final long serialVersionUID = 2L;
    /**
     * The reference through which communication occurs between the
     * client-side and the server-side of the lookup discovery service
     *
     * @serial
     */
    final Fiddler server;
    /** 
     * The unique identifier assigned to the current instance of this 
     * proxy class by the lookup discovery service. This ID is used to
     * determine equality between proxies.
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Public static factory method that creates and returns an instance of 
     * <code>FiddlerProxy</code>. If the server associated with this proxy
     * implements <code>RemoteMethodControl</code>, then the object returned by
     * this method will also implement <code>RemoteMethodControl</code>.
     * 
     * @param server  reference to the server object through which 
     *                communication occurs between the client-side and
     *                server-side of the associated service.
     * @param proxyID the unique identifier assigned by the service to each
     *                instance of this proxy
     * 
     * @return an instance of <code>FiddlerProxy</code> that implements
     *         <code>RemoteMethodControl</code> if the given <code>server</code>
     *         does.
     */
    public static FiddlerProxy createServiceProxy(Fiddler server,
                                                  Uuid proxyID)
    {
        if(server instanceof RemoteMethodControl) {
            return new ConstrainableFiddlerProxy(server, proxyID, null);
        } else {
            return new FiddlerProxy(server, proxyID);
        }//endif
    }//end createServiceProxy

    /**
     * Constructs a new instance of FiddlerProxy.
     *
     * @param server  reference to the server object through which 
     *                communication occurs between the client-side and
     *                server-side of the associated service
     * @param proxyID the unique identifier assigned by the service to each
     *                instance of this proxy
     */
    private FiddlerProxy(Fiddler server, Uuid proxyID) {
	this.server  = server;
	this.proxyID = proxyID;
    }//end constructor

    /* From net.jini.admin.Administrable */
    /** 
     * Returns a proxy object through which the lookup discovery service
     * for which the object on which this method is invoked serves as
     * proxy may be administered
     *
     * @return a proxy object through which the lookup discovery service
     *         may be administered.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server.
     *
     * @see net.jini.admin.Administrable
     */
    public Object getAdmin() throws RemoteException {
        return server.getAdmin();
    }

    /* From net.jini.discovery.LookupDiscoveryService */
    /**
     * Registers with the lookup discovery service. When a client invokes
     * this method, it requests that the lookup discovery service perform
     * discovery processing on its behalf.
     *
     * @param groups        String array, none of whose elements may be null,
     *                      consisting of zero or more names of groups to
     *                      which lookup services to discover belong.
     *                      A null value or an empty array
     *                      (net.jini.discovery.LookupDiscovery.ALL_GROUPS
     *                      or net.jini.discovery.LookupDiscovery.NO_GROUPS)
     *                      are both acceptable.
     *                      
     * @param locators      array of zero or more non-null LookupLocator
     *                      objects, each corresponding to a specific lookup
     *                      service to discover. If either the empty array
     *                      or null is passed to this argument, then no
     *                      locator discovery will be performed for the
     *                      associated registration.
     *                      
     * @param listener      a non-null instance of RemoteEventListener. This 
     *                      argument specifies the entity that will receive
     *                      events notifying the registration that a lookup
     *                      service of interest has been discovered. A 
     *                      non-null value must be passed to this argument,
     *                      otherwise a NullPointerException will be thrown
     *                      and the registration.
     *                      
     * @param handback      null or an instance of MarshalledObject. This
     *                      argument specifies an object that will be 
     *                      included in the notification event that the
     *                      lookup discovery service sends to the registered
     *                      listener.
     *                      
     * @param leaseDuration long value representing the amount of time (in
     *                      milliseconds) for which the resources of the
     *                      lookup discovery service are being requested.
     *                      
     * @return an instance of the LookupDiscoveryRegistration interface.
     * 
     * @throws java.rmi.RemoteException typically, this exception occurs when
     *         there is a communication failure between the client and the
     *         server. When this exception does occur, the registration may
     *         or may not have completed successfully.
     *
     * @throws java.lang.NullPointerException this exception occurs when
     *         null is input to the listener parameter.
     *
     * @see net.jini.discovery.LookupDiscoveryService
     */
    public LookupDiscoveryRegistration register(String[] groups,
                                                LookupLocator[] locators,
                                                RemoteEventListener listener,
                                                MarshalledObject handback,
                                                long leaseDuration)
                                                        throws RemoteException
    {
	return server.register(groups,
                               locators,
                               listener,
                               handback,
                               leaseDuration);
    }

    /* From net.jini.id.ReferentUuid */
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
        return proxyID;
    }

    /** 
     * For any instance of this class, returns the hashcode value generated
     * by the hashCode method of the proxy ID associated with the current
     * instance of this proxy.
     *
     * @return <code>int</code> value representing the hashcode for an
     *         instance of this class.
     */
    public int hashCode() {
	return proxyID.hashCode();
    }

    /** 
     * For any instance of this class, indicates whether the object input
     * to this method is equal to the current instance of this class; where
     * equality of proxies to a lookup discovery service is defined by
     * reference equality. That is, two proxies are equal if they reference
     * (are proxies to) the same backend server.
     *
     * @param obj reference to the object that is to be compared to the
     *            object on which this method is invoked.
     *
     * @return <code>true</code> if the object input is referentially
     *         equal to the object on which this method is invoked;
     *         <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
	return  ReferentUuids.compare(this,obj);
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
            throw new InvalidObjectException("FiddlerProxy.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("FiddlerProxy.readObject "
                                             +"failure - proxyID "
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
                                         +"deserialize FiddlerProxy instance");
    }//end readObjectNoData

    /** The constrainable version of the class <code>FiddlerProxy</code>. 
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
     *  <code>Uuid</code> (<code>proxyID</code> which should be
     *  tested for trust. Consider the following diagram:
     *  <p>
     *  <pre>
     *    FiddlerProxy {
     *        Fiddler server
     *        Uuid proxyID
     *    }//end FiddlerProxy
     *  </pre>
     *  <p>
     *  Thus, in order to verify that an instance of this class is trusted,
     *  trust must be verified in the following objects from the diagram
     *  above:
     *  <ul><li> server
     *      <li> proxyID
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
     *    <li> proxyID
     *      <ul><li> readObject
     *          <ul><li> verify proxyID != null</ul>
     *      </ul>
     *  </ul>
     *
     * @since 2.0
     */
    static final class ConstrainableFiddlerProxy extends FiddlerProxy
                                                 implements RemoteMethodControl
    {
        static final long serialVersionUID = 2L;

        /* Array containing element pairs in which each pair of elements
         * represents a correspondence 'mapping' between two methods having
         * the following characteristics:
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
            ProxyUtil.getMethod(Administrable.class,
                                "getAdmin", new Class[] {} ),
            ProxyUtil.getMethod(Administrable.class,
                                "getAdmin", new Class[] {} ),

            ProxyUtil.getMethod(LookupDiscoveryService.class,
                                "register", 
                                new Class[] {String[].class,
                                             LookupLocator[].class,
                                             RemoteEventListener.class,
                                             MarshalledObject.class,
                                             long.class} ),
            ProxyUtil.getMethod(Fiddler.class,
                                "register",
                                new Class[] {String[].class,
                                             LookupLocator[].class,
                                             RemoteEventListener.class,
                                             MarshalledObject.class,
                                             long.class} )
        };//end methodMapArray

        /** Client constraints placed on this proxy (may be <code>null</code>).
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Constructs a new <code>ConstrainableFiddlerProxy</code> instance.
         *  <p>
         *  For a description of all but the <code>methodConstraints</code>
         *  argument (provided below), refer to the description for the
         *  constructor of this class' super class.
         *
         *  @param methodConstraints the client method constraints to place on
         *                           this proxy (may be <code>null</code>).
         */
        private ConstrainableFiddlerProxy(Fiddler server, 
                                          Uuid proxyID,
                                          MethodConstraints methodConstraints)
        {
            super( constrainServer(server, methodConstraints), proxyID);
	    this.methodConstraints = methodConstraints;
        }//end constructor

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
         *  (<code>ConstrainableFiddlerProxy</code>) with its client
         *  constraints set to the specified constraints. A <code>null</code>
         *  value is interpreted as mapping all methods to empty constraints.
         */
        public RemoteMethodControl setConstraints
                                              (MethodConstraints constraints)
        {
            return ( new ConstrainableFiddlerProxy
                                             (server, proxyID, constraints) );
        }//end setConstraints

        /** Returns the client constraints placed on the current instance
         *  of this proxy class (<code>ConstrainableFiddlerProxy</code>).
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

    }//end class ConstrainableFiddlerProxy

}//end class FiddlerProxy
