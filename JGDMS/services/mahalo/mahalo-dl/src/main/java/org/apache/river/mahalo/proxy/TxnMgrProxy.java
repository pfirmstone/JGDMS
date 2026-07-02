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
package org.apache.river.mahalo.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.CrashCountException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;
import net.jini.core.transaction.server.TransactionParticipant;
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
 * A <code>TxnMgrProxy</code> is a proxy for the 
 * transaction manager service.
 * This is the object passed to clients of this service.
 * It implements the <code>TransactionManager</code> and the 
 * <code>Administrable</code> interfaces.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
@AtomicSerial
public abstract class TxnMgrProxy implements TransactionManager, Administrable,
    ReferentUuid, ProxyAccessor
{

    /**
     * The reference to the transaction manager service implementation
     */
    final TxnManager backend;

    /**
     * The proxy's <code>Uuid</code>
     */
    final Uuid proxyID;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("backend", TxnManager.class),
            new SerialForm("proxyID", Uuid.class)
        };
    }

    public static void serialize(PutArg arg, TxnMgrProxy o) throws IOException {
        arg.put("backend", o.backend);
        arg.put("proxyID", o.proxyID);
        arg.writeArgs();
    }

    /**
     * Creates a transaction manager proxy.
     *
     * <p>Always returns the constrainable {@link ConstrainableTxnMgrProxy}, and
     * fails closed if the server proxy does not implement
     * {@link RemoteMethodControl}: a non-constrainable server was not exported
     * with a constrainable endpoint, so producing a plain proxy would silently
     * drop the client's security constraints.  The constrainable proxy is
     * therefore the only concrete wire form (this class is {@code abstract}).
     *
     * @param txnMgr the server proxy
     * @param id the ID of the server
     * @throws IllegalArgumentException if {@code txnMgr} does not implement
     *         {@link RemoteMethodControl}
     */
    public static TxnMgrProxy create(TxnManager txnMgr, Uuid id) {
        if (!(txnMgr instanceof RemoteMethodControl)) {
            throw new IllegalArgumentException(
                "service must be exported with a constrainable endpoint: "
                + "server does not implement RemoteMethodControl");
        }
        return new ConstrainableTxnMgrProxy(check(txnMgr, id), id, null);
    }

    /** Convenience constructor. */
    TxnMgrProxy(TxnManager txnMgr, Uuid id) {
	this.backend = txnMgr;
	this.proxyID = id;
    }
    
    TxnMgrProxy (GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg), arg.get("proxyID", null, Uuid.class));
    }
    
    private static TxnManager check(TxnManager txnMgr, Uuid id){
        if (txnMgr == null || id == null) {
            throw new IllegalArgumentException("Cannot accept null arguments");
        }
	 return txnMgr;
    }
    
    private static TxnManager check(GetArg arg) throws IOException, ClassNotFoundException {
	try {
	    return check((TxnManager) arg.get("backend", null),
		    (Uuid) arg.get("proxyID", null));
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException("Invariants unsatisfied");
	    e.initCause(ex);
	    throw e;
	}
    }
    
    public Created create(long lease) 
	throws LeaseDeniedException, RemoteException 
    {
	return backend.create(lease);
    }

    @Override
    public Created create(long lease, TransactionManager.TransactionConfig config)
	throws LeaseDeniedException, RemoteException
    {
	return backend.create(lease, config);
    }

    public void join(long id, TransactionParticipant part, long crashCount)
	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
	backend.join(id, part, crashCount);
    }

    public int getState(long id) 
	throws UnknownTransactionException, RemoteException
    {
	return backend.getState(id);
    }

    public void commit(long id)
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException
    {
	backend.commit(id);
    }

    public void commit(long id, long waitFor)
        throws UnknownTransactionException, CannotCommitException,
               TimeoutExpiredException, RemoteException
    {
	backend.commit(id, waitFor);
    }

    public void abort(long id)
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException
    {
	backend.abort(id);
    }

    public void abort(long id, long waitFor)
	throws UnknownTransactionException, CannotAbortException,
               TimeoutExpiredException, RemoteException
    {
	backend.abort(id, waitFor);
    }

    // inherit javadoc from parent
    public Object getAdmin() throws RemoteException {
        return backend.getAdmin();
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
	return backend;
    }
   
    /** A subclass of TxnMgrProxy that implements RemoteMethodControl. */
    @AtomicSerial
    @Stateless
    final static class ConstrainableTxnMgrProxy extends TxnMgrProxy
        implements RemoteMethodControl
    {
        /** Creates an instance of this class. */
        private ConstrainableTxnMgrProxy(TxnManager txnMgr, Uuid id,
            MethodConstraints methodConstraints)
        {
            super(constrainServer(txnMgr, methodConstraints),
                  id);
        }

	ConstrainableTxnMgrProxy(GetArg arg) 
		throws IOException, ClassNotFoundException {
	    super(check(arg));
	}
	
	private static GetArg check(GetArg arg)
		throws IOException, ClassNotFoundException {
	    // Validate the superclass fields directly from the stream rather than
	    // constructing a plain TxnMgrProxy (which is now abstract).  The
	    // super(arg) chain still runs TxnMgrProxy(GetArg)'s own validation.
	    Object backend = arg.get("backend", null);
	    // Verify that the server implements RemoteMethodControl
            if( !(backend instanceof RemoteMethodControl) ) {
                throw new InvalidObjectException(
		    "ConstrainableTxnMgrProxy.readObject failure - backend " +
		    "does not implement constrainable functionality ");
            }//endif
	    return arg;
	}

       /**
         * Returns a copy of the server proxy with the specified client
         * constraints and methods mapping.
         */
        private static TxnManager constrainServer(
            TxnManager txnMgr,
            MethodConstraints methodConstraints)
        {
            return (TxnManager)
                ((RemoteMethodControl)txnMgr).setConstraints(methodConstraints);
        }

        /** {@inheritDoc} */
        public RemoteMethodControl setConstraints(
            MethodConstraints constraints)
        {
            return new ConstrainableTxnMgrProxy(backend, proxyID,
                constraints);
        }

        /** {@inheritDoc} */
        public MethodConstraints getConstraints() {
            return ((RemoteMethodControl) backend).getConstraints();
        }

        /* Note that the superclass's hashCode method is OK as is. */
        /* Note that the superclass's equals method is OK as is. */

        /**
         * Returns a proxy trust iterator that is used in
         * <code>ProxyTrustVerifier</code> to retrieve this object's
         * trust verifier.
         */
        private ProxyTrustIterator getProxyTrustIterator() {
            return new SingletonProxyTrustIterator(backend);
        }//end getProxyTrustIterator
    }
}
