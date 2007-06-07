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
package com.sun.jini.mercury;

import com.sun.jini.proxy.ConstrainableProxyUtil;
import com.sun.jini.proxy.ThrowThis;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.TrustVerifier;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;

import javax.security.auth.Subject;

import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;

/**
 * The <code>ListenerProxy</code> class implements the 
 * <code>RemoteEventListener</code> interface.
 * Instances of this class are provided as the event "forwarding" 
 * target to clients of the mailbox service.  
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */

class ListenerProxy implements RemoteEventListener, Serializable, ReferentUuid {

    private static final long serialVersionUID = 2L;

    /**
     * The reference to the event mailbox service implementation
     *
     * @serial
     */
    final MailboxBackEnd server;

    /**
     * The proxy's <code>Uuid</code>
     *
     * @serial
     */
    final Uuid registrationID;
    
    /**
     * Creates a mailbox listener proxy, returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param id the ID of the proxy
     * @param server the server's listener proxy
     */
    static ListenerProxy create(Uuid id, MailboxBackEnd server) {
        if (server instanceof RemoteMethodControl) {
            return new ConstrainableListenerProxy(server, id, null);
        } else {
            return new ListenerProxy(server, id);
        }
    }

    /** Simple constructor */
    private ListenerProxy(MailboxBackEnd ref, Uuid regID) {
        if (ref == null || regID == null)
            throw new IllegalArgumentException("Cannot accept null arguments");
        server = ref;
        registrationID = regID;
    }

    // documentation inherited from supertype
    public void notify(RemoteEvent theEvent) 
	throws UnknownEventException, RemoteException 
    {
	try {
            server.notify(registrationID, theEvent);
	} catch (ThrowThis tt) {
	    tt.throwRemoteException();
	}

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
    // Final to ensure safety. Called by enableDeliveryDo() w/i lock 
    public final Uuid getReferentUuid() {
        return registrationID;
    }

    /** Proxies for servers with the same proxyID have the same hash code. */
    public int hashCode() {
        return registrationID.hashCode();
    }

    /**
     * Proxies for servers with the same <code>proxyID</code> are
     * considered equal.
     */
    public boolean equals(Object o) {
        return ReferentUuids.compare(this,o);
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
            throw new InvalidObjectException("ListenerProxy.readObject "
                                             +"failure - server "
                                             +"field is null");
        }//endif
        /* Verify registrationID */
        if(registrationID == null) {

            throw new InvalidObjectException("ListenerProxy.readObject "
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
    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException("no data found when attempting to "
                                         +"deserialize ListenerProxy instance");
    }//end readObjectNoData



    /** A subclass of ListenerProxy that implements RemoteMethodControl. */
    final static class ConstrainableListenerProxy extends ListenerProxy
        implements RemoteMethodControl
    {
        private static final long serialVersionUID = 2L;

        // Mappings from client to server methods,
        private static final Method[] methodMap1 = {
            ProxyUtil.getMethod(RemoteEventListener.class,
                "notify", new Class[] {RemoteEvent.class}),
            ProxyUtil.getMethod(MailboxBackEnd.class,
                "notify", new Class[] {Uuid.class, RemoteEvent.class}),
        };
	
        /**
         * The client constraints placed on this proxy or <code>null</code>.
         *
         * @serial
         */
        private MethodConstraints methodConstraints;

        /** Creates an instance of this class. */
        private ConstrainableListenerProxy(MailboxBackEnd server, Uuid id, 
            MethodConstraints methodConstraints)
        {
            super(constrainServer(server, methodConstraints), id);
            this.methodConstraints = methodConstraints;
        }

        /**
         * Returns a copy of the server proxy with the specified client
         * constraints and methods mapping.
         */
        private static MailboxBackEnd constrainServer(
            MailboxBackEnd server,
            MethodConstraints methodConstraints)
        {
            return (MailboxBackEnd)
                ((RemoteMethodControl)server).setConstraints(
                    ConstrainableProxyUtil.translateConstraints(
                        methodConstraints, methodMap1));
        }
        /** {@inheritDoc} */
        public RemoteMethodControl setConstraints(
            MethodConstraints constraints)
        {
            return new ConstrainableListenerProxy(server, registrationID,
                constraints);
        }

        /** {@inheritDoc} */
        public MethodConstraints getConstraints() {
            return methodConstraints;
        }

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */
	
        /**
         * Returns a proxy trust iterator that is used in
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(server);
        }//end getProxyTrustIterator
	
        /**
         * Verifies that the registrationID and server fields are
         * not null, that server implements RemoteMethodControl, and that the
         * server proxy has the appropriate method constraints.
         *
         * @throws InvalidObjectException if registrationID or mailbox
         *         is null, if server does not implement RemoteMethodControl,
         *         or if server has the wrong constraints
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
            ConstrainableProxyUtil.verifyConsistentConstraints(methodConstraints,
                                                        server,
                                                        methodMap1);
        }
    }
}


