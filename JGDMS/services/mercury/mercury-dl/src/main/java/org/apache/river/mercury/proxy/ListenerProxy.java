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

import org.apache.river.proxy.ConstrainableProxyUtil;
import org.apache.river.proxy.ThrowThis;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;

import javax.security.auth.Subject;

import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import net.jini.security.TrustVerifier;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.Valid;

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
@AtomicSerial
public abstract class ListenerProxy implements RemoteEventListener,
	ReferentUuid, ProxyAccessor {

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

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("server", MailboxBackEnd.class),
            new SerialForm("registrationID", Uuid.class)
        };
    }

    public static void serialize(PutArg arg, ListenerProxy o) throws IOException {
        arg.put("server", o.server);
        arg.put("registrationID", o.registrationID);
        arg.writeArgs();
    }

    /**
     * Creates a mailbox listener proxy, returning an instance
     * that implements RemoteMethodControl if the server does too.
     *
     * @param id the ID of the proxy
     * @param server the server's listener proxy
     */
    static ListenerProxy create(Uuid id, MailboxBackEnd server) {
	if (server == null || id == null)
            throw new IllegalArgumentException("Cannot accept null arguments");
        // Always constrainable; fail closed when the server was not exported
        // with a constrainable endpoint.  The constrainable proxy is the only
        // concrete wire form (this class is abstract).
        if (!(server instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                "service must be exported with a constrainable endpoint: "
                + "server does not implement RemoteMethodControl");
        }
        return new ConstrainableListenerProxy(server, id, null);
    }

    ListenerProxy(GetArg arg) throws IOException, ClassNotFoundException {
	this(Valid.notNull(
		arg.get("server", null, MailboxBackEnd.class),
		"server cannot be null"
	    ), 
	    Valid.notNull(
		    arg.get("registrationID", null, Uuid.class), 
		    "registrationID cannot be null"
	    )
	);
    }

    /** Simple constructor */
    ListenerProxy(MailboxBackEnd ref, Uuid regID) {
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

    public Object getProxy() {
	return server;
    }

    /** A subclass of ListenerProxy that implements RemoteMethodControl. */
    @AtomicSerial
    final static class ConstrainableListenerProxy extends ListenerProxy
        implements RemoteMethodControl
    {
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
        private final MethodConstraints methodConstraints;

        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("methodConstraints", MethodConstraints.class)
            };
        }

        public static void serialize(PutArg arg, ConstrainableListenerProxy o) throws IOException {
            arg.put("methodConstraints", o.methodConstraints);
            arg.writeArgs();
        }

        /** Creates an instance of this class. */
        private ConstrainableListenerProxy(MailboxBackEnd server, Uuid id,
            MethodConstraints methodConstraints)
        {
            super(constrainServer(server, methodConstraints), id);
            this.methodConstraints = methodConstraints;
        }

	ConstrainableListenerProxy(GetArg arg) throws IOException, ClassNotFoundException {
	    this(arg, check(arg));
	}

	ConstrainableListenerProxy(GetArg arg, MethodConstraints constraints) 
		throws IOException, ClassNotFoundException{
	    super(arg);
	    methodConstraints = constraints;
	}
	private static MethodConstraints check(GetArg arg)
		throws IOException, ClassNotFoundException {
	    // Read the superclass field directly rather than constructing a plain
	    // ListenerProxy (now abstract); super(arg) still runs its validation.
	    MailboxBackEnd server = arg.get("server", null, MailboxBackEnd.class);
	    MethodConstraints methodConstraints =
		    arg.get("methodConstraints", null, MethodConstraints.class);
	    MethodConstraints proxyCon = null;
	    if (server instanceof RemoteMethodControl &&
		(proxyCon = ((RemoteMethodControl)server).getConstraints()) != null) {
		// Constraints set during proxy deserialization.
		return ConstrainableProxyUtil.reverseTranslateConstraints(
			proxyCon, methodMap1);
	    }
	    /* Verify the server and its constraints */
            ConstrainableProxyUtil.verifyConsistentConstraints(methodConstraints,
                                                        server,
                                                        methodMap1);
	    return methodConstraints;
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
    }
}


