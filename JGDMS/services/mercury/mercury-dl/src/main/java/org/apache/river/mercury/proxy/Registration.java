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
package org.apache.river.mercury.proxy;

import org.apache.river.landlord.ConstrainableLandlordLease;
import org.apache.river.proxy.ConstrainableProxyUtil;
import org.apache.river.proxy.ThrowThis;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collection;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.MailboxRegistration;
import net.jini.event.RemoteEventIterator;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

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
@AtomicSerial
public abstract class Registration implements MailboxPullRegistration,
    ReferentUuid, ProxyAccessor
{

    /** Unique identifier for this registration */
    final Uuid registrationID;

    /** Reference to service implementation */
    final MailboxBackEnd mailbox;

    /** Reference to service provided RemoteEventListener implementation */
    final ListenerProxy listener;

    /** The service's registration lease */
    final Lease lease;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("registrationID", Uuid.class),
            new SerialForm("mailbox", MailboxBackEnd.class),
            new SerialForm("listener", ListenerProxy.class),
            new SerialForm("lease", Lease.class)
        };
    }

    public static void serialize(PutArg arg, Registration o) throws IOException {
        arg.put("registrationID", o.registrationID);
        arg.put("mailbox", o.mailbox);
        arg.put("listener", o.listener);
        arg.put("lease", o.lease);
        arg.writeArgs();
    }

    /**
     * Creates a mailbox registration proxy, returning an instance 
     * that implements RemoteMethodControl if the server does too.
     *
     * @param server the server proxy
     * @param id the ID of the lease set
     * @param lease the lease set's lease
     */
    public static Registration create(Uuid id, MailboxBackEnd server, Lease lease) {
	if (id == null || server == null || lease == null)
            throw new IllegalArgumentException("Cannot accept null arguments");
	// Always constrainable; fail closed when the server was not exported
	// with a constrainable endpoint.  The constrainable proxy is the only
	// concrete wire form (this class is abstract).
	if (!(server instanceof RemoteMethodControl)) {
	    throw new IllegalArgumentException(
		"service must be exported with a constrainable endpoint: "
		+ "server does not implement RemoteMethodControl");
	}
	return new ConstrainableRegistration(id, server, lease, null);
    }

    /** Convenience constructor */
    Registration(Uuid id, MailboxBackEnd srv, ListenerProxy proxy, Lease l) {
        registrationID = id;
        mailbox = srv;
        listener = proxy;
        lease = l;
    }

    Registration(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg),
		arg.get("mailbox", null, MailboxBackEnd.class),
		arg.get("listener", null, ListenerProxy.class),
		arg.get("lease", null, Lease.class)
		);
    }
    
    private static Uuid check(GetArg arg) throws IOException, ClassNotFoundException {
	Uuid registrationID = arg.get("registrationID", null, Uuid.class);
	MailboxBackEnd mailbox = arg.get("mailbox", null, MailboxBackEnd.class);
	ListenerProxy listener = arg.get("listener", null, ListenerProxy.class);
	Lease lease = arg.get("lease", null, Lease.class);
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
	return registrationID;
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

    public Object getProxy() {
	return mailbox;
    }

    /** A subclass of Registration that implements RemoteMethodControl. */
    @AtomicSerial
    final static class ConstrainableRegistration extends Registration
        implements RemoteMethodControl
    {
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
	private final MethodConstraints methodConstraints;

	public static SerialForm[] serialForm() {
	    return new SerialForm[] {
		new SerialForm("methodConstraints", MethodConstraints.class)
	    };
	}

	public static void serialize(PutArg arg, ConstrainableRegistration o) throws IOException {
	    arg.put("methodConstraints", o.methodConstraints);
	    arg.writeArgs();
	}

	/** Creates an instance of this class. */
	private ConstrainableRegistration(Uuid id, MailboxBackEnd server,
            Lease lease, MethodConstraints methodConstraints)
	{
	    super(id, 
		  constrainServer(server, methodConstraints),
		  ListenerProxy.create(id, server),
	          lease);
	    this.methodConstraints = methodConstraints;
	}
	
	ConstrainableRegistration(GetArg arg) throws IOException, ClassNotFoundException {
	    this(arg, check(arg));
	}
	
	ConstrainableRegistration(GetArg arg, MethodConstraints constraints) throws IOException, ClassNotFoundException {
	    super(arg);
	    methodConstraints = constraints;
	}
	
	private static MethodConstraints check(GetArg arg) throws IOException, ClassNotFoundException {
	    // Read the superclass fields directly rather than constructing a
	    // plain Registration (now abstract).  super(arg) still runs
	    // Registration(GetArg)'s own field validation.
	    MailboxBackEnd rMailbox = arg.get("mailbox", null, MailboxBackEnd.class);
	    Lease rLease = arg.get("lease", null, Lease.class);
	    ListenerProxy rListener = arg.get("listener", null, ListenerProxy.class);
	    Uuid rRegistrationID = arg.get("registrationID", null, Uuid.class);
	    MethodConstraints methodConstraints = (MethodConstraints)
		    arg.get("methodConstraints", null, MethodConstraints.class);
	    MethodConstraints proxyCon = null;
	    if (rMailbox instanceof RemoteMethodControl &&
		(proxyCon = ((RemoteMethodControl)rMailbox).getConstraints()) != null) {
		// Constraints set during proxy deserialization.
		methodConstraints = ConstrainableProxyUtil.reverseTranslateConstraints(
			proxyCon, methodMap1);
	    } else {
		/* Verify the server and its constraints */
		ConstrainableProxyUtil.verifyConsistentConstraints(
							methodConstraints,
                                                        rMailbox,
                                                        methodMap1);
	    }
            if( !(rLease instanceof ConstrainableLandlordLease) ) {
                throw new InvalidObjectException
                                ("Registration.readObject failure - "
                                 +"lease is not an instance of "
                                 +"ConstrainableLandlordLease");
            }//endif

            if( !(rListener instanceof ListenerProxy.ConstrainableListenerProxy) ) {
                throw new InvalidObjectException
                                ("Registration.readObject failure - "
                                 +"listener is not an instance of "
                                 +"ListenerProxy.ConstrainableListenerProxy");
            }//endif

            /* Verify listener's ID */
            if(!rRegistrationID.equals(
	       ((ListenerProxy.ConstrainableListenerProxy)rListener).registrationID))
            {
                throw new InvalidObjectException
                                        ("Registration.readObject "
                                         +"failure - listener ID "
                                         +"is not equal to "
                                         +"proxy ID");
            }
	    return methodConstraints;
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
    }
}
