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

import com.sun.jini.proxy.ThrowThis;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.rmi.RemoteException;

import javax.security.auth.Subject;

import net.jini.admin.Administrable;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;

/**
 * A MailboxProxy is a proxy for the event mailbox service.  
 * This is the object passed to clients of this service.
 * It implements the <code>PullEventMailbox</code> and the 
 * <code>Administrable</code> interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
class MailboxProxy implements PullEventMailbox,
    Administrable, Serializable, ReferentUuid 
{

    private static final long serialVersionUID = 2L;

    /**
     * The reference to the event mailbox service implementation
     *
     * @serial
     */
    final MailboxBackEnd mailbox;

    /**
     * The proxy's <code>Uuid</code>
     *
     * @serial
     */
    final Uuid proxyID;

    /**
     * Creates a mailbox proxy, returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param mailbox the server proxy
     * @param id the ID of the server
     */
    static MailboxProxy create(MailboxBackEnd mailbox, Uuid id) {
        if (mailbox instanceof RemoteMethodControl) {
            return new ConstrainableMailboxProxy(mailbox, id, null);
        } else {
            return new MailboxProxy(mailbox, id);
        }
    }

    /** Convenience constructor. */
    private MailboxProxy(MailboxBackEnd mailbox, Uuid proxyID) {
        if (mailbox == null || proxyID == null) {
            throw new IllegalArgumentException("Cannot accept null arguments");
        }
	this.mailbox = mailbox;
	this.proxyID = proxyID;
    }

    // inherit javadoc from parent
    public MailboxRegistration register(long duration) 
        throws RemoteException, LeaseDeniedException {
        // Check for a bad argument
        // Note that -1 (i.e. Lease.ANY) is a valid request
        if (duration < 1 && duration != Lease.ANY)
            throw new IllegalArgumentException(
                "Duration values must be positive");
        return mailbox.register(duration);
    }

    // inherit javadoc from parent
    public MailboxPullRegistration pullRegister(long duration) 
        throws RemoteException, LeaseDeniedException {
        // Check for a bad argument
        // Note that -1 (i.e. Lease.ANY) is a valid request
        if (duration < 1 && duration != Lease.ANY)
            throw new IllegalArgumentException(
                "Duration values must be positive");
        return mailbox.pullRegister(duration);
    }
    
    // inherit javadoc from parent
    public Object getAdmin() throws RemoteException {
        return mailbox.getAdmin();
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

    /** Proxies for servers with the same proxyID have the same hash code. */
    public int hashCode() {
	return proxyID.hashCode();
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
        if(mailbox == null) {
            throw new InvalidObjectException("MailboxProxy.readObject "
                                             +"failure - mailbox "
                                             +"field is null");
        }//endif
        /* Verify proxyID */
        if(proxyID == null) {
            throw new InvalidObjectException("MailboxProxy.proxyID "
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
    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException("no data found when attempting to "
                                         +"deserialize MailboxProxy instance");
    }//end readObjectNoData

    
    /** A subclass of MailboxProxy that implements RemoteMethodControl. */
    final static class ConstrainableMailboxProxy extends MailboxProxy
        implements RemoteMethodControl
    {
        private static final long serialVersionUID = 1L;

        /** Creates an instance of this class. */
        private ConstrainableMailboxProxy(MailboxBackEnd mailbox, Uuid uuid,
            MethodConstraints methodConstraints)
        {
            super(constrainServer(mailbox, methodConstraints),
                  uuid);
        }

       /**
         * Returns a copy of the server proxy with the specified client
         * constraints and methods mapping.
         */
        private static MailboxBackEnd constrainServer(
            MailboxBackEnd mailbox,
            MethodConstraints methodConstraints)
        {
            return (MailboxBackEnd)
                ((RemoteMethodControl)mailbox).setConstraints(methodConstraints);
        }

        /** {@inheritDoc} */
        public RemoteMethodControl setConstraints(
            MethodConstraints constraints)
        {
            return new ConstrainableMailboxProxy(mailbox, proxyID,
                constraints);
        }

        /** {@inheritDoc} */
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) mailbox).getConstraints();
        }

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */

        /**
         * Returns a proxy trust iterator that is used in
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(mailbox);
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
	    // Verify that the server implements RemoteMethodControl
            if( !(mailbox instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException(
		    "MailboxAdminProxy.readObject failure - mailbox " +
		    "does not implement constrainable functionality ");
            }//endif
        }//end readObject 


    }
}
