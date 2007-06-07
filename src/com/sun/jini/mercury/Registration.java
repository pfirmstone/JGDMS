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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collection;

import javax.security.auth.Subject;

import com.sun.jini.landlord.ConstrainableLandlordLease;
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

import net.jini.event.MailboxPullRegistration;
import net.jini.event.MailboxRegistration;
import net.jini.event.RemoteEventIterator;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;

/**
 * The <tt>Registration</tt> class is the client-side proxy
 * returned to event mailbox clients as the result of the 
 * registration process. It implements the <tt>MailboxRegistration</tt>
 * interface and delegates functionality to the mailbox service
 * where necessary.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */ 
class Registration implements MailboxPullRegistration, 
    Serializable, ReferentUuid 
{

    private static final long serialVersionUID = 2L;

    /** Unique identifier for this registration */
    final Uuid registrationID;

    /** Reference to service implementation */
    final MailboxBackEnd mailbox;

    /** Reference to service provided RemoteEventListener implementation */
    final ListenerProxy listener;

    /** The service's registration lease */ 
    final Lease lease;
 
    /**
     * Creates a mailbox registration proxy, returning an instance 
     * that implements RemoteMethodControl if the server does too.
     *
     * @param server the server proxy
     * @param id the ID of the lease set
     * @param lease the lease set's lease
     */
    static Registration create(Uuid id, MailboxBackEnd server, Lease lease) {
	if (server instanceof RemoteMethodControl) {
	    return new ConstrainableRegistration(id, server, lease, null);
	} else {
	    return new Registration(id, server, lease);
	}
    }

    /** Convenience constructor */
    private Registration(Uuid id, MailboxBackEnd srv, Lease l) {
        if (id == null || srv == null || l == null)
            throw new IllegalArgumentException("Cannot accept null arguments");
        registrationID = id;
        mailbox = srv;
        listener = ListenerProxy.create(id, srv);
        lease = l;
    }

    // inherit javadoc from supertype
    public Lease getLease() {
	return lease;
    }
    
    // inherit javadoc from supertype
    public RemoteEventListener getListener() {
	return listener;
    }

    // inherit javadoc from supertype
    public void enableDelivery(RemoteEventListener target) 
	throws RemoteException
    {
        // Prevent resubmission of this registration's listener
	if ((target instanceof ListenerProxy) &&
	    (listener.equals((ListenerProxy)target))) {
	    throw new IllegalArgumentException("Cannot resubmit " +
		"a target that was provided by the EventMailbox service");
	} else { // OK to make the call, now
	    try {
	        mailbox.enableDelivery(registrationID, target);
	    } catch (ThrowThis tt) { 
	        tt.throwRemoteException();
	    }
	}
    }

    // inherit javadoc from supertype
    public void disableDelivery() throws RemoteException {
	try { 
            mailbox.disableDelivery(registrationID);
	} catch (ThrowThis tt) { 
	    tt.throwRemoteException();
	}
    }
    
    // inherit javadoc from supertype
    public RemoteEventIterator getRemoteEvents() 
	throws RemoteException 
    {
        RemoteEventIteratorImpl i = null;
	try { 
            RemoteEventIteratorData d = mailbox.getRemoteEvents(registrationID);
            i = new RemoteEventIteratorImpl(
                d.uuid, registrationID, mailbox, d.events);
	} catch (ThrowThis tt) { 
	    tt.throwRemoteException();
	}
	return i;
    }

    // inherit javadoc from supertype
    public void addUnknownEvents(Collection unknownEvents)
	throws RemoteException
    {
        //TODO - verify collection contains RemoteEvents
	try { 
            mailbox.addUnknownEvents(registrationID, unknownEvents);
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
    public Uuid getReferentUuid() {
        return registrationID;
    }

    /** Proxies with the same registrationID have the same hash code. */
    public int hashCode() {
        return registrationID.hashCode();
    }

    /** Proxies with the same registrationID are considered equal. */
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
            throw new InvalidObjectException("Registration.readObject "
                                             +"failure - mailbox "
                                             +"field is null");
        }//endif
        /* Verify registrationID */
        if(registrationID == null) {
            throw new InvalidObjectException
                                  ("Registration.readObject "
                                   +"failure - registrationID field is null");
        }//endif
        /* Verify regLease */
        if(lease == null) {
            throw new InvalidObjectException
                                        ("Registration.readObject "
                                         +"failure - lease field is null");
        }//endif
        /* Verify listener */
        if(listener == null) {
            throw new InvalidObjectException
                                        ("Registration.readObject "
                                         +"failure - listener field is null");
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
                                         +"deserialize Registration instance");
    }//end readObjectNoData

    /** A subclass of Registration that implements RemoteMethodControl. */
    final static class ConstrainableRegistration extends Registration 
        implements RemoteMethodControl 
    {
	private static final long serialVersionUID = 1L;

	// Mappings from client to server methods, 
	private static final Method[] methodMap1 = {
	    ProxyUtil.getMethod(MailboxPullRegistration.class,
	        "getRemoteEvents", new Class[] {}),
            ProxyUtil.getMethod(MailboxBackEnd.class, 
	        "getRemoteEvents", new Class[] {Uuid.class}), 
            // Use the same constraints for getNextBatch as getRemoteEvents
	    ProxyUtil.getMethod(MailboxPullRegistration.class,
	        "getRemoteEvents", new Class[] {}),
            ProxyUtil.getMethod(MailboxBackEnd.class, 
	        "getNextBatch", new Class[] {
		    Uuid.class, Uuid.class, long.class, Object.class}), 
	    ProxyUtil.getMethod(MailboxPullRegistration.class,
	        "addUnknownEvents", new Class[] {Collection.class}),
            ProxyUtil.getMethod(MailboxBackEnd.class, 
	        "addUnknownEvents", new Class[] {Uuid.class, Collection.class}), 
	    ProxyUtil.getMethod(MailboxRegistration.class,
	        "enableDelivery", new Class[] {RemoteEventListener.class}),
	    ProxyUtil.getMethod(MailboxBackEnd.class, 
	        "enableDelivery", new Class[] {Uuid.class, 
		RemoteEventListener.class}), 
	    ProxyUtil.getMethod(MailboxRegistration.class,
	        "disableDelivery", new Class[] {}),
	    ProxyUtil.getMethod(MailboxBackEnd.class, 
	        "disableDelivery", new Class[] {Uuid.class})
	};
	/**
	 * The client constraints placed on this proxy or <code>null</code>.
	 *
	 * @serial
	 */
	private MethodConstraints methodConstraints;

	/** Creates an instance of this class. */
	private ConstrainableRegistration(Uuid id, MailboxBackEnd server,
            Lease lease, MethodConstraints methodConstraints)
	{
	    super(id, constrainServer(server, methodConstraints),
	          lease);
	    this.methodConstraints = methodConstraints;
	}
	
	// inherit javadoc from supertype
	public RemoteEventIterator getRemoteEvents(long maxEvents, long timeout) 
	    throws RemoteException 
	{ 
	    //
	    //TODO - return constrained remote iterator impl
	    //
 	    return super.getRemoteEvents();
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
	    return new ConstrainableRegistration(registrationID, mailbox, lease, 
	        constraints);
	}

	/** {@inheritDoc} */
	public MethodConstraints getConstraints() {
	    return methodConstraints;
	}

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */

	/* Note that the superclass's hashCode method is OK as is. */
        /**
         * Returns a proxy trust iterator that is used in
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(mailbox);
        }//end getProxyTrustIterator

	/**
	 * Verifies that the registrationID, lease and mailbox fields are 
	 * not null, that mailbox implements RemoteMethodControl, and that the 
	 * mailbox proxy has the appropriate method constraints.
	 *
	 * @throws InvalidObjectException if registrationID, lease or mailbox
	 *         is null, if mailbox does not implement RemoteMethodControl, 
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
                                                        mailbox,
                                                        methodMap1);
            if( !(lease instanceof ConstrainableLandlordLease) ) {
                throw new InvalidObjectException
                                ("Registration.readObject failure - "
                                 +"lease is not an instance of "
                                 +"ConstrainableLandlordLease");
            }//endif

            if( !(listener instanceof ListenerProxy.ConstrainableListenerProxy) ) {
                throw new InvalidObjectException
                                ("Registration.readObject failure - "
                                 +"listener is not an instance of "
                                 +"ListenerProxy.ConstrainableListenerProxy");
            }//endif

            /* Verify listener's ID */
            if(registrationID !=
	       ((ListenerProxy.ConstrainableListenerProxy)listener).registrationID) 
            {
                throw new InvalidObjectException
                                        ("Registration.readObject "
                                         +"failure - listener ID "
                                         +"is not equal to "
                                         +"proxy ID");
            }            
	}
    }
}
