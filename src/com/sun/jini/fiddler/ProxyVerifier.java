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

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.TrustEquivalence;

import java.io.Serializable;
import java.rmi.RemoteException;

/** This class defines a trust verifier for the proxies related to the 
 *  Fiddler implementation of the lookup discovery service.
 *
 * @see net.jini.security.TrustVerifier
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
final class ProxyVerifier implements Serializable, TrustVerifier {

    private static final long serialVersionUID = 2L;

    /** The canonical instance of the inner proxy to the service. This
     *  instance will be used by the <code>isTrusted</code> method 
     *  as the known trusted object used to determine whether or not a
     *  given proxy is equivalent in trust, content, and function.
     *
     *  @serial
     */
    private final RemoteMethodControl innerProxy;
    /** 
     * The unique identifier associated with the backend server referenced
     * by the <code>innerProxy</code>, used for comparison with the IDs
     * extracted from the smart proxies being verified.
     *
     * @serial
     */
    private final Uuid proxyID;

    /** Constructs an instance of <code>TrustVerifier</code> that can be
     *  used to determine whether or not a given proxy is equivalent in
     *  trust, content, and function to the service's <code>innerProxy</code>
     *  referenced in the class constructed here.
     *
     * @param innerProxy canonical instance of the inner proxy to the service.
     *                   The <code>isTrustedObject</code> method will
     *                   determine whether or not proxies input to that
     *                   method are equivalent in trust, content, and function
     *                   to this object.
     * @param proxyID    instance of <code>Uuid</code> containing the unique
     *                   identifier associated with the backend server
     *                   referenced by the <code>innerProxy</code> paramater.
     * 
     * @throws UnsupportedOperationException if <code>innerProxy</code> does
     *         not implement both {@link RemoteMethodControl} and {@link
     *         TrustEquivalence}
     */
    ProxyVerifier(Fiddler innerProxy, Uuid proxyID) {
        if( !(innerProxy instanceof RemoteMethodControl) ) {
            throw new UnsupportedOperationException
                         ("cannot construct verifier - canonical inner "
                          +"proxy is not an instance of RemoteMethodControl");
        } else if( !(innerProxy instanceof TrustEquivalence) ) {
            throw new UnsupportedOperationException
                             ("cannot construct verifier - canonical inner "
                              +"proxy is not an instance of TrustEquivalence");
        }//endif
        this.innerProxy = (RemoteMethodControl)innerProxy;
        this.proxyID = proxyID;
    }//end constructor

    /** Returns <code>true</code> if the specified proxy object (that is
     *  not yet known to be trusted) is equivalent in trust, content, and
     *  function to the canonical inner proxy object referenced in this
     *  class; otherwise returns <code>false</code>.
     *
     * @param obj proxy object that will be compared to this class' stored
     *            canonical proxy to determine whether or not the given
     *            proxy object is equivalent in trust, content, and function.
     *            
     * @return <code>true</code> if the specified object (that is not yet
     *                           known to be trusted) is equivalent in trust,
     *                           content, and function to the canonical inner
     *                           proxy object referenced in this class;
     *                           otherwise returns <code>false</code>.
     *
     * @throws NullPointerException if any argument is <code>null</code>
     */
    public boolean isTrustedObject(Object obj,
                                   TrustVerifier.Context ctx)
                                                       throws RemoteException
    {
        /* Validate the arguments */
        if (obj == null || ctx == null) {
            throw new NullPointerException("arguments must not be null");
	}//endif
        /* Prepare the input proxy object for trust verification. The types
         * of proxies, specific to the service, that this method will
         * handle are:
         *  - ConstrainableFiddlerProxy
         *  - ConstrainableFiddlerRegistration
         *  - ConstrainableFiddlerLease
         *  - ConstrainableFiddlerAdminProxy
         */
        RemoteMethodControl inputProxy;
        Uuid inputProxyID;
        if( obj instanceof FiddlerProxy.ConstrainableFiddlerProxy ) {
            inputProxy = (RemoteMethodControl)((FiddlerProxy)obj).server;
            inputProxyID = ((ReferentUuid)obj).getReferentUuid();
        } else if
          (obj instanceof FiddlerRegistration.ConstrainableFiddlerRegistration)
        {
            FiddlerRegistration reg = (FiddlerRegistration)obj;
            if( !this.isTrustedObject( (reg.eventReg).getSource(), ctx) ) {
                return false;
            }//endif
            if( !this.isTrustedObject( (reg.eventReg).getLease(), ctx) ) {
                return false;
            }//endif
            inputProxy = (RemoteMethodControl)reg.server;
            /* FiddlerRegistration doesn't carry a proxyID. Default to the
             * cannonical proxyID to avoid complicated handler logic below.
             */
            inputProxyID = proxyID;
        } else if( obj instanceof FiddlerLease.ConstrainableFiddlerLease ) {
            inputProxy = (RemoteMethodControl)((FiddlerLease)obj).server;
            inputProxyID = ((FiddlerLease)obj).getServerID();
        } else if
             (obj instanceof FiddlerAdminProxy.ConstrainableFiddlerAdminProxy)
        {
            inputProxy = (RemoteMethodControl)((FiddlerAdminProxy)obj).server;
            inputProxyID = ((ReferentUuid)obj).getReferentUuid();
        } else if( obj instanceof RemoteMethodControl ) {
            /* This block handles the case where the inner proxy itself 
             * (rather than an outer proxy that contains the inner proxy)
             * is being verified for trust. Unlike most of the outer proxies,
             * the inner proxy doesn't provide a means for obtaining the UUID
             * of the associated backend server. Thus, to avoid complicated
             * handler logic, for this case the cannonical proxyID is used
             * in the trust equivalence comparison that is performed below.
             */
            inputProxy = (RemoteMethodControl)obj;
            inputProxyID = proxyID;
        } else {
            return false;
        }//endif
        /* Get the client constraints currently set on the input proxy */
        final MethodConstraints mConstraints 
                                        = inputProxy.getConstraints();
        /* Create a copy of the canonical proxy with its method constraints
         * replaced with the method constraints of the input proxy.
         */
        final TrustEquivalence constrainedInnerProxy =
             (TrustEquivalence)innerProxy.setConstraints(mConstraints);
        /* With respect to trust, content, and function, test whether the
         * input proxy is equivalent to the canonical inner proxy that has
         * the same method constraints as that input proxy, and verify that
         * the canonical ID of the backend server is equivalent to the ID
         * extracted from the input proxy; return the result.
         */
        return (    constrainedInnerProxy.checkTrustEquivalence(inputProxy)
                 && proxyID.equals(inputProxyID) );
    }//end isTrustedObject

}//end class ProxyVerifier
