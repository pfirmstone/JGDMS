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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.MailboxRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.AtomicSerial.Stateless;

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
@AtomicSerial
public abstract class MailboxProxy implements PullEventMailbox,
    Administrable, ReferentUuid, ProxyAccessor
{

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

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("mailbox", MailboxBackEnd.class),
            new SerialForm("proxyID", Uuid.class)
        };
    }

    public static void serialize(PutArg arg, MailboxProxy o) throws IOException {
        arg.put("mailbox", o.mailbox);
        arg.put("proxyID", o.proxyID);
        arg.writeArgs();
    }

    /**
     * Creates a mailbox proxy, returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param mailbox the server proxy
     * @param id the ID of the server
     */
    public static MailboxProxy create(MailboxBackEnd mailbox, Uuid id) {
        if (mailbox == null || id == null) {
            throw new IllegalArgumentException("Cannot accept null arguments");
        }
        // Always constrainable; fail closed when the server was not exported
        // with a constrainable endpoint, so a plain proxy that would silently
        // drop the client's security constraints can never be produced.  The
        // constrainable proxy is the only concrete wire form (this class is
        // abstract).
        if (!(mailbox instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                "service must be exported with a constrainable endpoint: "
                + "server does not implement RemoteMethodControl");
        }
        return new ConstrainableMailboxProxy(mailbox, id, null);
    }

    /** Convenience constructor. */
    MailboxProxy(MailboxBackEnd mailbox, Uuid proxyID) {
	this.mailbox = mailbox;
	this.proxyID = proxyID;
    }

    MailboxProxy(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg),(Uuid) arg.get("proxyID", null, Uuid.class));
    }
    
    private static MailboxBackEnd check(GetArg arg) throws IOException, ClassNotFoundException {
	MailboxBackEnd mailbox = arg.get("mailbox", null, MailboxBackEnd.class);
	Uuid proxyID = arg.get("proxyID", null, Uuid.class);
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
	return mailbox;
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

    @Override
    public Object getProxy() {
	return mailbox;
    }

    
    /** A subclass of MailboxProxy that implements RemoteMethodControl. */
    @AtomicSerial
    @Stateless
    final static class ConstrainableMailboxProxy extends MailboxProxy
        implements RemoteMethodControl
    {
        /** Creates an instance of this class. */
        private ConstrainableMailboxProxy(MailboxBackEnd mailbox, Uuid uuid,
            MethodConstraints methodConstraints)
        {
            super(constrainServer(mailbox, methodConstraints),
                  uuid);
        }

	ConstrainableMailboxProxy(GetArg arg) throws IOException, ClassNotFoundException {
	    super(check(arg));
	}
	
	private static GetArg check(GetArg arg) throws IOException, ClassNotFoundException{
	    // Read the superclass field directly rather than constructing a plain
	    // MailboxProxy (now abstract); super(arg) still runs MailboxProxy's
	    // own field validation.
	    Object mailbox = arg.get("mailbox", null, MailboxBackEnd.class);
	    // Verify that the server implements RemoteMethodControl
            if( !(mailbox instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException(
		    "MailboxAdminProxy.readObject failure - mailbox " +
		    "does not implement constrainable functionality ");
            }//endif
	    return arg;
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

    }
}
